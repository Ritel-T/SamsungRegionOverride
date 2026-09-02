package com.riteldevelopment.carriertestoverride;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class CarrierConfigBridgeTest {
    @Test
    public void parsesEncodedMultilineResult() {
        String message = "App country: cleared\nsubId=2, ISO=cn";
        String output = "INSTRUMENTATION_RESULT: sro_message_b64=" + encode(message)
                + "\nINSTRUMENTATION_CODE: " + Activity.RESULT_OK + "\n";

        assertEquals(message, CarrierConfigBridge.parseInstrumentationOutput(output, 0));
    }

    @Test
    public void surfacesEncodedInstrumentationError() {
        String output = "INSTRUMENTATION_RESULT: sro_error_b64="
                + encode("java.lang.SecurityException: denied")
                + "\nINSTRUMENTATION_CODE: 0\n";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> CarrierConfigBridge.parseInstrumentationOutput(output, 0));

        assertEquals("java.lang.SecurityException: denied", failure.getMessage());
    }

    @Test
    public void rejectsProcessCrashWithoutPayload() {
        String output = "INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n"
                + "INSTRUMENTATION_CODE: 0\n";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> CarrierConfigBridge.parseInstrumentationOutput(output, 0));

        assertTrue(failure.getMessage().contains("Process crashed"));
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
