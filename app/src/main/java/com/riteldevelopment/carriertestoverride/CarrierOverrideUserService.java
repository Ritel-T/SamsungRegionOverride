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
        if (overrideSimIdentity) {
            attempted++;
            try {
                result.append(TelephonyBridge.applySimOverride(
                        subId, mccMnc, imsi, carrierName));
                succeeded++;
            } catch (Throwable throwable) {
                appendSection(result, failure(SIM_LAYER_APPLY_FAILED, throwable));
            }
        }
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
        if (succeeded > 0) {
            appendSection(result, TargetApps.forceStop(refreshPackages));
        }
        appendSection(result, "Layers: " + succeeded + "/" + attempted + " succeeded");
        return result.toString();
    }

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
     * Brings IMS back after a restore, so voice and SMS work again without a reboot.
     *
     * <p>Applying the app country layer makes the IMS stack re-register using the overridden identity,
     * which the real network rejects: data keeps working, calls and SMS stop. Putting the identity back
     * does not re-trigger registration, so the subscription stays deregistered indefinitely — measured
     * at over two minutes on SM-S938B, and reported by users as lasting until they toggle the SIM in
     * Settings or reboot. Cycling the UICC applications is that Settings toggle, done programmatically.</p>
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
