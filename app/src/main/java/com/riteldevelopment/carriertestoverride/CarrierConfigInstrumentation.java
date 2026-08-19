package com.riteldevelopment.carriertestoverride;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Short-lived instrumentation started by the shell-UID Shizuku UserService.
 *
 * <p>Samsung rejects CarrierConfig overrides made directly by the shell UID on recent firmware.
 * Instrumentation lets the app process temporarily adopt the shell phone-state permissions while
 * preserving the app's package identity expected by Samsung's service implementation.</p>
 */
public final class CarrierConfigInstrumentation extends Instrumentation {
    static final String ARG_ACTION = "action";
    static final String ARG_SUB_ID = "sub_id";
    static final String ARG_COUNTRY_ISO = "country_iso";
    static final String ARG_CARRIER_NAME = "carrier_name";
    static final String ARG_OVERRIDE_NAME = "override_name";

    static final String ACTION_PROBE = "probe";
    static final String ACTION_APPLY_TRANSIENT = "apply_transient";
    static final String ACTION_CLEAR_TRANSIENT = "clear_transient";
    static final String ACTION_CLEAR_ALL = "clear_all";

    private static final String KEY_COUNTRY_ISO = "sim_country_iso_override_string";
    private static final String KEY_OVERRIDE_NAME = "carrier_name_override_bool";
    private static final String KEY_CARRIER_NAME = "carrier_name_string";

    private Bundle arguments;

    @Override
    public void onCreate(Bundle arguments) {
        this.arguments = arguments == null ? Bundle.EMPTY : new Bundle(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        int resultCode = Activity.RESULT_CANCELED;
        UiAutomation automation = null;
        try {
            // FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES matters here. The no-argument overload connects
            // with flags 0, which registers this as a UI-test automation service and suppresses every
            // other bound accessibility service for the life of the connection — so a TalkBack user
            // would go silent twice per operation (the capability probe runs one instrumentation and the
            // real work runs another). Nothing here needs any accessibility capability; only
            // adoptShellPermissionIdentity is wanted, and that is unaffected by the flag.
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            if (automation == null) {
                throw new IllegalStateException("UiAutomation connection is unavailable");
            }
            automation.adoptShellPermissionIdentity(
                    Manifest.permission.MODIFY_PHONE_STATE,
                    Manifest.permission.READ_PHONE_STATE);

            String action = arguments.getString(ARG_ACTION, ACTION_PROBE);
            if (ACTION_PROBE.equals(action)) {
                result.putString("message", inspectRuntime());
            } else {
                int subId = arguments.getInt(ARG_SUB_ID, -1);
                if (subId < 0) {
                    throw new IllegalArgumentException("Invalid subId: " + subId);
                }
                CarrierConfigManager manager = getTargetContext()
                        .getSystemService(CarrierConfigManager.class);
                if (manager == null) {
                    throw new IllegalStateException("CarrierConfigManager is unavailable");
                }

                if (ACTION_APPLY_TRANSIENT.equals(action)) {
                    PersistableBundle values = buildOverrideBundle(arguments);
                    invokeOverride(manager, subId, values, false);
                    result.putString("message", "App country: wrote a transient CarrierConfig override"
                            + "\nsubId=" + subId + ", ISO="
                            + values.getString(KEY_COUNTRY_ISO)
                            + (values.getBoolean(KEY_OVERRIDE_NAME)
                            ? ", displayName=" + values.getString(KEY_CARRIER_NAME) : ""));
                } else if (ACTION_CLEAR_TRANSIENT.equals(action)) {
                    String restoredIso = restoreCountryCacheIfAvailable(
                            manager, subId, arguments);
                    invokeOverride(manager, subId, null, false);
                    result.putString("message", "App country: cleared the transient CarrierConfig override"
                            + (restoredIso == null ? ""
                            : "\nSamsung's SIM country cache was warmed back to " + restoredIso + " first")
                            + "\nPersistent overrides written by other tools were left alone");
                } else if (ACTION_CLEAR_ALL.equals(action)) {
                    invokeOverride(manager, subId, null, true);
                    result.putString("message", "Cleared this subscription's transient and persistent"
                            + " CarrierConfig test overrides, including values written by other tools");
                } else {
                    throw new IllegalArgumentException("Unknown action: " + action);
                }
            }
            resultCode = Activity.RESULT_OK;
        } catch (Throwable throwable) {
            result.putString("error", throwable.getClass().getName()
                    + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()));
        } finally {
            if (automation != null) {
                try {
                    automation.dropShellPermissionIdentity();
                } catch (Throwable ignored) {
                    // Instrumentation is ending and AMS will tear down the automation connection.
                }
            }
            finish(resultCode, result);
        }
    }

