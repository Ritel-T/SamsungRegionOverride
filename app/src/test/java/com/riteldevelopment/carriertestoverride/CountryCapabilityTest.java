package com.riteldevelopment.carriertestoverride;

import android.os.PersistableBundle;
import org.junit.Test;
import static org.junit.Assert.*;

public final class CountryCapabilityTest {
    @Test public void android11IsRejectedWithoutInspectingOrStartingInstrumentation() {
        for (int sdk : new int[]{29, 30}) {
            String probe = CountryCapability.inspect(sdk);
            assertEquals(CountryCapability.Status.UNSUPPORTED_ANDROID, CountryCapability.status(probe));
            assertTrue(assertThrows(UnsupportedOperationException.class,
                    () -> CountryCapability.requireSupported(probe)).getMessage().contains("API 31"));
        }
    }

    @Test public void missingLegacyGetterCannotBeReportedAsSupported() {
        assertEquals(CountryCapability.Status.UNAVAILABLE,
                CountryCapability.status(CountryCapability.inspect(31, OwnerAwareManager.class, Config.class)));
        assertEquals(CountryCapability.Status.SUPPORTED,
                CountryCapability.status(CountryCapability.inspect(35, OwnerAwareManager.class, Config.class)));
    }

    @Test public void currentPathRequiresItsActualMethods() {
        String probe = CountryCapability.inspect(31, LegacyManager.class, Config.class);
        assertNull(CountryCapability.failureReason(probe));
        assertEquals(CountryCapability.Status.UNAVAILABLE,
                CountryCapability.status(CountryCapability.inspect(31, LegacyManager.class, Object.class)));
    }

    @Test public void networkProbeDoesNotProveCountrySupport() {
        String networkOnly = "uid=2000\nproxy=Proxy\nmethods=setCarrierTestOverride(...)";
        assertEquals(CountryCapability.Status.UNKNOWN, CountryCapability.status(networkOnly));
        assertThrows(UnsupportedOperationException.class, () -> CountryCapability.requireSupported(networkOnly));
        assertEquals(CountryCapability.Status.UNSUPPORTED_ANDROID,
                CountryCapability.status(networkOnly + "\ncountry_path=UNSUPPORTED_ANDROID"));
    }

    public static class OwnerAwareManager {
        public void startDelegateShellPermissionIdentity(int uid, String[] permissions) {}
    }
    public static class LegacyManager extends OwnerAwareManager {
        public String[] getDelegatedShellPermissions() { return null; }
    }
    public static class Config {
        public void overrideConfig(int subId, PersistableBundle values, boolean persistent) {}
    }
}
