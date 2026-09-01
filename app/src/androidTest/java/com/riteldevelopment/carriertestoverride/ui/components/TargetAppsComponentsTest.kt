package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.data.TargetApp
import com.riteldevelopment.carriertestoverride.data.WipeMode
import com.riteldevelopment.carriertestoverride.ui.DialogRequest
import com.riteldevelopment.carriertestoverride.ui.theme.CarrierOverrideTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TargetAppsComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun enableAccessibilityValidation() {
        compose.enableAccessibilityChecks()
    }

    @Test
    fun targetAppsStayQuietUntilExpanded() {
        val app = TargetApp("com.example.store", "Galaxy Store", installed = true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val heading = context.getString(R.string.target_apps_heading)
        val stopAndOpen = context.getString(R.string.target_app_stop_open)

        compose.setContent {
            CarrierOverrideTheme(darkTheme = true, dynamicColor = false) {
                TargetAppsPanel(
                    apps = listOf(app),
                    wipeMode = WipeMode.NONE,
                    relaunch = true,
                    enabled = true,
                    onWipeModeChange = {},
                    onRelaunchChange = {},
                    onRun = {},
                    onChoose = {},
                )
            }
        }

        compose.onNodeWithText("Galaxy Store").assertDoesNotExist()
        compose.onNodeWithText(heading).performClick()
        compose.onNodeWithText("Galaxy Store").assertIsDisplayed()
        compose.onNodeWithText(stopAndOpen).assertIsDisplayed()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun targetAppsHeaderOffersExpandThenCollapse() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val heading = context.getString(R.string.target_apps_heading)

        compose.setContent {
            CarrierOverrideTheme(darkTheme = true, dynamicColor = false) {
                TargetAppsPanel(
                    apps = emptyList(),
                    wipeMode = WipeMode.NONE,
                    relaunch = false,
                    enabled = true,
                    onWipeModeChange = {},
                    onRelaunchChange = {},
                    onRun = {},
                    onChoose = {},
                )
            }
        }

        compose.onNodeWithText(heading)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Collapse))
        compose.onNodeWithText(heading).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(heading)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Expand))
    }

    @Test
    fun targetAppPickerExposesOneTogglePerRow() {
        val store = TargetApp("com.example.store", "Galaxy Store", installed = true)
        val maps = TargetApp("com.example.maps", "Maps", installed = true)
        var request by mutableStateOf(
            DialogRequest.ChooseTargetApps(
                available = listOf(store, maps),
                selected = emptySet(),
                loading = false,
            )
        )

        compose.setContent {
            CarrierOverrideTheme(darkTheme = true, dynamicColor = false) {
                TargetAppPickerDialog(
                    request = request,
                    showReset = false,
                    onToggle = { packageName ->
                        request = request.copy(
                            selected = if (packageName in request.selected) {
                                request.selected - packageName
                            } else {
                                request.selected + packageName
                            }
                        )
                    },
                    onConfirm = {},
                    onReset = {},
                    onDismiss = {},
                )
            }
        }

        compose.onAllNodes(isToggleable()).assertCountEquals(2)
        compose.onNode(hasText("Galaxy Store") and isToggleable()).assertIsOff().performClick().assertIsOn()
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
