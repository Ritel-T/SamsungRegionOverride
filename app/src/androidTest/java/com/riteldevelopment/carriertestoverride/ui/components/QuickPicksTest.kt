package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riteldevelopment.carriertestoverride.ui.QuickPick
import com.riteldevelopment.carriertestoverride.ui.RegionPreset
import com.riteldevelopment.carriertestoverride.ui.theme.CarrierOverrideTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickPicksTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun enableAccessibilityValidation() {
        compose.enableAccessibilityChecks()
    }

    @Test
    fun quickPicksExposeAndUpdateSingleSelection() {
        val ee = preset("United Kingdom", "gb", "EE", "23430")
        val tMobile = preset("United States", "us", "T-Mobile", "310260")
        var selectedId by mutableStateOf(ee.id)

        compose.setContent {
            CarrierOverrideTheme(darkTheme = true, dynamicColor = false) {
                QuickPickRow(
                    quickPicks = listOf(QuickPick(ee, recent = true), QuickPick(tMobile, recent = false)),
                    selectedId = selectedId,
                    enabled = true,
                    onSelect = { selectedId = it.id },
                )
            }
        }

        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        compose.onNode(hasText("EE") and isToggleable() and radio).assertIsOn()
        compose.onNode(hasText("T-Mobile") and isToggleable() and radio).performClick().assertIsOn()
        compose.onNode(hasText("EE") and isToggleable() and radio).assertIsOff()
    }

    private fun preset(country: String, iso: String, carrier: String, mccMnc: String) =
        RegionPreset(country = country, countryIso = iso, carrier = carrier, mccMnc = mccMnc)
}
