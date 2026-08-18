package com.riteldevelopment.carriertestoverride.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.riteldevelopment.carriertestoverride.data.OverrideRepository
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.data.TargetApp
import com.riteldevelopment.carriertestoverride.data.WipeMode
import com.riteldevelopment.carriertestoverride.ui.components.BlockGap
import com.riteldevelopment.carriertestoverride.ui.components.HazardNote
import com.riteldevelopment.carriertestoverride.ui.components.IdentityDiff
import com.riteldevelopment.carriertestoverride.ui.components.LayerSection
import com.riteldevelopment.carriertestoverride.ui.components.MicroLabel
import com.riteldevelopment.carriertestoverride.ui.components.OverrideDialogs
import com.riteldevelopment.carriertestoverride.ui.components.PresetField
import com.riteldevelopment.carriertestoverride.ui.components.ResultPanel
import com.riteldevelopment.carriertestoverride.ui.components.ShizukuStatusRow
import com.riteldevelopment.carriertestoverride.ui.components.SimSelector
import com.riteldevelopment.carriertestoverride.ui.components.TargetAppsPanel

/** Everything the screen can do, hoisted so the screen itself stays previewable and stateless. */
data class OverrideActions(
    val onSelectSim: (Int) -> Unit,
    val onSelectPreset: (RegionPreset) -> Unit,
    val onMccMncChange: (String) -> Unit,
    val onCountryIsoChange: (String) -> Unit,
    val onCarrierNameChange: (String) -> Unit,
    val onSimIdentityLayerChange: (Boolean) -> Unit,
    val onAppCountryLayerChange: (Boolean) -> Unit,
    val onCarrierNameOverrideChange: (Boolean) -> Unit,
    val onWipeModeChange: (WipeMode) -> Unit,
    val onRelaunchChange: (Boolean) -> Unit,
    val onApply: () -> Unit,
    val onRestore: () -> Unit,
    val onClearAll: () -> Unit,
    val onRefreshApps: (TargetApp) -> Unit,
    val onRescan: () -> Unit,
    val onCancel: () -> Unit,
    val onDismissDialog: () -> Unit,
    val onConfirmApply: (DialogRequest.ConfirmApply) -> Unit,
    val onConfirmRestoreWithoutMarkers: (SimInfo) -> Unit,
    val onConfirmClearAll: (SimInfo) -> Unit,
    val onConfirmWipeData: (List<TargetApp>) -> Unit,
)

/**
 * The standing hazard note.
 *
 * It names App country rather than SIM identity because that is what the measurements show: with only
 * the SIM identity layer applied IMS stays registered and calls work, while App country deregisters it
 * for as long as it is live. The earlier wording blamed SIM identity and called the disturbance
 * "brief", which pointed a worried user at the wrong switch.
 */
private const val RISK_TEXT =
    "App country deregisters IMS on this SIM: data keeps working, calls and SMS stop until you " +
        "restore. Restore clears every transient CarrierConfig test value on the subId, not only " +
        "this tool's. A reboot is the definitive undo."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverrideScreen(
    state: OverrideUiState,
    actions: OverrideActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Region Override") },
                actions = { OverflowMenu(state = state, actions = actions) },
            )
        },
        bottomBar = { ActionBar(state = state, actions = actions) },
    ) { padding ->
        // Horizontal insets are real in landscape on a cutout device; dropping them would let the
        // notch sit on top of the identity digits this screen exists to let the user compare.
        val direction = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp + padding.calculateStartPadding(direction),
                end = 16.dp + padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(BlockGap),
        ) {
            item("shizuku") {
                ShizukuStatusRow(status = state.shizuku)
            }

            item("sims") {
                SimSelector(
                    sims = state.sims,
                    slotCount = state.slotCount,
                    selectedSubId = state.selectedSubId,
                    scanError = state.simScanError,
                    enabled = !state.isBusy,
                    onSelect = actions.onSelectSim,
                )
            }

            item("diff") {
                IdentityDiff(
                    current = state.selectedSim,
                    targetMccMnc = state.mccMnc,
                    targetCountryIso = state.countryIso,
                    targetCarrierName = state.carrierName,
                )
            }

            item("target") {
                TargetBlock(state = state, actions = actions)
            }

            item("layer-sim") {
                SimIdentityLayer(state = state, actions = actions)
            }

            item("layer-country") {
                AppCountryLayer(state = state, actions = actions)
            }

            item("apps") {
                TargetAppsPanel(
                    apps = state.targetApps,
                    wipeMode = state.wipeMode,
                    relaunch = state.relaunchApps,
                    enabled = !state.isBusy,
                    onWipeModeChange = actions.onWipeModeChange,
                    onRelaunchChange = actions.onRelaunchChange,
                    onRun = actions.onRefreshApps,
                )
            }

            item("result") {
                ResultPanel(result = state.result)
            }

            item("risk") {
                HazardNote(text = RISK_TEXT)
            }

            item("device") {
                DeviceLine(state.device)
            }
        }
    }

    OverrideDialogs(
        dialog = state.dialog,
        onDismiss = actions.onDismissDialog,
        onConfirmApply = actions.onConfirmApply,
        onConfirmRestore = actions.onConfirmRestoreWithoutMarkers,
        onConfirmClearAll = actions.onConfirmClearAll,
        onConfirmWipeData = actions.onConfirmWipeData,
    )
}

