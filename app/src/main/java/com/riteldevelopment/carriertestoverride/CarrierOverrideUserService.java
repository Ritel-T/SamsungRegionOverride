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
        // Do not probe CarrierConfig here. Its probe is itself an instrumentation run, and the real
        // Country operation starts another one immediately afterwards. Android 17 Beta can receive
        // that second start before AMS has detached the first run, crashing system_server's
        // startInstrumentation path and leaving UiAutomation half-connected. The actual Country call
        // remains the authoritative capability check and reports its own failure in the operation.
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
            // Only observe IMS after the real identity is restored. Do not disable SIM applications:
            // that state is persistent and the service can die before a matching enable call.
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

    /** Do not offer reconnect guidance while a known fake Network identity remains live. */
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
     * Observes IMS after restore without changing the subscription's enabled state.
     *
     * <p>The previous disable/sleep/enable sequence could leave a SIM persistently disabled when the
     * service died between calls. A finally block cannot protect against process death. Passive
     * observation and manual Settings guidance require no compensating write from this process.</p>
     *
     * <p>Never fatal: a successful identity restore is distinct from confirmed IMS registration.</p>
     */
    private static String recoverIms(int subId) {
        return observeImsRecovery(new ImsRecoveryProbe() {
            @Override
            public boolean isRegistered() throws Exception {
                return TelephonyBridge.isImsRegistered(subId);
            }

            @Override
            public void pause(long millis) throws InterruptedException {
                Thread.sleep(millis);
            }
        });
    }

    /** Read-only capability boundary also lets tests exercise failures without Android Binder. */
    interface ImsRecoveryProbe {
        boolean isRegistered() throws Exception;
        void pause(long millis) throws InterruptedException;
    }

    static String observeImsRecovery(ImsRecoveryProbe probe) {
        try {
            for (int sample = 0; sample <= IMS_RECOVERY_SAMPLES; sample++) {
                if (probe.isRegistered()) {
                    return sample == 0 ? "IMS: registered at the time of restore."
                            : "IMS: registered again after restore.";
                }
                if (sample == IMS_RECOVERY_SAMPLES) {
                    break;
                }
                probe.pause(IMS_RECOVERY_INTERVAL_MILLIS);
            }
            return IMS_RECOVERY_UNCONFIRMED
                    + ": still not registered after the observation window." + IMS_MANUAL_RECOVERY;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return IMS_RECOVERY_UNCONFIRMED + ": observation was interrupted."
                    + IMS_MANUAL_RECOVERY;
        } catch (Throwable throwable) {
            return IMS_RECOVERY_UNCONFIRMED + ": state could not be read ("
                    + throwable.getClass().getSimpleName() + ")." + IMS_MANUAL_RECOVERY;
        }
    }

    private static final int IMS_RECOVERY_SAMPLES = 15;
    private static final long IMS_RECOVERY_INTERVAL_MILLIS = 1000L;
    private static final String IMS_MANUAL_RECOVERY =
            "\nIf this SIM is disabled, enable it in Settings; reboot alone does not re-enable it."
                    + " If it is already enabled but calls or SMS do not work, toggle it off and on"
                    + " in Settings or reboot.";

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
