package com.riteldevelopment.carriertestoverride;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Build;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    static final String RESULT_MESSAGE_BASE64 = "sro_message_b64";
    static final String RESULT_ERROR_BASE64 = "sro_error_b64";

    /** Bounds the reload wait so a device that never broadcasts still finishes the operation. */
    private static final long SETTLE_CEILING_MILLIS = 6000L;

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
            // Permission adoption does not require accessibility or view-hierarchy access.
            int automationFlags = automationFlagsForSdk(Build.VERSION.SDK_INT);
            automation = getUiAutomation(automationFlags);
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
                int subId = intArgument(arguments.get(ARG_SUB_ID), -1);
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
                    boolean settled = writeAndAwaitReload(manager, subId, values);
                    result.putString("message", "App country: wrote a transient CarrierConfig override"
                            + "\nsubId=" + subId + ", ISO="
                            + values.getString(KEY_COUNTRY_ISO)
                            + (values.getBoolean(KEY_OVERRIDE_NAME)
                            ? ", displayName=" + values.getString(KEY_CARRIER_NAME) : "")
                            + (settled ? ""
                            : "\nNote: no carrier config reload broadcast arrived within the timeout;"
                            + " the value was still written."));
                } else if (ACTION_CLEAR_TRANSIENT.equals(action)) {
                    String restoredIso = restoreCountryCacheIfAvailable(
                            manager, subId, arguments);
                    boolean settled = writeAndAwaitReload(manager, subId, null, false);
                    result.putString("message", "App country: cleared the transient CarrierConfig override"
                            + (restoredIso == null ? ""
                            : "\nSamsung's SIM country cache was warmed back to " + restoredIso + " first")
                            + (settled ? ""
                            : "\nNote: the final clear was submitted, but no reload broadcast arrived"
                            + " within the timeout")
                            + "\nPersistent overrides written by other tools were left alone");
                } else if (ACTION_CLEAR_ALL.equals(action)) {
                    boolean settled = writeAndAwaitReload(manager, subId, null, true);
                    result.putString("message", "Cleared this subscription's transient and persistent"
                            + " CarrierConfig test overrides, including values written by other tools"
                            + (settled ? ""
                            : "\nNote: the clear was submitted, but no reload broadcast arrived within"
                            + " the timeout"));
                } else {
                    throw new IllegalArgumentException("Unknown action: " + action);
                }
            }
            resultCode = Activity.RESULT_OK;
        } catch (Throwable throwable) {
            result.putString("error", throwable.getClass().getName()
                    + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()));
        } finally {
            encodeResultForCommandHost(result);
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

    @SuppressLint("InlinedApi")
    static int automationFlagsForSdk(int sdkInt) {
        // Android 17 / One UI 9 can return from FLAG_DONT_USE_ACCESSIBILITY while UiAutomation is
        // still CONNECTING. A fast instrumentation then reaches finish(), whose unconditional
        // disconnect throws "Cannot call disconnect() while connecting" after the CarrierConfig
        // write has already run. Keeping existing accessibility services active forces the normal
        // connection handshake to complete before getUiAutomation() returns, so finish is safe.
        if (sdkInt >= 37) {
            return UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES;
        }
        return sdkInt >= Build.VERSION_CODES.S
                ? UiAutomation.FLAG_DONT_USE_ACCESSIBILITY
                : UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES;
    }

    private static void encodeResultForCommandHost(Bundle result) {
        encodeResultValue(result, "message", RESULT_MESSAGE_BASE64);
        encodeResultValue(result, "error", RESULT_ERROR_BASE64);
    }

    private static void encodeResultValue(Bundle result, String sourceKey, String encodedKey) {
        String value = result.getString(sourceKey);
        if (value == null) {
            return;
        }
        result.putString(encodedKey, Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)));
    }

    private static PersistableBundle buildOverrideBundle(Bundle arguments) {
        String iso = arguments.getString(ARG_COUNTRY_ISO, "")
                .trim().toLowerCase(Locale.ROOT);
        if (!iso.matches("[a-z]{2}")) {
            throw new IllegalArgumentException("Country ISO must be two letters");
        }
        PersistableBundle values = new PersistableBundle();
        values.putString(KEY_COUNTRY_ISO, iso);
        if (booleanArgument(arguments.get(ARG_OVERRIDE_NAME), true)) {
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

    static int intArgument(Object value, int fallback) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static boolean booleanArgument(Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String normalized = ((String) value).trim();
            if ("true".equalsIgnoreCase(normalized)) {
                return true;
            }
            if ("false".equalsIgnoreCase(normalized)) {
                return false;
            }
        }
        return fallback;
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
        // A timed-out warm is deliberately left in place rather than followed by a clear. Explicitly
        // disable the name override so that safe fallback cannot keep re-applying the fake label.
        restore.putBoolean(KEY_OVERRIDE_NAME, false);
        // Same wait as the apply path: the point of warming the cache is that the real ISO is in
        // effect *before* the override is dropped, and a write that has only been submitted is not.
        if (!writeAndAwaitReload(manager, subId, restore)) {
            throw new IllegalStateException(
                    "Real country cache reload was not confirmed; the transient override was not cleared");
        }
        return iso;
    }

    /**
     * Writes the override and does not return until the carrier config reload it triggers has landed.
     *
     * <p>{@code overrideConfig} returns as soon as the bundle is handed over, while the reload it kicks
     * off runs asynchronously. Returning before that reload finishes would leave the caller writing its
     * next layer into an indeterminate state, so this waits for
     * {@code ACTION_CARRIER_CONFIG_CHANGED} — the framework's own signal that the reload completed.</p>
     *
     * <p>Note what this does <em>not</em> do: it does not stop the IMS deregistration. That was the
     * hypothesis it was written for — that the SIM identity write was racing the reload — and it was
     * tested and did not hold. Applying both layers with the reload confirmed complete, and with an
     * extra 2.5s on top, still deregistered IMS on SM-S938B. It is kept because not racing an
     * asynchronous reload you just triggered is correct regardless, and because the negative result is
     * worth keeping reproducible; it is not kept as a fix, and nothing should be worded as though the
     * cost were gone.</p>
     *
     * <p>Waiting on {@code getSimCountryIso} was tried first and is useless here: it reports the new
     * value the instant the bundle is accepted, so it returns before anything has reloaded.</p>
     *
     * <p>Returns whether the reload was observed. A write that landed without a confirming broadcast is
     * still a write that landed, so this reports rather than throws.</p>
     */
    private boolean writeAndAwaitReload(CarrierConfigManager manager, int subId,
            PersistableBundle values) throws Exception {
        return writeAndAwaitReload(manager, subId, values, false);
    }

    private boolean writeAndAwaitReload(CarrierConfigManager manager, int subId,
            PersistableBundle values, boolean persistent) throws Exception {
        CountDownLatch reloaded = new CountDownLatch(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int changed = intent.getIntExtra(
                        CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                if (changed == subId) {
                    reloaded.countDown();
                }
            }
        };
        Context context = getTargetContext();
        boolean observed = false;
        // Registered before the write, or a reload that finishes quickly is missed entirely and every
        // apply pays the full ceiling.
        context.registerReceiver(receiver,
                new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        try {
            invokeOverride(manager, subId, values, persistent);
            observed = reloaded.await(SETTLE_CEILING_MILLIS, TimeUnit.MILLISECONDS);
        } finally {
            try {
                context.unregisterReceiver(receiver);
            } catch (Throwable ignored) {
                // Instrumentation is ending either way; a receiver leak here outlives nothing.
            }
        }
        return observed;
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

    // This app exists to exercise the platform's test-only override. Instrumentation starts with
    // hidden-API checks disabled and the method is probed at runtime because vendor signatures differ.
    @SuppressLint("BlockedPrivateApi")
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