/**
 * The destructive action lives here rather than as a third button: it is rare, and it wipes persistent
 * CarrierConfig overrides written by *other* tools, so it should not sit a thumb-width from Apply.
 */
@Composable
private fun OverflowMenu(state: OverrideUiState, actions: OverrideActions) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Rescan SIMs") },
            enabled = !state.isBusy,
            onClick = {
                expanded = false
                actions.onRescan()
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = "Clear all CarrierConfig overrides…",
                    color = MaterialTheme.colorScheme.error,
                )
            },
            enabled = state.canClearAll,
            onClick = {
                expanded = false
                actions.onClearAll()
            },
        )
    }
}

/**
 * The preset picker and the carrier name.
 *
 * The name lives here rather than inside a layer because *both* layers consume it — the SIM identity
 * layer writes it as PNN/SPN, and the app country layer writes it as the display name. Everything that
 * belongs to exactly one layer lives inside that layer instead.
 */
@Composable
private fun TargetBlock(state: OverrideUiState, actions: OverrideActions) {
    Column {
        MicroLabel("TARGET REGION")
        Spacer(Modifier.height(8.dp))
        PresetField(
            preset = state.preset,
            enabled = !state.isBusy,
            onSelect = actions.onSelectPreset,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.carrierName,
            onValueChange = actions.onCarrierNameChange,
            enabled = !state.isBusy,
            singleLine = true,
            label = { Text("Carrier name") },
            supportingText = { Text("Used by both layers") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SimIdentityLayer(state: OverrideUiState, actions: OverrideActions) {
    val sim = state.selectedSim
    LayerSection(
        title = "SIM identity",
        subtitle = "MCC/MNC, test IMSI and SPN/PNN",
        enabled = state.layers.simIdentity,
        applied = sim?.flags?.simIdentity == true,
        accent = MaterialTheme.colorScheme.primary,
        controlsEnabled = !state.isBusy,
        onEnabledChange = actions.onSimIdentityLayerChange,
    ) {
        OutlinedTextField(
            value = state.mccMnc,
            onValueChange = actions.onMccMncChange,
            enabled = !state.isBusy,
            singleLine = true,
            label = { Text("MCC/MNC") },
            supportingText = { Text("5 or 6 digits") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        if (sim != null && !sim.isReady) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This layer needs a READY SIM; ${sim.displayName} is ${sim.stateLabel}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AppCountryLayer(state: OverrideUiState, actions: OverrideActions) {
    val sim = state.selectedSim
    LayerSection(
        title = "App country",
        subtitle = "CarrierConfig SIM country ISO",
        enabled = state.layers.appCountry,
        applied = sim?.flags?.appCountry == true,
        accent = MaterialTheme.colorScheme.secondary,
        controlsEnabled = !state.isBusy,
        onEnabledChange = actions.onAppCountryLayerChange,
    ) {
        OutlinedTextField(
            value = state.countryIso,
            onValueChange = actions.onCountryIsoChange,
            enabled = !state.isBusy,
            singleLine = true,
            label = { Text("Country ISO") },
            supportingText = { Text("Two letters, e.g. gb") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.layers.carrierNameOverride,
                onCheckedChange = actions.onCarrierNameOverrideChange,
                enabled = !state.isBusy,
            )
            Text(
                text = "Also override the display name",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Pinned so the primary action never drifts below the fold, and so the busy narration replaces the
 * controls in place rather than appearing somewhere else on screen.
 */
@Composable
private fun ActionBar(state: OverrideUiState, actions: OverrideActions) {
    Surface(tonalElevation = 3.dp) {
        Crossfade(targetState = state.busy, label = "actionBar") { busy ->
            if (busy == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = actions.onApply,
                        enabled = state.canApply,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Apply")
                    }
                    OutlinedButton(
                        onClick = actions.onRestore,
                        enabled = state.canRestore,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Restore")
                    }
                }
            } else {
                BusyBar(busy = busy, onCancel = actions.onCancel)
            }
        }
    }
}

/**
 * Named stages rather than a nameless spinner: waiting on Shizuku and waiting on the user's permission
 * grant are indistinguishable from a hang otherwise, and both can last indefinitely.
 */
@Composable
private fun BusyBar(busy: BusyState, onCancel: () -> Unit) {
    val stages = OverrideRepository.Stage.entries
    val step = stages.indexOf(busy.stage) + 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$step/${stages.size} · ${busy.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (busy.cancellable) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DeviceLine(device: DeviceInfo) {
    Text(
        text = "${device.manufacturer} ${device.model} · API ${device.apiLevel} · v${device.appVersion}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
