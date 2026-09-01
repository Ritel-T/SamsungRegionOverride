package com.riteldevelopment.carriertestoverride;

import static org.junit.Assert.assertEquals;

import android.app.UiAutomation;

import org.junit.Test;

public final class CarrierConfigInstrumentationTest {
    @Test
    public void disablesAccessibilityConnectionOnModernAndroid() {
        assertEquals(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
                CarrierConfigInstrumentation.automationFlagsForSdk(30));
        assertEquals(
                UiAutomation.FLAG_DONT_USE_ACCESSIBILITY,
                CarrierConfigInstrumentation.automationFlagsForSdk(31));
        assertEquals(
                UiAutomation.FLAG_DONT_USE_ACCESSIBILITY,
                CarrierConfigInstrumentation.automationFlagsForSdk(37));
    }
}
