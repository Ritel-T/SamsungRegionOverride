package com.riteldevelopment.carriertestoverride;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Method;

import org.junit.Test;

public final class TelephonyBridgeTest {
    interface IntBoolean {
        void setUiccApplicationsEnabled(int subId, boolean enabled);
    }

    interface BooleanIntWithDecoy {
        void setUiccApplicationsEnabled(String unsupported);
        void setUiccApplicationsEnabled(boolean enabled, int subId);
    }

    interface UnsupportedOnly {
        void setUiccApplicationsEnabled(int subId, boolean enabled, String packageName);
    }

    @Test
    public void findsBothKnownArgumentOrders() {
        assertArrayEquals(
                new Class<?>[]{int.class, boolean.class},
                TelephonyBridge.findUiccToggleMethod(IntBoolean.class).getParameterTypes());
        assertArrayEquals(
                new Class<?>[]{boolean.class, int.class},
                TelephonyBridge.findUiccToggleMethod(BooleanIntWithDecoy.class).getParameterTypes());
    }

    @Test
    public void rejectsUnknownOverloads() {
        Method method = TelephonyBridge.findUiccToggleMethod(UnsupportedOnly.class);
        assertNull(method);
    }
}
