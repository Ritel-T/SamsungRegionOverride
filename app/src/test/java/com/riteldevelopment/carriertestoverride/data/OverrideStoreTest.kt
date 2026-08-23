package com.riteldevelopment.carriertestoverride.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideStoreTest {
    @Test
    fun legacySnapshotWithoutFingerprintRemainsUnbound() {
        assertTrue(
            isLegacyUnboundState(
                hasFingerprint = false,
                fingerprintWasUnavailable = false,
                hasSnapshot = true,
                hasLiveOrPendingFlag = false,
            )
        )
    }

    @Test
    fun legacyFlagWithoutFingerprintRemainsUnbound() {
        assertTrue(
            isLegacyUnboundState(
                hasFingerprint = false,
                fingerprintWasUnavailable = false,
                hasSnapshot = false,
                hasLiveOrPendingFlag = true,
            )
        )
    }

    @Test
    fun newOrExplicitlyUnavailableFingerprintStateIsNotLegacy() {
        assertFalse(isLegacyUnboundState(false, false, false, false))
        assertFalse(isLegacyUnboundState(true, false, true, true))
        assertFalse(isLegacyUnboundState(false, true, true, true))
    }

    @Test
    fun failedApplyKeepsUncertaintyWithoutInventingSuccess() {
        assertEquals(
            ApplyLayerState(live = false, pending = true),
            resolveApplyLayer(
                previous = ApplyLayerState(live = false, pending = true),
                attempted = true,
                succeeded = false,
            )
        )
    }

    @Test
    fun failedUpdatePreservesOlderLiveLayerAndPending() {
        assertEquals(
            ApplyLayerState(live = true, pending = true),
            resolveApplyLayer(
                previous = ApplyLayerState(live = true, pending = true),
                attempted = true,
                succeeded = false,
            )
        )
    }

    @Test
    fun explicitSuccessClearsPendingAndMarksLayerLive() {
        assertEquals(
            ApplyLayerState(live = true, pending = false),
            resolveApplyLayer(
                previous = ApplyLayerState(live = false, pending = true),
                attempted = true,
                succeeded = true,
            )
        )
    }

    @Test
    fun unattemptedLayerKeepsItsPriorState() {
        val previous = ApplyLayerState(live = false, pending = true)
        assertEquals(
            previous,
            resolveApplyLayer(previous, attempted = false, succeeded = false)
        )
    }
}
