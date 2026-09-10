package com.riteldevelopment.carriertestoverride;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.List;

/** Per-run protocol between the app instrumentation and its shell command host. */
final class PermissionHandshake {
    static final String ARG_TOKEN = "sro_permission_token";
    static final String STATUS_KEY = "sro_permission_request";
    static final String STATUS_PREFIX = "INSTRUMENTATION_STATUS: " + STATUS_KEY + "=";
    static final long TIMEOUT_MILLIS = 5000L;

    interface Grant { void grant(int uid) throws Exception; }
    interface Sleeper { void sleep(long millis) throws InterruptedException; }
    interface DelegationReader { List<String> read() throws Exception; }

    private final String token;
    private final Grant grant;
    private boolean attempted;
    private volatile Throwable failure;

    PermissionHandshake(String token, Grant grant) {
        this.token = token;
        this.grant = grant;
    }

    synchronized void acceptLine(String line) {
        String prefix = STATUS_PREFIX + token + ":";
        if (!line.startsWith(prefix) || attempted) return;
        attempted = true;
        try {
            int uid = Integer.parseInt(line.substring(prefix.length()));
            // Android allocates application IDs within each user's 100000-UID range.
            int appId = uid % 100000;
            if (uid < 0 || appId < 10000 || appId > 19999) {
                throw new SecurityException("Invalid instrumentation application UID: " + uid);
            }
            grant.grant(uid);
        } catch (Exception error) {
            failure = error;
        }
    }

    void throwIfFailed() {
        Throwable error = failure;
        if (error != null) {
            throw new IllegalStateException("delegate permissions: "
                    + error.getClass().getName() + ": " + error.getMessage(), error);
        }
    }

    static void ensureLegacyDelegationAvailable(int sdkInt, DelegationReader reader) {
        if (sdkInt >= 35) return;
        final List<String> existing;
        try {
            existing = reader.read();
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot check existing shell permission delegation; "
                    + "instrumentation was not started", failure);
        }
        // Null means all shell permissions are delegated; an empty list means none.
        if (existing == null || !existing.isEmpty()) {
            throw new IllegalStateException("Another shell permission delegation is active; "
                    + "instrumentation was not started because this Android version may revoke it");
        }
    }

    static void awaitPermissions(BooleanSupplier granted, LongSupplier clock, Sleeper sleeper)
            throws InterruptedException {
        long started = clock.getAsLong();
        while (!granted.getAsBoolean()) {
            long remaining = TIMEOUT_MILLIS - (clock.getAsLong() - started);
            if (remaining <= 0) {
                throw new IllegalStateException("Shell permission delegation was not confirmed within "
                        + TIMEOUT_MILLIS + " ms; no CarrierConfig write was attempted");
            }
            sleeper.sleep(Math.min(50L, remaining));
        }
    }
}
