package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.data.OverrideRepository
import com.riteldevelopment.carriertestoverride.ui.BusyState
import com.riteldevelopment.carriertestoverride.ui.LocalizedText
import com.riteldevelopment.carriertestoverride.ui.ResultState
import com.riteldevelopment.carriertestoverride.ui.ResultTone
import com.riteldevelopment.carriertestoverride.ui.theme.CarrierOverrideTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultPanelTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun enableAccessibilityValidation() {
        compose.enableAccessibilityChecks()
    }

    @Test
    fun completedResultDoesNotPretendToBeProgress() {
        compose.setContent {
            CarrierOverrideTheme(darkTheme = true, dynamicColor = false) {
                ResultPanel(
                    ResultState(
                        headline = LocalizedText.Literal("Done"),
                        tone = ResultTone.SUCCESS,
                    )
                )
            }
        }

        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        ).assertCountEquals(0)
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun runningOperationReplacesThePreviousResultWithItsCurrentStage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousHeadline = "Previous result"
        val expectedStage = context.getString(
            R.string.busy_step,
            OverrideRepository.Stage.BINDING.ordinal + 1,
            OverrideRepository.Stage.entries.size,
            context.getString(R.string.busy_binding),
        )
        var busy by mutableStateOf<BusyState?>(null)

        compose.setContent {
            CarrierOverrideTheme(darkTheme = true, dynamicColor = false) {
                ResultPanel(
                    result = ResultState(
                        headline = LocalizedText.Literal(previousHeadline),
                        tone = ResultTone.SUCCESS,
                    ),
                    busy = busy,
                )
            }
        }

        compose.onNodeWithText(previousHeadline).assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        busy = BusyState(OverrideRepository.Stage.BINDING)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(expectedStage).assertIsDisplayed()
        compose.onNodeWithText(previousHeadline).assertDoesNotExist()
        compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        ).assertCountEquals(0)
    }

    @Test
    fun resultPanelReplacesTheIdleLineWithThePanel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nothingRun = context.getString(R.string.result_nothing_run)
        var result by mutableStateOf(ResultState.Initial)

        compose.setContent {
            CarrierOverrideTheme(darkTheme = true, dynamicColor = false) {
                ResultPanel(result = result)
            }
        }

        compose.onNodeWithText(nothingRun).assertIsDisplayed()
        result = ResultState(
            headline = LocalizedText.Literal("Region applied"),
            detail = "SIM operator override written",
            tone = ResultTone.SUCCESS,
        )
        compose.waitForIdle()
        compose.onNodeWithText("Region applied").assertIsDisplayed()
        compose.onNodeWithText(nothingRun).assertDoesNotExist()
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
