package com.riteldevelopment.carriertestoverride;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Collections;
import static org.junit.Assert.*;

public final class PermissionHandshakeTest {
    @Test public void legacyPreflightAllowsOnlyAnEmptyDelegation() {
        PermissionHandshake.ensureLegacyDelegationAvailable(34, Collections::emptyList);
        assertThrows(IllegalStateException.class, () ->
                PermissionHandshake.ensureLegacyDelegationAvailable(34,
                        () -> Collections.singletonList("android.permission.READ_PHONE_STATE")));
        // The platform uses null for delegation of every shell permission.
        assertThrows(IllegalStateException.class, () ->
                PermissionHandshake.ensureLegacyDelegationAvailable(31, () -> null));
    }

    @Test public void legacyPreflightFailsClosedWhenInspectionIsUnavailable() {
        NoSuchMethodException cause = new NoSuchMethodException("getDelegatedShellPermissions");
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                PermissionHandshake.ensureLegacyDelegationAvailable(34, () -> { throw cause; }));
        assertSame(cause, failure.getCause());
        assertTrue(failure.getMessage().contains("instrumentation was not started"));
    }

    @Test public void ownerAwareFrameworkDoesNotInspectOtherDelegations() {
        PermissionHandshake.ensureLegacyDelegationAvailable(35, () -> {
            fail("Android 15 and later must not run the legacy preflight");
            return null;
        });
    }

    @Test public void onlyMatchingRunCanGrantAndOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        PermissionHandshake request = new PermissionHandshake("expected", uid -> {
            assertEquals(110123, uid);
            calls.incrementAndGet();
        });
        request.acceptLine(PermissionHandshake.STATUS_PREFIX + "other:110123");
        request.acceptLine(PermissionHandshake.STATUS_PREFIX + "expected:110123");
        request.acceptLine(PermissionHandshake.STATUS_PREFIX + "expected:110123");
        request.throwIfFailed();
        assertEquals(1, calls.get());
    }

    @Test public void rejectsPrivilegedAndMalformedUidsBeforeGrant() {
        for (String uid : new String[] {"2000", "0", "-1", "199999", "n/a"}) {
            PermissionHandshake request = new PermissionHandshake("run", value -> fail("grant"));
            request.acceptLine(PermissionHandshake.STATUS_PREFIX + "run:" + uid);
            assertThrows(IllegalStateException.class, request::throwIfFailed);
        }
    }

    @Test public void preservesGrantFailureAndDoesNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        SecurityException cause = new SecurityException("another instrumentation owns delegation");
        PermissionHandshake request = new PermissionHandshake("run", uid -> {
            calls.incrementAndGet();
            throw cause;
        });
        request.acceptLine(PermissionHandshake.STATUS_PREFIX + "run:10123");
        request.acceptLine(PermissionHandshake.STATUS_PREFIX + "run:10123");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                request::throwIfFailed);
        assertSame(cause, failure.getCause());
        assertTrue(failure.getMessage().contains(cause.getMessage()));
        assertEquals(1, calls.get());
    }

    @Test public void waitsForActualPermissionBeforeProceeding() throws Exception {
        AtomicLong time = new AtomicLong();
        PermissionHandshake.awaitPermissions(() -> time.get() >= 100,
                time::get, time::addAndGet);
        assertEquals(100, time.get());
    }

    @Test public void deniesWriteWhenPermissionNeverArrives() {
        AtomicLong time = new AtomicLong();
        AtomicInteger writes = new AtomicInteger();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> {
            PermissionHandshake.awaitPermissions(() -> false, time::get, time::addAndGet);
            writes.incrementAndGet();
        });
        assertEquals(PermissionHandshake.TIMEOUT_MILLIS, time.get());
        assertEquals(0, writes.get());
        assertTrue(failure.getMessage().contains("no CarrierConfig write"));
    }

    @Test public void interruptionDoesNotPermitAWrite() {
        assertThrows(InterruptedException.class, () -> PermissionHandshake.awaitPermissions(
                () -> false, () -> 0L, millis -> { throw new InterruptedException("stopped"); }));
    }
}