    private static PersistableBundle buildOverrideBundle(Bundle arguments) {
        String iso = arguments.getString(ARG_COUNTRY_ISO, "")
                .trim().toLowerCase(Locale.ROOT);
        if (!iso.matches("[a-z]{2}")) {
            throw new IllegalArgumentException("Country ISO must be two letters");
        }
        PersistableBundle values = new PersistableBundle();
        values.putString(KEY_COUNTRY_ISO, iso);
        if (arguments.getBoolean(ARG_OVERRIDE_NAME, true)) {
            String carrierName = arguments.getString(ARG_CARRIER_NAME, "").trim();
            if (carrierName.isEmpty()) {
                throw new IllegalArgumentException(
                        "Carrier name must not be empty when overriding the display name");
            }
            values.putBoolean(KEY_OVERRIDE_NAME, true);
            values.putString(KEY_CARRIER_NAME, carrierName);
        } else {
            // Written explicitly rather than left out. CarrierConfigLoader *merges* a non-null override
            // bundle into whatever is already in place — only a null bundle resets it — so omitting the
            // key does not switch a previous apply's name override off. It silently keeps it, while this
            // run's success message, built from the local bundle, reports no display name at all.
            values.putBoolean(KEY_OVERRIDE_NAME, false);
        }
        return values;
    }

    private String restoreCountryCacheIfAvailable(CarrierConfigManager manager, int subId,
            Bundle arguments) throws Exception {
        String iso = arguments.getString(ARG_COUNTRY_ISO, "")
                .trim().toLowerCase(Locale.ROOT);
        if (!iso.matches("[a-z]{2}")) {
            return null;
        }
        PersistableBundle restore = new PersistableBundle();
        restore.putString(KEY_COUNTRY_ISO, iso);
        invokeOverride(manager, subId, restore, false);

        TelephonyManager telephony = getTargetContext()
                .getSystemService(TelephonyManager.class);
        if (telephony != null) {
            TelephonyManager forSub = telephony.createForSubscriptionId(subId);
            for (int attempt = 0; attempt < 20; attempt++) {
                if (iso.equalsIgnoreCase(forSub.getSimCountryIso())) {
                    break;
                }
                Thread.sleep(150L);
            }
        } else {
            Thread.sleep(600L);
        }
        return iso;
    }

    private static String inspectRuntime() throws Exception {
        Method method = findOverrideMethod();
        return "instrumentationUid=" + android.os.Process.myUid()
                + "\ncarrierConfigMethod=" + signature(method);
    }

    private static void invokeOverride(CarrierConfigManager manager, int subId,
            PersistableBundle values, boolean persistent) throws Exception {
        Method method = findOverrideMethod();
        try {
            if (method.getParameterCount() == 3) {
                method.invoke(manager, subId, values, persistent);
            } else {
                if (persistent) {
                    throw new UnsupportedOperationException(
                            "This Android version has no persistent override API");
                }
                method.invoke(manager, subId, values);
            }
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private static Method findOverrideMethod() throws NoSuchMethodException {
        try {
            return CarrierConfigManager.class.getDeclaredMethod(
                    "overrideConfig", int.class, PersistableBundle.class, boolean.class);
        } catch (NoSuchMethodException ignored) {
            return CarrierConfigManager.class.getDeclaredMethod(
                    "overrideConfig", int.class, PersistableBundle.class);
        }
    }

    private static String signature(Method method) {
        StringBuilder result = new StringBuilder(method.getName()).append('(');
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                result.append(',');
            }
            result.append(types[i].getSimpleName());
        }
        return result.append(')').toString();
    }
}
