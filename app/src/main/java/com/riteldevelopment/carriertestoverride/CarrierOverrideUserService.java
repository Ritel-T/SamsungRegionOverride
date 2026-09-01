package com.riteldevelopment.carriertestoverride;

import android.content.Context;
import android.util.Log;

public final class CarrierOverrideUserService extends ICarrierOverrideService.Stub {
    private static final String TAG = "CarrierOverrideService";

    /*
     * Per-layer failure markers.
     *
     * An operation reports one string for both layers, so the UI recovers "which half failed" by
     * matching these prefixes. That is still a string contract, but it is no longer a fragile one:
     * OverrideRepository references these very constants instead of repeating the literals, so the two
     * sides cannot drift. Making it a typed result would mean changing the AIDL surface, which would
     * cost a re-validation of the privileged path on real hardware for no behavioural gain.
     */
    public static final String SIM_LAYER_APPLY_FAILED = "SIM identity layer failed";
    public static final String SIM_LAYER_RESTORE_FAILED = "SIM identity layer restore failed";
    public static final String COUNTRY_LAYER_APPLY_FAILED = "App country layer failed";
    public static final String COUNTRY_LAYER_RESTORE_FAILED = "App country layer restore failed";

    public CarrierOverrideUserService() {
        Log.i(TAG, "created without Context");
    }

    public CarrierOverrideUserService(Context context) {
        Log.i(TAG, "created with Context for " + context.getPackageName());
    }

    @Override
    public void destroy() {
        Log.i(TAG, "destroy");
        System.exit(0);
    }

    @Override
    public String inspectRuntime() {
        StringBuilder result = new StringBuilder();
        try {
            result.append(TelephonyBridge.inspectRuntime());
        } catch (Throwable throwable) {
            result.append("SIM identity probe unavailable: ")
                    .append(throwable.getClass().getSimpleName())
                    .append(throwable.getMessage() == null ? "" : ": " + throwable.getMessage());
        }
        result.append("\n");
        try {
            result.append(CarrierConfigBridge.inspectRuntime());
        } catch (Throwable throwable) {
            result.append("CarrierConfig probe unavailable: ")
                    .append(throwable.getClass().getSimpleName())
                    .append(throwable.getMessage() == null ? "" : ": " + throwable.getMessage());
        }
        return result.toString();
    }

    /**
     * Reads the subscription display name and its source, for the caller to keep until restore.
     *
     * <p>Returns null rather than throwing when the platform will not give it up: a name that cannot be
     * read is a name that will not be restored, and that is a documented cosmetic shortfall — not a
     * reason to fail an apply that is otherwise fine.</p>
     */
    @Override
    public String[] readDisplayName(int subId) {
        try {
            return TelephonyBridge.readDisplayName(subId);
        } catch (Throwable throwable) {
            Log.w(TAG, "Reading the subscription display name failed", throwable);
            return null;
        }
    }

    @Override
    public String readSimFingerprint(int subId) {
        try {
            return TelephonyBridge.readSimFingerprint(subId);
        } catch (Throwable throwable) {
            Log.w(TAG, "Reading the SIM fingerprint failed", throwable);
            return null;
        }
    }

    @Override
    public String applyRegionOverride(int subId, String mccMnc, String imsi,
            String carrierName, String countryIso, boolean overrideSimIdentity,
            boolean overrideAppCountry, boolean overrideCarrierName) {
        StringBuilder result = new StringBuilder();
        int attempted = 0;
        int succeeded = 0;
        Boolean imsBefore = readImsState(subId);
        // App country goes first. This ordering lets CarrierConfig finish its asynchronous reload before
        // the SIM identity is changed, but it is not an IMS fix. The fake Network MCC/MNC is the latent
        // cause: Samsung later derives an IMS home domain from it, while a Country reload is one common
        // event that tears down the existing data profile and forces that bad registration attempt.
        //
        // A Network-only apply can therefore look healthy for minutes: it preserves the old Chinese IMS
        // session. A later signal loss, SIM cycle, airplane mode or CarrierConfig refresh can still make
        // it register against the fake carrier and fail. The post-apply watch is only a point-in-time
        // observation and is worded that way.
        if (overrideAppCountry) {
            attempted++;
            try {
                appendSection(result, CarrierConfigBridge.applyTransient(subId,
                        countryIso, carrierName, overrideCarrierName));
                succeeded++;
            } catch (Throwable throwable) {
                appendSection(result, failure(COUNTRY_LAYER_APPLY_FAILED, throwable));
            }
        }
        if (overrideSimIdentity) {
            attempted++;
            try {
                appendSection(result, TelephonyBridge.applySimOverride(
                        subId, mccMnc, imsi, carrierName));
                succeeded++;
            } catch (Throwable throwable) {
                appendSection(result, failure(SIM_LAYER_APPLY_FAILED, throwable));
            }
        }
        if (succeeded > 0) {
            appendSection(result, reportVoiceState(subId, imsBefore));
        }
        appendSection(result, "Layers: " + succeeded + "/" + attempted + " succeeded");
        return result.toString();
    }

