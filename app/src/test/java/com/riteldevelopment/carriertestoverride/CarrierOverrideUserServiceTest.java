package com.riteldevelopment.carriertestoverride;

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
}
