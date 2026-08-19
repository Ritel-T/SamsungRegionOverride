package com.riteldevelopment.carriertestoverride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riteldevelopment.carriertestoverride.ui.OverrideActions
import com.riteldevelopment.carriertestoverride.ui.OverrideScreen
import com.riteldevelopment.carriertestoverride.ui.OverrideViewModel
import com.riteldevelopment.carriertestoverride.ui.theme.CarrierOverrideTheme

class MainActivity : ComponentActivity() {

    private val viewModel: OverrideViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Called before super.onCreate so the decor is laid out edge to edge from the first frame.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CarrierOverrideTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
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

    override fun onResume() {
        super.onResume()
        // Shizuku may have been started or revoked, a SIM swapped, or a target app installed or removed
        // while this screen was in the background. Relaunching the target apps sends the user away and
        // back, so this path runs after every refresh too. All three reads are cheap.
        viewModel.refreshShizuku()
        viewModel.refreshSims()
        viewModel.refreshTargetApps()
    }
}
