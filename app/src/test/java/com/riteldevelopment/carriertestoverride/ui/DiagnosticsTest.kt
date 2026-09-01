package com.riteldevelopment.carriertestoverride.ui

import com.riteldevelopment.carriertestoverride.data.OperationKind
import com.riteldevelopment.carriertestoverride.data.OverrideRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test
    fun safeTextKeepsSupportFieldsButDropsPrivateIdentifiers() {
        val text = DiagnosticReport(
            appVersion = "3.8.0-debug",
            manufacturer = "Samsung",
            model = "SM-S938B",
            apiLevel = 37,
            operation = OperationKind.APPLY,
            slotIndex = 1,
            layers = "NETWORK",
            targetCountry = "gb",
            targetAppCount = 3,
            result = ResultTone.ERROR,
            ims = DiagnosticIms.UNKNOWN,
            shizuku = DiagnosticShizuku.CONNECTED_GRANTED,
            stage = OverrideRepository.Stage.BINDING,
            failure = DiagnosticFailure.OPERATION,
            runtime = DiagnosticRuntime.AVAILABLE,
            exception = "SecurityException",
            durationMs = 42,
        ).toSafeText()

        assertTrue(text.startsWith("SRO-DIAGNOSTIC/1"))
        assertTrue(text.contains("operation=APPLY"))
        assertTrue(text.contains("target_country=gb"))
        assertTrue(text.contains("stage=BINDING"))
        assertTrue(text.contains("exception=SecurityException"))
        assertFalse(text.contains("subId"))
        assertFalse(text.contains("123456789012345"))
        assertFalse(text.contains("uid=2000"))
    }

    @Test
    fun safeTextNormalizesInvalidCountryAndDeviceCharacters() {
        val text = DiagnosticReport(
            appVersion = "3.8.0 beta/1",
            manufacturer = "Samsung Mobile",
            model = "SM/S938B",
            apiLevel = 37,
            operation = null,
            slotIndex = null,
            layers = "NONE",
            targetCountry = "not-a-country",
            targetAppCount = 0,
            result = ResultTone.IDLE,
            ims = DiagnosticIms.UNKNOWN,
            shizuku = DiagnosticShizuku.NOT_RUNNING,
            stage = null,
            failure = DiagnosticFailure.VALIDATION,
            runtime = DiagnosticRuntime.NOT_REQUESTED,
        ).toSafeText()

        assertTrue(text.contains("app=3.8.0beta1"))
        assertTrue(text.contains("device=SamsungMobile_SMS938B"))
        assertTrue(text.contains("target_country=UNKNOWN"))
        assertTrue(text.contains("slot=UNKNOWN"))
    }
}
