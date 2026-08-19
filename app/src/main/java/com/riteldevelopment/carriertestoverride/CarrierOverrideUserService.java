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
    public String applyRegionOverride(int subId, String mccMnc, String imsi,
            String carrierName, String countryIso, boolean overrideSimIdentity,
            boolean overrideAppCountry, boolean overrideCarrierName, String[] refreshPackages) {
        StringBuilder result = new StringBuilder();
        int attempted = 0;
        int succeeded = 0;
        // App country goes first. That ordering is deliberate but it is NOT a fix for the IMS
        // deregistration, and nothing here should be read as though the cost had been removed.
        //
        // What is established on SM-S938B / Android 16, from a healthy baseline each time:
        //   SIM identity alone ......................... voice OK (60s)
        //   App country alone .......................... voice OK (50s)
        //   SIM identity -> App country ................ voice DEAD  (reproduced twice)
        //   App country -> SIM identity, back to back .. voice DEAD  (reproduced twice)
        //   App country -> SIM identity, seconds apart . voice OK, both layers live
        //   UICC cycle while App country is still live . does NOT recover; stays down 48s+
        //   Restore (drops App country) then cycle ..... recovers every time
        //
        // So the deregistration tracks the App country layer being live, and the gap between the two
        // writes changes the odds without settling them: the same build, run twice, went both ways.
        // The ordering is kept because writing the layer that survives a reload first is the better
        // guess, and CarrierConfigInstrumentation waits for the reload broadcast so the second write is
        // not issued into an in-flight reload. Neither was able to make an apply reliably keep voice.
        //
        // The consequence for the user is reported rather than hidden: the apply checks IMS afterwards
        // and says so, because the alternative is finding out on a call that will not connect. Recovery
        // requires Restore -- the UICC cycle cannot help while the override is still in place.
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
            appendSection(result, reportVoiceState(subId, overrideAppCountry));
            appendSection(result, TargetApps.forceStop(refreshPackages));
        }
        appendSection(result, "Layers: " + succeeded + "/" + attempted + " succeeded");
        return result.toString();
    }

    /** Recovered by the UI to headline the result, so the finding is not buried in the detail. */
    public static final String VOICE_STOPPED = "Calls and SMS: STOPPED on this SIM";

    /**
     * Watches whether this SIM can still place calls, for a few seconds after an apply.
     *
     * <p>The App country layer deregisters IMS often enough to matter and not reliably enough to
     * predict, and the failure is silent: data keeps working, the signal bars stay up, and the phone
     * looks fine until a call does not connect. Reporting it turns that into something the result panel
     * can state outright.</p>
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
     * <p>Only consulted when the country layer was part of the operation. A network-only apply has
     * never been observed to touch IMS, and reporting on it would invite the reader to attribute an
     * unrelated network problem to this tool.</p>
     */
    private static String reportVoiceState(int subId, boolean overrodeAppCountry) {
        if (!overrodeAppCountry) {
            return "Calls and SMS: untouched by this operation.";
        }
        try {
            for (int sample = 0; sample < VOICE_WATCH_SAMPLES; sample++) {
                if (!TelephonyBridge.isImsRegistered(subId)) {
                    return VOICE_STOPPED + ". IMS deregistered under the App country override."
                            + "\nRestore brings them back. Cycling the SIM will not, while the"
                            + " override is still in place.";
                }
                Thread.sleep(VOICE_WATCH_INTERVAL_MILLIS);
            }
            return "Calls and SMS: still working on this SIM, "
                    + VOICE_WATCH_SAMPLES + "s after the override.";
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not read the IMS registration state after apply", throwable);
            return "Calls and SMS: could not be checked ("
                    + throwable.getClass().getSimpleName() + ")."
                    + "\nIf calls stop working, Restore brings them back.";
        }
    }

    /** Long enough to cover the delay measured between the override landing and IMS dropping. */
    private static final int VOICE_WATCH_SAMPLES = 8;
    private static final long VOICE_WATCH_INTERVAL_MILLIS = 1000L;

    @Override
    public String restoreTransient(int subId, String originalMccMnc, String originalSpn,
            String originalCountryIso, String originalDisplayName, int originalDisplayNameSource,
            boolean restoreSimIdentity, boolean clearAppCountry, String[] refreshPackages) {
        StringBuilder result = new StringBuilder();
        int attempted = 0;
        int succeeded = 0;
        if (restoreSimIdentity) {
            attempted++;
            try {
                result.append(TelephonyBridge.restoreOriginal(
                        subId, originalMccMnc, originalSpn));
                succeeded++;
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
            appendSection(result, recoverIms(subId));
            appendSection(result, recoverDisplayName(
                    subId, originalDisplayName, originalDisplayNameSource));
            appendSection(result, TargetApps.forceStop(refreshPackages));
        }
        appendSection(result, "Layers: " + succeeded + "/" + attempted + " succeeded");
        return result.toString();
    }

    @Override
    public String clearAllCarrierConfigOverrides(int subId) {
        try {
            return CarrierConfigBridge.clearAll(subId)
                    + "\n" + TargetApps.forceStop(null);
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
     * outage to fix a problem that, since the apply order was corrected in
     * {@link #applyRegionOverride}, a normal apply/restore no longer causes. The registration state is
     * checked first and the cycle is skipped when IMS is already up.</p>
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
                        + "\nCalls and SMS were never interrupted.";
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not read the IMS registration state; cycling anyway", throwable);
        }
        try {
            return TelephonyBridge.cycleUiccApplications(subId);
        } catch (Throwable throwable) {
            Log.w(TAG, "IMS recovery failed", throwable);
            return "IMS recovery unavailable: " + throwable.getClass().getSimpleName()
                    + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage())
                    + "\nIf calls or SMS do not work, turn this SIM off and on in Settings, or reboot.";
        }
    }

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