    /** Stable machine marker recovered by the UI; do not translate or paraphrase. */
    public static final String VOICE_STOPPED = "IMS state: UNREGISTERED";
    /** Stable machine marker for a restore whose telephony values landed but whose IMS state did not. */
    public static final String IMS_RECOVERY_UNCONFIRMED = "IMS recovery: UNCONFIRMED";

    /**
     * Watches IMS registration for a few seconds after an apply.
     *
     * <p>A fake Network identity can poison the next IMS registration, while a Country/CarrierConfig
     * reload is one common trigger for that registration. The existing session can also survive, so a
     * phone may look healthy until a later reconnect. Reporting the sampled state makes the immediate
     * result visible without pretending it predicts the rest of the session.</p>
     *
     * <p>Sampled over a window rather than once, because once is wrong. The first version read the
     * registration state immediately after the write and cheerfully reported "still working" on a SIM
     * that was deregistered twenty seconds later — the drop lands a few seconds after the override, not
     * with it. Polling also lets a healthy apply finish as soon as the window is up, and a broken one
     * report the moment it breaks.</p>
     *
     * <p>The seconds this costs are spent on every successful apply, which is a real price for an
     * operation that is already several seconds long. It buys the difference between a user who knows
     * their phone cannot take calls and one who finds out from a missed one.</p>
     *
     * <p>The baseline separates a break introduced during this operation from a subscription that was
     * already unregistered. Either state still matters to the user, but they should not be assigned the
     * same cause.</p>
     */
    private static String reportVoiceState(int subId, Boolean imsBefore) {
        try {
            for (int sample = 0; sample < VOICE_WATCH_SAMPLES; sample++) {
                if (!TelephonyBridge.isImsRegistered(subId)) {
                    return VOICE_STOPPED + ". IMS is not registered while the disguise is live."
                            + (imsBefore == null
                            ? " Its state before this apply could not be read."
                            : (imsBefore
                            ? " It was registered before this apply."
                            : " It was already unregistered before this apply."))
                            + "\nEnd the disguise first. If service does not return, toggle this SIM"
                            + " off and on in Settings or reboot.";
                }
                Thread.sleep(VOICE_WATCH_INTERVAL_MILLIS);
            }
            return "IMS: currently registered " + VOICE_WATCH_SAMPLES
                    + "s after apply. This is not a guarantee: a later IMS/SIM reconnect can still"
                    + " fail while the fake Network identity is live. End the disguise when finished.";
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not read the IMS registration state after apply", throwable);
            return "IMS: could not be checked ("
                    + throwable.getClass().getSimpleName() + ")."
                    + "\nEnd the disguise when finished; if service does not return, toggle this SIM"
                    + " off and on in Settings or reboot.";
        }
    }

    private static Boolean readImsState(int subId) {
        try {
            return TelephonyBridge.isImsRegistered(subId);
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not read IMS state before apply", throwable);
            return null;
        }
    }

    /** Long enough to cover the delay measured between the override landing and IMS dropping. */
    private static final int VOICE_WATCH_SAMPLES = 8;
    private static final long VOICE_WATCH_INTERVAL_MILLIS = 1000L;

    @Override
    public String restoreTransient(int subId, String originalMccMnc, String originalSpn,
            String originalCountryIso, String originalDisplayName, int originalDisplayNameSource,
            boolean networkWasLive, boolean restoreSimIdentity, boolean clearAppCountry) {
        StringBuilder result = new StringBuilder();
        int attempted = 0;
        int succeeded = 0;
        boolean simRestoreSucceeded = false;
        if (restoreSimIdentity) {
            attempted++;
            try {
                result.append(TelephonyBridge.restoreOriginal(
                        subId, originalMccMnc, originalSpn));
                succeeded++;
                simRestoreSucceeded = true;
            } catch (Throwable throwable) {
                appendSection(result, failure(SIM_LAYER_RESTORE_FAILED, throwable));
            }
        }
        if (clearAppCountry) {
            attempted++;
            try {
                appendSection(result, CarrierConfigBridge.clearTransient(
                        subId, originalCountryIso));
                succeeded++;
            } catch (Throwable throwable) {
                appendSection(result, failure(COUNTRY_LAYER_RESTORE_FAILED, throwable));
            }
        }
        if (succeeded > 0) {
            // IMS first, then the name. Cycling the UICC applications makes the framework re-read the
            // subscription, so a name written before the cycle is a name the cycle can overwrite;
            // writing after it means this tool has the last word on the record it damaged.
            if (canRecoverIms(networkWasLive, restoreSimIdentity, simRestoreSucceeded)) {
                appendSection(result, recoverIms(subId));
            } else {
                appendSection(result, IMS_RECOVERY_UNCONFIRMED
                        + ": skipped because the fake Network identity may still be live."
                        + "\nRestore Network before cycling the SIM or rebooting.");
            }
            appendSection(result, recoverDisplayName(
                    subId, originalDisplayName, originalDisplayNameSource));
        }
        appendSection(result, "Layers: " + succeeded + "/" + attempted + " succeeded");
        return result.toString();
    }

