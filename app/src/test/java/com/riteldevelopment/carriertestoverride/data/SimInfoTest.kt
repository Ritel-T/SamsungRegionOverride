package com.riteldevelopment.carriertestoverride.data

import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Test

class SimInfoTest {
    @Test
    fun networkFirstUsesSavedMccForRealCountry() {
        val sim = SimInfo(
            slotIndex = 1,
            subId = 4,
            simState = TelephonyManager.SIM_STATE_READY,
            operatorNumeric = "23430",
            operatorName = "EE",
            countryIso = "gb",
            flags = OverrideStore.Flags(simIdentity = true, appCountry = false),
            original = OverrideStore.Snapshot(
                mccMnc = "46009",
                operatorName = "China Unicom",
                countryIso = null,
                displayName = null,
                displayNameSource = OverrideStore.DISPLAY_NAME_SOURCE_NONE,
            ),
        )

        assertEquals("cn", sim.realCountryIso)
    }
}
