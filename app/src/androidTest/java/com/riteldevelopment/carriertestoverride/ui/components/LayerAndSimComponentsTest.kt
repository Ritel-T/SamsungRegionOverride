package com.riteldevelopment.carriertestoverride.ui.components

import android.telephony.TelephonyManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.data.OverrideStore
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.ui.theme.CarrierOverrideTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerAndSimComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun enableAccessibilityValidation() {
        compose.enableAccessibilityChecks()
    }

    @Test
    fun layerSectionRaisesTheStillLiveWarningOnlyWhenDisarmed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stillLive = context.getString(R.string.layer_still_live)
        val live = context.getString(R.string.badge_live)
        var armed by mutableStateOf(true)

        compose.setContent {
            CarrierOverrideTheme(darkTheme = true, dynamicColor = false) {
                LayerSection(
                    title = "Network",
                    subtitle = "23430",
                    enabled = armed,
                    applied = true,
                    accent = MaterialTheme.colorScheme.primary,
                    controlsEnabled = true,
                    onEnabledChange = { armed = it },
                    liveButDisarmedText = stillLive,
                ) {}
            }
        }

        compose.onNodeWithText(live).assertIsDisplayed()
        compose.onNodeWithText(stillLive).assertDoesNotExist()
        compose.onNode(isToggleable()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(stillLive).assertIsDisplayed()
        compose.onNodeWithText(live).assertIsDisplayed()
        compose.onNode(isToggleable()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(stillLive).assertDoesNotExist()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun stateBadgeHoldsItsSizeAcrossTheActiveFlip() {
        var active by mutableStateOf(false)

        compose.setContent {
            CarrierOverrideTheme(darkTheme = true, dynamicColor = false) {
                StateBadge(text = "CTY", active = active)
            }
        }

        val inactive = compose.onNodeWithText("CTY").getUnclippedBoundsInRoot()
        active = true
        compose.waitForIdle()
        val filled = compose.onNodeWithText("CTY").getUnclippedBoundsInRoot()
        assertEquals(inactive.width, filled.width)
        assertEquals(inactive.height, filled.height)
    }

    @Test
    fun dataBadgeDoesNotMoveOneSimCardsIdentityRowsLower() {
        val emptyFlags = OverrideStore.Flags(simIdentity = false, appCountry = false)
        val emptySnapshot = OverrideStore.Snapshot(
            mccMnc = null,
            operatorName = null,
            countryIso = null,
            displayName = null,
            displayNameSource = OverrideStore.DISPLAY_NAME_SOURCE_NONE,
        )
        val sims = listOf(
            SimInfo(
                slotIndex = 0,
                subId = 11,
                simState = TelephonyManager.SIM_STATE_READY,
                operatorNumeric = "46001",
                operatorName = "Carrier A",
                countryIso = "cn",
                flags = emptyFlags,
                original = emptySnapshot,
                isDefaultData = true,
            ),
            SimInfo(
                slotIndex = 1,
                subId = 22,
                simState = TelephonyManager.SIM_STATE_READY,
                operatorNumeric = "23430",
                operatorName = "Carrier B",
                countryIso = "gb",
                flags = emptyFlags,
                original = emptySnapshot,
                isDefaultData = false,
            ),
        )

        compose.setContent {
            CarrierOverrideTheme(darkTheme = true, dynamicColor = false) {
                SimSelector(
                    sims = sims,
                    slotCount = 2,
                    selectedSubId = 11,
                    scanError = null,
                    enabled = true,
                    onSelect = {},
                )
            }
        }

        val firstIdentity = compose.onNodeWithText("46001", substring = true).getUnclippedBoundsInRoot()
        val secondIdentity = compose.onNodeWithText("23430", substring = true).getUnclippedBoundsInRoot()
        assertEquals(firstIdentity.top, secondIdentity.top)
    }
}
