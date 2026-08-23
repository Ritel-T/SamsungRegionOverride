package com.riteldevelopment.carriertestoverride.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideRepositoryTest {
    @Test
    fun pendingNetworkIsTreatedAsPossiblyLive() {
        assertTrue(
            networkMayBeLive(
                OverrideStore.Flags(
                    simIdentity = false,
                    appCountry = false,
                    simPending = true,
                )
            )
        )
    }

    @Test
    fun confirmedNetworkFlagIsTreatedAsLive() {
        assertTrue(
            networkMayBeLive(
                OverrideStore.Flags(simIdentity = true, appCountry = false)
            )
        )
    }

    @Test
    fun networkIsKnownRealOnlyWithoutLiveOrPendingState() {
        assertFalse(
            networkMayBeLive(
                OverrideStore.Flags(simIdentity = false, appCountry = true)
            )
        )
    }
}
