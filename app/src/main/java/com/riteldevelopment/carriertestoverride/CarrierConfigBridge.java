package com.riteldevelopment.carriertestoverride;

import android.app.Activity;
import android.Manifest;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Build;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/** Starts the registered CarrierConfig instrumentation from the shell UserService. */
final class CarrierConfigBridge {
    private static final String AM_PATH = "/system/bin/am";
    private static final String INSTRUMENTATION_CLASS =
            "com.riteldevelopment.carriertestoverride.CarrierConfigInstrumentation";
    private static final String RESULT_PREFIX = "INSTRUMENTATION_RESULT: ";
    private static final String CODE_PREFIX = "INSTRUMENTATION_CODE: ";
    private static final long TIMEOUT_SECONDS = 25;
    private static final int MAX_OUTPUT_CHARS = 16_384;

    private CarrierConfigBridge() {
    }

    static String inspectRuntime() throws Exception {
        Bundle arguments = new Bundle();
        arguments.putString(CarrierConfigInstrumentation.ARG_ACTION,
                CarrierConfigInstrumentation.ACTION_PROBE);
        return runInstrumentation(arguments);
    }

    static String applyTransient(int subId, String countryIso, String carrierName,
            boolean overrideCarrierName) throws Exception {
        requireValidSubId(subId);
        Bundle arguments = new Bundle();
        arguments.putString(CarrierConfigInstrumentation.ARG_ACTION,
                CarrierConfigInstrumentation.ACTION_APPLY_TRANSIENT);
        arguments.putInt(CarrierConfigInstrumentation.ARG_SUB_ID, subId);
        arguments.putString(CarrierConfigInstrumentation.ARG_COUNTRY_ISO, countryIso);
        arguments.putString(CarrierConfigInstrumentation.ARG_CARRIER_NAME, carrierName);
        arguments.putBoolean(CarrierConfigInstrumentation.ARG_OVERRIDE_NAME,
                overrideCarrierName);
        return runInstrumentation(arguments);
    }

    static String clearTransient(int subId, String restoreCountryIso) throws Exception {
        requireValidSubId(subId);
        Bundle arguments = new Bundle();
        arguments.putString(CarrierConfigInstrumentation.ARG_ACTION,
                CarrierConfigInstrumentation.ACTION_CLEAR_TRANSIENT);
        arguments.putInt(CarrierConfigInstrumentation.ARG_SUB_ID, subId);
        if (restoreCountryIso != null) {
            arguments.putString(CarrierConfigInstrumentation.ARG_COUNTRY_ISO,
                    restoreCountryIso);
        }
        return runInstrumentation(arguments);
    }

    static String clearAll(int subId) throws Exception {
        return runForSubscription(CarrierConfigInstrumentation.ACTION_CLEAR_ALL, subId);
    }

    private static String runForSubscription(String action, int subId) throws Exception {
        requireValidSubId(subId);
        Bundle arguments = new Bundle();
        arguments.putString(CarrierConfigInstrumentation.ARG_ACTION, action);
        arguments.putInt(CarrierConfigInstrumentation.ARG_SUB_ID, subId);
        return runInstrumentation(arguments);
    }

    /**
     * Uses Android's command host for instrumentation lifecycle and delegates phone permissions
     * directly from the shell. Its automation Binder remains unconnected, so other automation
     * services retain their accessibility connection.
     *
     * <p>The shell permission delegate requires an active instrumentation with the command host's
     * non-null automation Binder, but does not require that Binder to register an accessibility
     * service. {@code --no-restart} also prevents ActivityManager from force-stopping the separate
     * UI process while starting or finishing this operation.</p>
     */
    private static synchronized String runInstrumentation(Bundle arguments) throws Exception {
        java.lang.Process process = null;
        try {
            // Android 15+ finishes only its own delegation. Older frameworks may clear any
            // delegate when instrumentation ends, so reject an existing one before starting.
            // This read-only preflight is not atomic with another tool starting a delegation.
            PermissionHandshake.ensureLegacyDelegationAvailable(Build.VERSION.SDK_INT,
                    CarrierConfigBridge::readDelegatedPermissions);
            Bundle runArguments = new Bundle(arguments);
            String token = UUID.randomUUID().toString();
            runArguments.putString(PermissionHandshake.ARG_TOKEN, token);
            PermissionHandshake handshake = new PermissionHandshake(token,
                    CarrierConfigBridge::delegatePermissions);
            process = new ProcessBuilder(buildCommand(runArguments))
                    .redirectErrorStream(true)
                    .start();
            StreamDrain drain = new StreamDrain(process.getInputStream(), handshake);
            Thread reader = new Thread(drain, "carrier-config-instrumentation-output");
            reader.setDaemon(true);
            reader.start();

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                reader.join(TimeUnit.SECONDS.toMillis(1));
                handshake.throwIfFailed();
                throw new IllegalStateException("CarrierConfig instrumentation timed out after "
                        + TIMEOUT_SECONDS + " seconds");
            }
            reader.join(TimeUnit.SECONDS.toMillis(1));
            handshake.throwIfFailed();
            return parseInstrumentationOutput(drain.text(), process.exitValue());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CarrierConfig instrumentation was interrupted",
                    interrupted);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> readDelegatedPermissions() throws Exception {
        Object activity = service("activity", "android.app.IActivityManager");
        Method getter = Class.forName("android.app.IActivityManager")
                .getMethod("getDelegatedShellPermissions");
        return (List<String>) invoke(getter, activity);
    }

