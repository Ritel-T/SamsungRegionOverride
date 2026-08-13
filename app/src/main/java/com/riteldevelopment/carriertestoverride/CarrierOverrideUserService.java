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

    @Override
    public String applyRegionOverride(int subId, String mccMnc, String imsi,
            String carrierName, String countryIso, boolean overrideSimIdentity,
            boolean overrideAppCountry, boolean overrideCarrierName) {
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
            appendSection(result, TargetApps.forceStopDefaults());
        }
        appendSection(result, "Layers: " + succeeded + "/" + attempted + " succeeded");
        return result.toString();
    }

    @Override
    public String restoreTransient(int subId, String originalMccMnc, String originalSpn,
            String originalCountryIso, boolean restoreSimIdentity, boolean clearAppCountry) {
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
            appendSection(result, TargetApps.forceStopDefaults());
        }
        appendSection(result, "Layers: " + succeeded + "/" + attempted + " succeeded");
        return result.toString();
    }

    @Override
    public String clearAllCarrierConfigOverrides(int subId) {
        try {
            return CarrierConfigBridge.clearAll(subId)
                    + "\n" + TargetApps.forceStopDefaults();
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
