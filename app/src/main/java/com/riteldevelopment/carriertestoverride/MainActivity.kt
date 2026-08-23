package com.riteldevelopment.carriertestoverride

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riteldevelopment.carriertestoverride.ui.OverrideActions
import com.riteldevelopment.carriertestoverride.ui.OverrideScreen
import com.riteldevelopment.carriertestoverride.ui.OverrideViewModel
import com.riteldevelopment.carriertestoverride.ui.theme.CarrierOverrideTheme

class MainActivity : ComponentActivity() {

    private val viewModel: OverrideViewModel by viewModels()

    /**
     * Registered as a field because `registerForActivityResult` has to run before the activity is
     * started. The result is not read: the next SIM scan asks the system what it is actually allowed to
     * do, which is the answer that matters, and the app asks only once whichever way this goes.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Called before super.onCreate so the decor is laid out edge to edge from the first frame.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            CarrierOverrideTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                // Driven by state rather than by a lifecycle callback: the first time this matters is
                // usually an apply made with the screen already open, and no resume follows that.
                LaunchedEffect(state.notificationPromptDue) {
                    if (!state.notificationPromptDue) return@LaunchedEffect
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
                    viewModel.markNotificationPromptShown()
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                OverrideScreen(
                    state = state,
                    actions = OverrideActions(
                        onSelectSim = viewModel::selectSim,
                        onSelectPreset = viewModel::selectPreset,
                        onMccMncChange = viewModel::setMccMnc,
                        onCountryIsoChange = viewModel::setCountryIso,
                        onCarrierNameChange = viewModel::setCarrierName,
                        onSimIdentityLayerChange = viewModel::setSimIdentityLayer,
                        onAppCountryLayerChange = viewModel::setAppCountryLayer,
                        onCarrierNameOverrideChange = viewModel::setCarrierNameOverride,
                        onWipeModeChange = viewModel::setWipeMode,
                        onRelaunchChange = viewModel::setRelaunchApps,
                        onApply = viewModel::requestApply,
                        onRestore = viewModel::requestRestore,
                        onClearAll = viewModel::requestClearAll,
                        onRefreshApps = viewModel::requestRefreshApps,
                        onRescan = viewModel::refreshSims,
                        onCancel = viewModel::cancelOperation,
                        onOpenShizuku = viewModel::openShizuku,
                        onOpenLanguageSettings = viewModel::openLanguageSettings,
                        onChooseTargetApps = viewModel::requestChooseTargetApps,
                        onToggleTargetApp = viewModel::toggleTargetApp,
                        onConfirmTargetApps = viewModel::confirmTargetApps,
                        onResetTargetApps = viewModel::resetTargetApps,
                        onDismissDialog = viewModel::dismissDialog,
                        onConfirmApply = viewModel::confirmApply,
                        onConfirmRestoreWithoutMarkers = viewModel::confirmRestoreWithoutMarkers,
                        onConfirmClearAll = viewModel::confirmClearAll,
                        onConfirmWipeData = viewModel::confirmWipeData,
                    ),
                )
            }
        }
    }

    /** The activity is `singleTop`, so a second tap on the notification lands here, not in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Shizuku may have been started or revoked, a SIM swapped, or a target app installed or removed
        // while this screen was in the background. Relaunching the target apps sends the user away and
        // back, so this path runs after every refresh too. All three reads are cheap.
        viewModel.refreshShizuku()
        viewModel.refreshSims()
        viewModel.refreshTargetApps()
    }

    /**
     * The Restore button on the ongoing notification.
     *
     * The intent is consumed by clearing its action, because the activity keeps it: without that, coming
     * back to the app from Recents after a restore would re-deliver the same intent and run the whole
     * privileged operation a second time on a SIM that no longer has an override.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ACTION_RESTORE) return
        val subId = intent.getIntExtra(EXTRA_SUB_ID, -1)
        intent.action = Intent.ACTION_MAIN
        viewModel.restoreFromNotification(subId)
    }

    companion object {
        /** Set on the pending intent behind the notification's Restore action. */
        const val ACTION_RESTORE = "com.riteldevelopment.carriertestoverride.action.RESTORE"

        const val EXTRA_SUB_ID = "com.riteldevelopment.carriertestoverride.extra.SUB_ID"
    }
}
