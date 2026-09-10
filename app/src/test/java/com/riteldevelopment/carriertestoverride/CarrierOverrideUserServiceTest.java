package com.riteldevelopment.carriertestoverride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CarrierOverrideUserServiceTest {
    @Test
    public void skipsRecoveryWhenFakeNetworkWasNotRestored() {
        assertFalse(CarrierOverrideUserService.canRecoverIms(true, false, false));
        assertFalse(CarrierOverrideUserService.canRecoverIms(true, true, false));
    }

    @Test
    public void allowsRecoveryOnceNetworkIsKnownReal() {
        assertTrue(CarrierOverrideUserService.canRecoverIms(false, false, false));
        assertTrue(CarrierOverrideUserService.canRecoverIms(true, true, true));
    }

    @Test
    public void registeredSubscriptionReturnsImmediately() {
        FakeImsProbe probe = new FakeImsProbe();
        probe.registerAtRead = 1;

        String result = CarrierOverrideUserService.observeImsRecovery(probe);

        assertEquals("IMS: registered at the time of restore.", result);
        assertEquals(1, probe.reads);
        assertEquals(0, probe.pauses);
    }

    @Test
    public void naturallyRecoveredRegistrationIsReportedWithoutAReconnectionAction() {
        FakeImsProbe probe = new FakeImsProbe();
        probe.registerAtRead = 3;

        String result = CarrierOverrideUserService.observeImsRecovery(probe);

        assertEquals("IMS: registered again after restore.", result);
        assertEquals(3, probe.reads);
        assertEquals(2000L, probe.elapsedMillis);
    }

    @Test
    public void persistentUnregistrationHasABoundedWindowAndDisabledSimGuidance() {
        FakeImsProbe probe = new FakeImsProbe();

        String result = CarrierOverrideUserService.observeImsRecovery(probe);

        assertUnconfirmed(result);
        assertTrue(result.contains("still not registered"));
        assertTrue(result.contains("If this SIM is disabled, enable it in Settings"));
        assertTrue(result.contains("reboot alone does not re-enable it"));
        assertEquals(16, probe.reads);
        assertEquals(15000L, probe.elapsedMillis);
    }

    @Test
    public void registrationAtTheEndOfTheWindowStillCounts() {
        FakeImsProbe probe = new FakeImsProbe();
        probe.registerAtRead = 16;

        assertEquals("IMS: registered again after restore.",
                CarrierOverrideUserService.observeImsRecovery(probe));
        assertEquals(15000L, probe.elapsedMillis);
    }

    @Test
    public void unreadableRegistrationDoesNotStartARecoveryMutationOrClaimSuccess() {
        FakeImsProbe probe = new FakeImsProbe();
        probe.failAtRead = 1;

        String result = CarrierOverrideUserService.observeImsRecovery(probe);

        assertUnconfirmed(result);
        assertTrue(result.contains("SecurityException"));
        assertEquals(0, probe.pauses);
    }

    @Test
    public void registrationReadFailureDuringObservationStopsPolling() {
        FakeImsProbe probe = new FakeImsProbe();
        probe.failAtRead = 3;

        String result = CarrierOverrideUserService.observeImsRecovery(probe);

        assertUnconfirmed(result);
        assertEquals(3, probe.reads);
        assertEquals(2000L, probe.elapsedMillis);
    }

    @Test
    public void interruptionRemainsUnconfirmedAndPreservesTheInterruptFlag() {
        FakeImsProbe probe = new FakeImsProbe();
        probe.interruptPause = true;
        try {
            String result = CarrierOverrideUserService.observeImsRecovery(probe);

            assertUnconfirmed(result);
            assertTrue(result.contains("observation was interrupted"));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, probe.reads);
        } finally {
            Thread.interrupted();
        }
    }

    private static void assertUnconfirmed(String result) {
        // The repository parses this marker independently of layer success/failure.
        assertTrue(result.startsWith(CarrierOverrideUserService.IMS_RECOVERY_UNCONFIRMED));
        assertFalse(result.contains("ERROR:"));
    }

    private static final class FakeImsProbe implements CarrierOverrideUserService.ImsRecoveryProbe {
        int registerAtRead = Integer.MAX_VALUE;
        int failAtRead = Integer.MAX_VALUE;
        boolean interruptPause;
        int reads;
        int pauses;
        long elapsedMillis;

        @Override
        public boolean isRegistered() {
            reads++;
            if (reads == failAtRead) {
                throw new SecurityException("Read unavailable");
            }
            return reads >= registerAtRead;
        }

        @Override
        public void pause(long millis) throws InterruptedException {
            pauses++;
            if (interruptPause) {
                throw new InterruptedException("Observation cancelled");
            }
            elapsedMillis += millis;
        }
    }
}
