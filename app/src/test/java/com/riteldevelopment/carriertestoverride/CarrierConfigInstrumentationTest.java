package com.riteldevelopment.carriertestoverride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;

import org.junit.Test;

public final class CarrierConfigInstrumentationTest {
    @Test
    public void avoidsTheBrokenNoAccessibilityLifecycleOnAndroid17() {
        assertEquals(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
                CarrierConfigInstrumentation.automationFlagsForSdk(30));
        assertEquals(
                UiAutomation.FLAG_DONT_USE_ACCESSIBILITY,
                CarrierConfigInstrumentation.automationFlagsForSdk(31));
        assertEquals(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
                CarrierConfigInstrumentation.automationFlagsForSdk(37));
    }

    @Test
    public void acceptsTypedAndCommandLineArguments() {
        assertEquals(2, CarrierConfigInstrumentation.intArgument(2, -1));
        assertEquals(2, CarrierConfigInstrumentation.intArgument("2", -1));
        assertEquals(-1, CarrierConfigInstrumentation.intArgument("not-an-int", -1));

        assertTrue(CarrierConfigInstrumentation.booleanArgument(true, false));
        assertTrue(CarrierConfigInstrumentation.booleanArgument("true", false));
        assertFalse(CarrierConfigInstrumentation.booleanArgument("false", true));
        assertTrue(CarrierConfigInstrumentation.booleanArgument("not-a-bool", true));
    }
}