    private static void delegatePermissions(int uid) throws Exception {
        Object packages = service("package", "android.content.pm.IPackageManager");
        Method getPackages = Class.forName("android.content.pm.IPackageManager")
                .getMethod("getPackagesForUid", int.class);
        String[] names = (String[]) invoke(getPackages, packages, uid);
        if (names == null || !Arrays.asList(names).contains(BuildConfig.APPLICATION_ID)) {
            throw new SecurityException("Permission request UID does not belong to this app");
        }
        Object activity = service("activity", "android.app.IActivityManager");
        Method delegate = Class.forName("android.app.IActivityManager").getMethod(
                "startDelegateShellPermissionIdentity", int.class, String[].class);
        invoke(delegate, activity, uid, new String[] {
                Manifest.permission.MODIFY_PHONE_STATE, Manifest.permission.READ_PHONE_STATE});
        // Android 15+ normal finish revokes only its own delegation; older versions require the
        // pre-start guard above. Never call the global stop API and revoke another tool's identity.
    }

    private static Object service(String name, String interfaceName) throws Exception {
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, name);
        if (binder == null) throw new IllegalStateException(name + " service is unavailable");
        return Class.forName(interfaceName + "$Stub").getMethod("asInterface", IBinder.class)
                .invoke(null, binder);
    }

    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        }
    }

    private static List<String> buildCommand(Bundle arguments) {
        List<String> command = new ArrayList<>();
        Collections.addAll(command,
                AM_PATH,
                "instrument",
                "-w",
                "-r",
                "--no-restart",
                "--no-hidden-api-checks",
                "--user",
                "current");

        List<String> keys = new ArrayList<>(arguments.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            Object value = arguments.get(key);
            if (value == null) {
                continue;
            }
            command.add("-e");
            command.add(key);
            command.add(String.valueOf(value));
        }
        command.add(BuildConfig.APPLICATION_ID + "/" + INSTRUMENTATION_CLASS);
        return command;
    }

    static String parseInstrumentationOutput(String output, int exitCode) {
        String encodedError = resultValue(
                output, CarrierConfigInstrumentation.RESULT_ERROR_BASE64);
        if (encodedError != null) {
            throw new IllegalStateException(decode(encodedError));
        }

        String encodedMessage = resultValue(
                output, CarrierConfigInstrumentation.RESULT_MESSAGE_BASE64);
        Integer resultCode = instrumentationCode(output);
        if (exitCode != 0 || resultCode == null || resultCode != Activity.RESULT_OK
                || encodedMessage == null) {
            throw new IllegalStateException("CarrierConfig instrumentation failed: exit="
                    + exitCode + ", code=" + resultCode + ", output=" + compact(output));
        }
        return decode(encodedMessage);
    }

    private static String resultValue(String output, String key) {
        String prefix = RESULT_PREFIX + key + "=";
        for (String line : lines(output)) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    private static Integer instrumentationCode(String output) {
        for (String line : lines(output)) {
            if (!line.startsWith(CODE_PREFIX)) {
                continue;
            }
            try {
                return Integer.valueOf(line.substring(CODE_PREFIX.length()).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String decode(String encoded) {
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("CarrierConfig instrumentation returned invalid data",
                    invalid);
        }
    }

    private static String compact(String output) {
        String normalized = output == null ? "" : output.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private static String[] lines(String output) {
        return output == null ? new String[0] : output.split("\\R");
    }

    private static void requireValidSubId(int subId) {
        if (subId < 0) {
            throw new IllegalArgumentException("Invalid subId: " + subId);
        }
    }

    private static final class StreamDrain implements Runnable {
        private final InputStream source;
        private final StringBuilder sink = new StringBuilder();
        private final PermissionHandshake handshake;
        private final StringBuilder line = new StringBuilder();

        StreamDrain(InputStream source, PermissionHandshake handshake) {
            this.source = source;
            this.handshake = handshake;
        }

        @Override
        public void run() {
            char[] buffer = new char[512];
            try (Reader reader = new InputStreamReader(source, StandardCharsets.UTF_8)) {
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    for (int i = 0; i < read; i++) {
                        char character = buffer[i];
                        if (character == '\n') {
                            handshake.acceptLine(line.toString());
                            line.setLength(0);
                        } else if (character != '\r' && line.length() < MAX_OUTPUT_CHARS) {
                            line.append(character);
                        }
                    }
                    synchronized (sink) {
                        int remaining = MAX_OUTPUT_CHARS - sink.length();
                        if (remaining > 0) {
                            sink.append(buffer, 0, Math.min(read, remaining));
                        }
                    }
                }
            } catch (Throwable ignored) {
                // The process owns the stream; output received before it closed is still useful.
            }
        }

        String text() {
            synchronized (sink) {
                return sink.toString();
            }
        }
    }
}
