package com.riteldevelopment.carriertestoverride;

import android.os.PersistableBundle;

/** Read-only prerequisites for our existing Country instrumentation path, not a write probe. */
public final class CountryCapability {
    public enum Status { SUPPORTED, UNSUPPORTED_ANDROID, UNAVAILABLE, UNKNOWN, NOT_REQUESTED }

    private CountryCapability() {}

    public static String inspect(int sdk) {
        // Android 11 lacks both the delegation getter and am instrument --no-restart.
        if (sdk < 31) return "country_path=" + Status.UNSUPPORTED_ANDROID;
        try {
            return inspect(sdk, Class.forName("android.app.IActivityManager"),
                    Class.forName("android.telephony.CarrierConfigManager"));
        } catch (ReflectiveOperationException | SecurityException unavailable) {
            return "country_path=" + Status.UNAVAILABLE;
        }
    }

    static String inspect(int sdk, Class<?> activityManager, Class<?> config) {
        if (sdk < 31) return "country_path=" + Status.UNSUPPORTED_ANDROID;
        try {
            activityManager.getMethod("startDelegateShellPermissionIdentity", int.class, String[].class);
            if (sdk < 35) activityManager.getMethod("getDelegatedShellPermissions");
            try {
                config.getDeclaredMethod("overrideConfig", int.class, PersistableBundle.class, boolean.class);
            } catch (NoSuchMethodException missing) {
                config.getDeclaredMethod("overrideConfig", int.class, PersistableBundle.class);
            }
            return "country_path=" + Status.SUPPORTED;
        } catch (ReflectiveOperationException | SecurityException unavailable) {
            return "country_path=" + Status.UNAVAILABLE;
        }
    }

    public static Status status(String probe) {
        if (probe != null) {
            for (String line : probe.split("\\R")) {
                for (Status status : Status.values()) {
                    if (line.equals("country_path=" + status)) return status;
                }
            }
        }
        return Status.UNKNOWN;
    }

    /** Fixed, shareable reasons; do not export exception messages or device identifiers. */
    public static String failureReason(String probe) {
        switch (status(probe)) {
            case SUPPORTED: return null;
            case UNSUPPORTED_ANDROID:
                return "App country requires Android 12 (API 31) or newer for the current "
                        + "permission check and --no-restart path. Android 10/11 can install the app, "
                        + "but cannot use this Country path. SIM operator is a separate capability.";
            case UNAVAILABLE:
                return "App country is unavailable: this firmware does not expose the methods "
                        + "required by the current Country path. SIM operator is a separate capability.";
            default:
                return "App country capability could not be confirmed. No new disguise was started; "
                        + "existing recovery records are retained.";
        }
    }

    public static void requireSupported(String probe) {
        String reason = failureReason(probe);
        if (reason != null) throw new UnsupportedOperationException(reason);
    }
}
