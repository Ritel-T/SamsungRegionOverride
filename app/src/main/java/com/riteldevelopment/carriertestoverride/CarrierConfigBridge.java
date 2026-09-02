package com.riteldevelopment.carriertestoverride;

import android.app.Activity;
import android.os.Bundle;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
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
     * Uses Android's own command host for the watcher and UiAutomationConnection lifecycle.
     *
     * <p>Hosting those Binder objects inside a Shizuku UserService worked through Android 16, but
     * Android 17 can leave UiAutomation in CONNECTING while instrumentation is finishing. A normal
     * instrumentation start is not an option because ActivityManager force-stops every process in the
     * package, including the separate UI process. The platform command with {@code --no-restart}
     * follows the same supported shell path without taking the interface down.</p>
     */
    private static synchronized String runInstrumentation(Bundle arguments) throws Exception {
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder(buildCommand(arguments))
                    .redirectErrorStream(true)
                    .start();
            StreamDrain drain = new StreamDrain(process.getInputStream());
            Thread reader = new Thread(drain, "carrier-config-instrumentation-output");
            reader.setDaemon(true);
            reader.start();

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                reader.join(TimeUnit.SECONDS.toMillis(1));
                throw new IllegalStateException("CarrierConfig instrumentation timed out after "
                        + TIMEOUT_SECONDS + " seconds");
            }
            reader.join(TimeUnit.SECONDS.toMillis(1));
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

        StreamDrain(InputStream source) {
            this.source = source;
        }

        @Override
        public void run() {
            char[] buffer = new char[512];
            try (Reader reader = new InputStreamReader(source, StandardCharsets.UTF_8)) {
                int read;
                while ((read = reader.read(buffer)) != -1) {
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
