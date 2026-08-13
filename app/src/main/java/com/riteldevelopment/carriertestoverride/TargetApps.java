package com.riteldevelopment.carriertestoverride;

import android.os.Process;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Makes the apps whose region this tool exists to change actually re-read it.
 *
 * <p>Changing what telephony reports is only half the job: Galaxy Store, Samsung Members and TikTok
 * all latch the region they saw at startup, so an override that has landed at the framework level is
 * invisible to them until their process is gone. Everything here runs as the shell UID inside the
 * Shizuku UserService, which is the only identity that may stop or wipe another package.</p>
 *
 * <p>Every step is a bounded subprocess and every outcome is reported verbatim. That is not defensive
 * padding — see {@link #WIPE_CACHE}, which is a documented no-op on the primary target device.</p>
 */
public final class TargetApps {

    /** Leave storage alone; only stop (and optionally relaunch) the app. */
    public static final int WIPE_NONE = 0;

    /**
     * {@code pm clear --cache-only}.
     *
     * <p>On SM-S938B / Android 16 / One UI 8.5 this call never returns and never deletes anything:
     * the platform accepts it, then never invokes the {@code IPackageDataObserver} the shell command
     * blocks on. Verified by seeding a file in a debuggable package's cache directory, issuing the
     * command, and finding the file still present after the call was killed. It is kept because it is
     * the correct API and works elsewhere, but it is timeout-bounded and reports {@code timeout} so the
     * UI never claims a wipe that did not happen.</p>
     */
    public static final int WIPE_CACHE = 1;

    /**
     * {@code pm clear}. Destructive — it signs the user out — but it is the only wipe that works on the
     * primary target device, and the region an app caches usually lives in its data rather than its
     * cache, so it is also the one that reliably forces a re-detect.
     */
    public static final int WIPE_DATA = 2;

    /**
     * The apps this tool exists to influence. Kotlin reads this list for the picker through
     * {@link #defaultPackages()}, so the set has exactly one definition.
     */
    private static final String[] DEFAULT_PACKAGES = {
            "com.sec.android.app.samsungapps",
            "com.samsung.android.voc",
            "com.zhiliaoapp.musically"
    };

    /** A copy, so a caller cannot rewrite the list every other caller depends on. */
    public static String[] defaultPackages() {
        return DEFAULT_PACKAGES.clone();
    }

    private static final long STOP_TIMEOUT_SECONDS = 5;
    private static final long WIPE_TIMEOUT_SECONDS = 12;
    private static final long QUERY_TIMEOUT_SECONDS = 5;
    private static final long LAUNCH_TIMEOUT_SECONDS = 8;

    private TargetApps() {
    }

    /** Stops the default targets. Runs after every successful apply/restore. */
    static String forceStopDefaults() {
        return refresh(DEFAULT_PACKAGES, WIPE_NONE, false);
    }

    static String refresh(String[] packages, int wipeMode, boolean relaunch) {
        // Only a null list means "you decide"; an empty one means the caller resolved the set to nothing
        // and must not be answered by stopping every default target instead.
        String[] targets = packages == null ? DEFAULT_PACKAGES : packages;
        int userId = currentUserId();

        StringBuilder report = new StringBuilder("Target apps · user ").append(userId)
                .append(" · wipe=").append(wipeName(wipeMode))
                .append(relaunch ? " · relaunch" : "");

        // Stopping and wiping happen for every target first, then launching runs in reverse order, so
        // the app listed first ends up on top of the stack rather than buried under the ones after it.
        List<String> launchable = new ArrayList<>();
        for (String packageName : targets) {
            report.append('\n').append(packageName).append(": ");
            if (!isInstalled(packageName, userId)) {
                report.append("not installed");
                continue;
            }
            report.append(describe(forceStop(packageName)));
            if (wipeMode != WIPE_NONE) {
                report.append(", ").append(wipeName(wipeMode)).append(' ')
                        .append(describe(wipe(packageName, userId, wipeMode)));
            }
            if (relaunch) {
                launchable.add(packageName);
            }
        }

        for (int index = launchable.size() - 1; index >= 0; index--) {
            String packageName = launchable.get(index);
            report.append('\n').append(packageName).append(": launch ")
                    .append(launch(packageName, userId));
        }
        return report.toString();
    }

    /**
     * The user this shell is running as. {@code uid / 100000} is {@code UserHandle.getUserId} without
     * the hidden API; the literal is the platform's {@code PER_USER_RANGE}. Needed because {@code pm}
     * only accepts a numeric {@code --user}, unlike {@code am}, which understands {@code current}.
     */
    private static int currentUserId() {
        return Process.myUid() / 100000;
    }

    private static boolean isInstalled(String packageName, int userId) {
        Result result = run(QUERY_TIMEOUT_SECONDS,
                "/system/bin/pm", "path", "--user", String.valueOf(userId), packageName);
        return result.exitCode == 0 && result.output.contains("package:");
    }

    private static Result forceStop(String packageName) {
        return run(STOP_TIMEOUT_SECONDS,
                "/system/bin/am", "force-stop", "--user", "current", packageName);
    }

    private static Result wipe(String packageName, int userId, int wipeMode) {
        if (wipeMode == WIPE_CACHE) {
            return run(WIPE_TIMEOUT_SECONDS, "/system/bin/pm", "clear", "--cache-only",
                    "--user", String.valueOf(userId), packageName);
        }
        return run(WIPE_TIMEOUT_SECONDS, "/system/bin/pm", "clear",
                "--user", String.valueOf(userId), packageName);
    }

    /**
     * Resolving the launcher activity explicitly, rather than letting {@code am start} match on the
     * intent, keeps the failure legible: an app with no launcher entry says so instead of producing an
     * {@code am} usage error that reads like a bug in this tool.
     */
    private static String launch(String packageName, int userId) {
        Result resolved = run(QUERY_TIMEOUT_SECONDS, "/system/bin/cmd", "package",
                "resolve-activity", "--brief", "--user", String.valueOf(userId),
                "-c", "android.intent.category.LAUNCHER", packageName);
        String component = lastLine(resolved.output);
        if (resolved.timedOut) {
            return "timeout resolving";
        }
        if (resolved.exitCode != 0 || component == null || !component.contains("/")) {
            return "no launcher activity";
        }
        Result started = run(LAUNCH_TIMEOUT_SECONDS, "/system/bin/am", "start",
                "--user", "current",
                "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.LAUNCHER",
                "-n", component);
        return describe(started);
    }

    private static String lastLine(String output) {
        String candidate = null;
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                candidate = trimmed;
            }
        }
        return candidate;
    }

    private static String wipeName(int wipeMode) {
        switch (wipeMode) {
            case WIPE_CACHE:
                return "cache";
            case WIPE_DATA:
                return "data";
            default:
                return "none";
        }
    }

    private static String describe(Result result) {
        if (result.timedOut) {
            return "timeout";
        }
        if (result.exitCode == 0) {
            return "ok";
        }
        String detail = firstLine(result.output);
        return "exit" + result.exitCode + (detail == null ? "" : " (" + detail + ")");
    }

    private static String firstLine(String output) {
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
            }
        }
        return null;
    }

    private static Result run(long timeoutSeconds, String... command) {
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            // Drained on a separate thread: a command that hangs would otherwise hang this thread too,
            // and a command that talks more than the pipe buffer holds would deadlock against waitFor.
            StreamDrain drain = new StreamDrain(process.getInputStream());
            Thread reader = new Thread(drain, "target-app-output");
            reader.setDaemon(true);
            reader.start();

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(Integer.MIN_VALUE, drain.text(), true);
            }
            reader.join(TimeUnit.SECONDS.toMillis(1));
            return new Result(process.exitValue(), drain.text(), false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Result(Integer.MIN_VALUE, "interrupted", true);
        } catch (Throwable throwable) {
            return new Result(-1, throwable.getClass().getSimpleName(), false);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static final class Result {
        final int exitCode;
        final String output;
        final boolean timedOut;

        Result(int exitCode, String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.timedOut = timedOut;
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
            try (Reader reader = new InputStreamReader(source)) {
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    synchronized (sink) {
                        // Bounded: a runaway command must not grow this without limit, and nothing
                        // useful lives past the first few lines of a shell command's output.
                        if (sink.length() < 4096) {
                            sink.append(buffer, 0, read);
                        }
                    }
                }
            } catch (Throwable ignored) {
                // The stream dies with the process; whatever arrived before that is still reported.
            }
        }

        String text() {
            synchronized (sink) {
                return sink.toString();
            }
        }
    }
}