    /** Package-visible for a regression test: never cycle while a known fake Network remains live. */
    static boolean canRecoverIms(boolean networkWasLive, boolean restoreWasRequested,
            boolean restoreSucceeded) {
        return !networkWasLive || (restoreWasRequested && restoreSucceeded);
    }

    @Override
    public String clearAllCarrierConfigOverrides(int subId) {
        try {
            return CarrierConfigBridge.clearAll(subId);
        } catch (Throwable throwable) {
            return failure("Clearing CarrierConfig overrides failed", throwable);
        }
    }

    @Override
    public String refreshTargetApps(String[] packages, int wipeMode, boolean relaunch) {
        try {
            return TargetApps.refresh(packages, wipeMode, relaunch);
        } catch (Throwable throwable) {
            return failure("Refreshing target apps failed", throwable);
        }
    }

    /**
     * Brings IMS back after a restore, but only when it actually needs bringing back.
     *
     * <p>Cycling the UICC applications is the programmatic form of toggling the SIM off and on in
     * Settings, and it is the only thing that recovers a deregistered subscription short of a reboot.
     * It is also, in itself, a few seconds with no service — so running it unconditionally spends a real
     * outage to fix nothing. The registration state is checked first and the cycle is skipped when IMS
     * is already up.</p>
     *
     * <p>An unreadable registration state cycles anyway: not knowing is not the same as knowing it is
     * healthy, and a needless cycle costs seconds where a missed one costs calls until the user
     * works out they have to reboot.</p>
     *
     * <p>Ordering matters and is not incidental: this runs <em>after</em> both layers have been put
     * back. Cycling while an override is still live re-registers IMS against the fake identity and
     * deregisters it again — verified on hardware.</p>
     *
     * <p>Never fatal. The restore itself has already succeeded by this point, so a device that does not
     * expose the call should report that and leave the user with the manual workaround, not turn a good
     * restore into a failure.</p>
     */
    private static String recoverIms(int subId) {
        try {
            if (TelephonyBridge.isImsRegistered(subId)) {
                return "IMS: still registered, so the UICC cycle was skipped."
                        + "\nIMS remained registered at the time of restore.";
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not read the IMS registration state; cycling anyway", throwable);
        }
        try {
            String cycle = TelephonyBridge.cycleUiccApplications(subId);
            Throwable unreadable = null;
            for (int sample = 0; sample < IMS_RECOVERY_SAMPLES; sample++) {
                try {
                    if (TelephonyBridge.isImsRegistered(subId)) {
                        return cycle + "\nIMS: registered again after restore.";
                    }
                } catch (Throwable throwable) {
                    unreadable = throwable;
                    break;
                }
                Thread.sleep(IMS_RECOVERY_INTERVAL_MILLIS);
            }
            return IMS_RECOVERY_UNCONFIRMED
                    + (unreadable == null ? ": still not registered after the recovery window."
                    : ": state could not be read ("
                    + unreadable.getClass().getSimpleName() + ").")
                    + "\nThe override was removed. Toggle this SIM off and on in Settings or reboot.";
        } catch (Throwable throwable) {
            Log.w(TAG, "IMS recovery failed", throwable);
            return IMS_RECOVERY_UNCONFIRMED + ": " + throwable.getClass().getSimpleName()
                    + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage())
                    + "\nIf calls or SMS do not work, turn this SIM off and on in Settings, or reboot.";
        }
    }

    private static final int IMS_RECOVERY_SAMPLES = 15;
    private static final long IMS_RECOVERY_INTERVAL_MILLIS = 1000L;

    /**
     * Puts the subscription's display name back, when one was captured before the first override.
     *
     * <p>Also never fatal, and for the same reason as {@link #recoverIms}: this is the cosmetic tail of
     * a restore whose telephony work has already landed. A failure here leaves a wrong SIM label, which
     * is worth reporting and not worth failing over.</p>
     */
    private static String recoverDisplayName(int subId, String name, int source) {
        try {
            return TelephonyBridge.restoreDisplayName(subId, name, source);
        } catch (Throwable throwable) {
            Log.w(TAG, "Display name restore failed", throwable);
            return "Display name restore failed: " + throwable.getClass().getSimpleName()
                    + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage())
                    + "\nThe SIM's label may still show the overridden operator; nothing else is affected.";
        }
    }

    private static void appendSection(StringBuilder builder, String value) {
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(value);
    }

    private static String failure(String prefix, Throwable throwable) {
        Log.e(TAG, prefix, throwable);
        String message = throwable.getMessage();
        return "ERROR: " + prefix + "\n" + throwable.getClass().getName()
                + (message == null ? "" : ": " + message);
    }
}
