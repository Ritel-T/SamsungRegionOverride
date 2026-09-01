package com.riteldevelopment.carriertestoverride.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.data.OverrideRepository
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.data.TargetApp
import com.riteldevelopment.carriertestoverride.data.WipeMode
import com.riteldevelopment.carriertestoverride.ui.components.BlockGap
import com.riteldevelopment.carriertestoverride.ui.components.DisclosureChevron
import com.riteldevelopment.carriertestoverride.ui.components.HazardNote
import com.riteldevelopment.carriertestoverride.ui.components.LayerSection
import com.riteldevelopment.carriertestoverride.ui.components.MicroLabel
import com.riteldevelopment.carriertestoverride.ui.components.OverrideDialogs
import com.riteldevelopment.carriertestoverride.ui.components.PresetField
import com.riteldevelopment.carriertestoverride.ui.components.QuickPickRow
import com.riteldevelopment.carriertestoverride.ui.components.RealVersusDisguise
import com.riteldevelopment.carriertestoverride.ui.components.ResultPanel
import com.riteldevelopment.carriertestoverride.ui.components.ShizukuStatusRow
import com.riteldevelopment.carriertestoverride.ui.components.SimSelector
import com.riteldevelopment.carriertestoverride.ui.components.TargetAppsPanel
import com.riteldevelopment.carriertestoverride.ui.components.rememberAppIcon

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
    val onOpenShizuku: () -> Unit,
    val onOpenLanguageSettings: () -> Unit,
    val onChooseTargetApps: () -> Unit,
    val onToggleTargetApp: (String) -> Unit,
    val onConfirmTargetApps: (DialogRequest.ChooseTargetApps) -> Unit,
    val onResetTargetApps: () -> Unit,
    val onDismissDialog: () -> Unit,
    val onConfirmApply: (DialogRequest.ConfirmApply) -> Unit,
    val onConfirmRestoreWithoutMarkers: (SimInfo) -> Unit,
    val onConfirmClearAll: (SimInfo) -> Unit,
    val onConfirmWipeData: (List<TargetApp>) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OverrideScreen(
    state: OverrideUiState,
    actions: OverrideActions,
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    // The title starts large and collapses as the form scrolls under it. This screen is a long single
    // column whose top block is the one the user returns to — which SIM, and who it is pretending to
    // be — so the bar giving that space back on the way down and taking it again on the way up is
    // worth more here than a fixed strip would be.
    val compactHeight = !windowSizeClass.isHeightAtLeastBreakpoint(
        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
    )
    val wide = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
    )
    val listState = rememberLazyListState()
    val scrollBehavior = if (compactHeight) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        // Guarded on the list rather than left at the default `{ true }`. This screen's failure states
        // are short — a Shizuku refusal or a failed SIM scan is three blocks — and on content that does
        // not scroll, a fling would still collapse the title with nothing able to scroll it back.
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            canScroll = { listState.canScrollForward || listState.canScrollBackward },
        )
    }
    val toolbarScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom,
    )
    // The bar stops reacting to scroll for as long as an operation is in flight, because it is also the
    // only place that operation narrates itself: letting it leave would take the stage text, the
    // progress bar and Cancel off the bottom of the screen for a user who scrolled down to re-read a
    // warning. An already-departed bar comes back rather than snapping, since a rescan can be started
    // from the overflow menu while the bar is out of sight.
    //
    // Pinning is the detached nested-scroll connection below and nothing else. The behaviour object
    // itself stays passed to the toolbar throughout, because that is what supplies the offset modifier:
    // withdrawing it does not mean "stop listening to scroll", it means "place the bar at rest in this
    // frame" — precisely the snap this exists to avoid. The offset is walked back to zero instead.
    val busy = state.busy != null
    val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(busy) {
        if (busy && toolbarScrollBehavior.state.offset != 0f) {
            animate(
                initialValue = toolbarScrollBehavior.state.offset,
                targetValue = 0f,
                animationSpec = settle,
            ) { value, _ -> toolbarScrollBehavior.state.offset = value }
        }
    }
    Scaffold(
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            // Detached rather than merely ignored, so the offset cannot drift while the bar is pinned
            // and then snap it away the instant the operation finishes.
            .then(if (busy) Modifier else Modifier.nestedScroll(toolbarScrollBehavior)),
        topBar = {
            val title = @Composable { Text(stringResource(R.string.screen_title)) }
            val topActions: @Composable RowScope.() -> Unit = {
                ShizukuButton(onClick = actions.onOpenShizuku)
                OverflowMenu(state = state, actions = actions)
            }
            if (compactHeight) {
                TopAppBar(title = title, actions = topActions, scrollBehavior = scrollBehavior)
            } else {
                MediumFlexibleTopAppBar(
                    title = title,
                    actions = topActions,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = {
            ActionBar(state = state, actions = actions, scrollBehavior = toolbarScrollBehavior)
        },
    ) { padding ->
        OverrideBody(
            padding = padding,
            state = state,
            actions = actions,
            wide = wide,
            listState = listState,
        )
    }

    OverrideDialogs(
        dialog = state.dialog,
        targetAppsAreDefault = state.targetAppsAreDefault,
        onDismiss = actions.onDismissDialog,
        onConfirmApply = actions.onConfirmApply,
        onConfirmRestore = actions.onConfirmRestoreWithoutMarkers,
        onConfirmClearAll = actions.onConfirmClearAll,
        onConfirmWipeData = actions.onConfirmWipeData,
        onToggleTargetApp = actions.onToggleTargetApp,
        onConfirmTargetApps = actions.onConfirmTargetApps,
        onResetTargetApps = actions.onResetTargetApps,
    )
}

private val WideContentMaxWidth = 1200.dp

@Composable
private fun OverrideBody(
    padding: PaddingValues,
    state: OverrideUiState,
    actions: OverrideActions,
    wide: Boolean,
    listState: LazyListState,
) {
    // Horizontal insets are real in landscape on a cutout device; dropping them would let the
    // notch sit on top of the identity digits this screen exists to let the user compare.
    val direction = LocalLayoutDirection.current
    val contentPadding = PaddingValues(
        start = 16.dp + padding.calculateStartPadding(direction),
        end = 16.dp + padding.calculateEndPadding(direction),
        top = padding.calculateTopPadding() + 8.dp,
        bottom = padding.calculateBottomPadding() + 24.dp,
    )

    if (wide) {
        WideBody(
            contentPadding = contentPadding,
            state = state,
            actions = actions,
            listState = listState,
        )
    } else {
        CompactBody(
            contentPadding = contentPadding,
            state = state,
            actions = actions,
            listState = listState,
        )
    }
}

@Composable
private fun CompactBody(
    contentPadding: PaddingValues,
    state: OverrideUiState,
    actions: OverrideActions,
    listState: LazyListState,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(BlockGap),
    ) {
        // Every item animates its placement, not just the one that comes and goes. The pending-state
        // warning is inserted in the middle of the column when a snapshot is unverified, and for that
        // insertion to read as the list making room, the blocks below it have to slide rather than jump
        // to their new offsets. Keys are already stable, which is what makes this work at all.
        item("shizuku") {
            ShizukuStatusRow(status = state.shizuku, modifier = Modifier.animateItem())
        }
        item("sims") {
            SimBlock(state = state, actions = actions, modifier = Modifier.animateItem())
        }
        item("diff") {
            IdentityBlock(state = state, modifier = Modifier.animateItem())
        }
        if (state.selectedSim?.flags?.uncertain == true) {
            item("pending-state") {
                HazardNote(
                    text = stringResource(R.string.pending_state_warning),
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item("target") {
            TargetBlock(state = state, actions = actions, modifier = Modifier.animateItem())
        }
        item("layer-country") {
            CountryLayer(state = state, actions = actions, modifier = Modifier.animateItem())
        }
        item("layer-sim") {
            NetworkLayer(state = state, actions = actions, modifier = Modifier.animateItem())
        }
        item("apps") {
            TargetAppsBlock(state = state, actions = actions, modifier = Modifier.animateItem())
        }
        item("result") {
            ResultPanel(
                result = state.result,
                busy = state.busy,
                modifier = Modifier.animateItem(),
            )
        }
        item("risk") {
            HazardNote(
                text = stringResource(R.string.risk_notice),
                modifier = Modifier.animateItem(),
            )
        }
        item("device") {
            DeviceLine(state.device, modifier = Modifier.animateItem())
        }
    }
}

@Composable
private fun WideBody(
    contentPadding: PaddingValues,
    state: OverrideUiState,
    actions: OverrideActions,
    listState: LazyListState,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BlockGap),
    ) {
        item("wide-shizuku") {
            WideContainer(modifier = Modifier.animateItem()) { ShizukuStatusRow(status = state.shizuku) }
        }
        item("wide-sims") {
            WideContainer(modifier = Modifier.animateItem()) { SimBlock(state = state, actions = actions) }
        }
        item("wide-identity-target") {
            WideContainer(modifier = Modifier.animateItem()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BlockGap),
                    verticalAlignment = Alignment.Top,
                ) {
                    IdentityBlock(state = state, modifier = Modifier.weight(1f))
                    TargetBlock(state = state, actions = actions, modifier = Modifier.weight(1f))
                }
            }
        }
        if (state.selectedSim?.flags?.uncertain == true) {
            item("wide-pending-state") {
                WideContainer(modifier = Modifier.animateItem()) {
                    HazardNote(text = stringResource(R.string.pending_state_warning))
                }
            }
        }
        item("wide-layers") {
            WideContainer(modifier = Modifier.animateItem()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BlockGap),
                    verticalAlignment = Alignment.Top,
                ) {
                    CountryLayer(
                        state = state,
                        actions = actions,
                        modifier = Modifier.weight(1f),
                    )
                    NetworkLayer(
                        state = state,
                        actions = actions,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item("wide-secondary") {
            WideContainer(modifier = Modifier.animateItem()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BlockGap),
                    verticalAlignment = Alignment.Top,
                ) {
                    TargetAppsBlock(
                        state = state,
                        actions = actions,
                        modifier = Modifier.weight(1f),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(BlockGap),
                    ) {
                        ResultPanel(result = state.result, busy = state.busy)
                        HazardNote(text = stringResource(R.string.risk_notice))
                        DeviceLine(state.device)
                    }
                }
            }
        }
    }
}

@Composable
private fun WideContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .widthIn(max = WideContentMaxWidth)
            .fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun SimBlock(
    state: OverrideUiState,
    actions: OverrideActions,
    modifier: Modifier = Modifier,
) {
    SimSelector(
        sims = state.sims,
        slotCount = state.slotCount,
        selectedSubId = state.selectedSubId,
        scanError = state.simScanError,
        enabled = !state.isBusy,
        onSelect = actions.onSelectSim,
        modifier = modifier,
    )
}

@Composable
private fun IdentityBlock(state: OverrideUiState, modifier: Modifier = Modifier) {
    RealVersusDisguise(
        sim = state.selectedSim,
        targetMccMnc = state.mccMnc,
        targetCountryIso = state.countryIso,
        targetCarrierName = state.carrierName,
        countryLayerArmed = state.layers.appCountry,
        networkLayerArmed = state.layers.simIdentity,
        modifier = modifier,
    )
}

@Composable
private fun TargetAppsBlock(
    state: OverrideUiState,
    actions: OverrideActions,
    modifier: Modifier = Modifier,
) {
    TargetAppsPanel(
        apps = state.targetApps,
        wipeMode = state.wipeMode,
        relaunch = state.relaunchApps,
        enabled = !state.isBusy,
        onWipeModeChange = actions.onWipeModeChange,
        onRelaunchChange = actions.onRelaunchChange,
        onRun = actions.onRefreshApps,
        onChoose = actions.onChooseTargetApps,
        modifier = modifier,
    )
}

/**
 * The one place this screen sends people when it cannot proceed.
 *
 * Shizuku's own launcher icon and nothing else. It sat next to a leave-the-app glyph for one revision,
 * on the reasoning that an app icon states an identity but not a verb; in the bar it just read as two
 * marks for one control. An app icon in an app bar is already a recognised idiom for "go to that app",
 * and the status line directly below says whether Shizuku is running, so the verb was never carrying
 * the weight the extra glyph cost.
 *
 * The glyph survives as the fallback for a phone with no Shizuku installed, where there is no icon to
 * draw. The action still leads somewhere useful there — the view model answers a missing package with
 * an install hint rather than silence — so the control must not vanish, or that hint is unreachable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShizukuButton(onClick: () -> Unit) {
    val icon = rememberAppIcon(KnownPackages.SHIZUKU)
    val label = stringResource(R.string.open_shizuku)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Below
        ),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = label,
                )
            }
        }
    }
}

/**
 * The destructive action lives here rather than as a third button: it is rare, and it wipes persistent
 * CarrierConfig overrides written by *other* tools, so it should not sit a thumb-width from Apply.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OverflowMenu(state: OverrideUiState, actions: OverrideActions) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            onClick = {
                expanded = false
                actions.onRescan()
            },
            text = { Text(stringResource(R.string.menu_rescan_sims)) },
            shape = MenuDefaults.leadingItemShape,
            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            enabled = !state.isBusy,
            colors = MenuDefaults.itemColors(),
        )
        DropdownMenuItem(
            onClick = {
                expanded = false
                actions.onOpenLanguageSettings()
            },
            text = { Text(stringResource(R.string.menu_app_language)) },
            shape = MenuDefaults.middleItemShape,
            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            enabled = !state.isBusy,
            colors = MenuDefaults.itemColors(),
        )
        DropdownMenuItem(
            onClick = {
                expanded = false
                actions.onClearAll()
            },
            text = {
                Text(
                    text = stringResource(R.string.menu_clear_all),
                    color = if (state.canClearAll) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            },
            shape = MenuDefaults.trailingItemShape,
            leadingIcon = {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = if (state.canClearAll) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            },
            enabled = state.canClearAll,
            colors = MenuDefaults.itemColors(),
        )
    }
}

/**
 * The preset picker and the carrier name.
 *
 * The name lives here rather than inside a layer because *both* layers consume it — the network layer
 * writes it as PNN/SPN, and the country layer can write it as the subscription's display name.
 * Everything that belongs to exactly one layer lives inside that layer instead.
 */
@Composable
private fun TargetBlock(
    state: OverrideUiState,
    actions: OverrideActions,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    var customExpanded by rememberSaveable { mutableStateOf(state.preset == null) }
    Column(modifier = modifier) {
        MicroLabel(stringResource(R.string.label_pretend_to_be))
        Spacer(Modifier.height(8.dp))
        PresetField(
            preset = state.preset,
            enabled = !state.isBusy,
            onSelect = {
                customExpanded = false
                actions.onSelectPreset(it)
            },
        )
        // Under the field they fill, so tapping one shows its effect in the line directly above.
        Spacer(Modifier.height(8.dp))
        QuickPickRow(
            quickPicks = state.quickPicks,
            selectedId = state.presetId,
            enabled = !state.isBusy,
            onSelect = {
                customExpanded = false
                actions.onSelectPreset(it)
            },
        )
        Spacer(Modifier.height(10.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                ListItem(
                    onClick = { customExpanded = !customExpanded },
                    trailingContent = {
                        DisclosureChevron(
                            expanded = customExpanded,
                            onToggle = { customExpanded = !customExpanded },
                        )
                    },
                    supportingContent = {
                        Text(
                            listOf(
                                stringResource(R.string.mcc_mnc),
                                stringResource(R.string.country_iso),
                                stringResource(R.string.carrier_name),
                            ).joinToString(" · ")
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                ) {
                    Text(
                        text = stringResource(R.string.preset_custom),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
                AnimatedVisibility(
                    visible = customExpanded,
                    enter = fadeIn(animationSpec = motion.fastEffectsSpec()) +
                        expandVertically(animationSpec = motion.defaultSpatialSpec()),
                    exit = fadeOut(animationSpec = motion.fastEffectsSpec()) +
                        shrinkVertically(animationSpec = motion.defaultSpatialSpec()),
                ) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.countryIso,
                            onValueChange = actions.onCountryIsoChange,
                            enabled = !state.isBusy,
                            singleLine = true,
                            label = { Text(stringResource(R.string.country_iso)) },
                            supportingText = { Text(stringResource(R.string.country_iso_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.mccMnc,
                            onValueChange = actions.onMccMncChange,
                            enabled = !state.isBusy,
                            singleLine = true,
                            label = { Text(stringResource(R.string.mcc_mnc)) },
                            supportingText = { Text(stringResource(R.string.mcc_mnc_hint)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.carrierName,
                            onValueChange = actions.onCarrierNameChange,
                            enabled = !state.isBusy,
                            singleLine = true,
                            label = { Text(stringResource(R.string.carrier_name)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The everyday switch: what country the phone reports to apps.
 *
 * Named for the signal rather than the mechanism. "App country" described where the value is written;
 * "Country" describes what the user is setting, and the icons beside it say who will believe it.
 */
@Composable
private fun CountryLayer(
    state: OverrideUiState,
    actions: OverrideActions,
    modifier: Modifier = Modifier,
) {
    val sim = state.selectedSim
    val motion = MaterialTheme.motionScheme
    LayerSection(
        title = stringResource(R.string.country_layer_title),
        subtitle = listOf(
            stringResource(R.string.country_layer_subtitle),
            state.countryIso.uppercase(),
        ).filter { it.isNotBlank() }.joinToString(" · "),
        enabled = state.layers.appCountry,
        applied = sim?.countryLayerLive == true,
        accent = MaterialTheme.colorScheme.secondary,
        controlsEnabled = !state.isBusy,
        onEnabledChange = actions.onAppCountryLayerChange,
        modifier = modifier,
        readerPackages = KnownPackages.COUNTRY_READERS,
        liveButDisarmedText = stringResource(R.string.layer_still_live),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = state.layers.carrierNameOverride,
                    enabled = !state.isBusy,
                    role = Role.Checkbox,
                    onValueChange = actions.onCarrierNameOverrideChange,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.layers.carrierNameOverride,
                onCheckedChange = null,
                enabled = !state.isBusy,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.rename_sim_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // The old label said "Also override the display name", which named a field rather than
                // an effect and left people unable to guess what ticking it would do.
                Text(
                    text = stringResource(R.string.rename_sim_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // This changes with the Network switch while the Country section is already expanded. Animate
        // both visibility and height so the card does not jump when the warning enters or leaves.
        AnimatedVisibility(
            visible = state.layers.simIdentity || sim?.simLayerLive == true,
            enter = fadeIn(animationSpec = motion.fastEffectsSpec()) +
                expandVertically(animationSpec = motion.defaultSpatialSpec()),
            exit = fadeOut(animationSpec = motion.fastEffectsSpec()) +
                shrinkVertically(animationSpec = motion.defaultSpatialSpec()),
        ) {
            Column {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.country_network_trigger_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** The specialist switch: the network code the SIM claims to be on. */
@Composable
private fun NetworkLayer(
    state: OverrideUiState,
    actions: OverrideActions,
    modifier: Modifier = Modifier,
) {
    val sim = state.selectedSim
    val motion = MaterialTheme.motionScheme
    LayerSection(
        title = stringResource(R.string.network_layer_title),
        subtitle = listOf(
            stringResource(R.string.network_layer_subtitle),
            state.mccMnc,
        ).filter { it.isNotBlank() }.joinToString(" · "),
        enabled = state.layers.simIdentity,
        applied = sim?.simLayerLive == true,
        accent = MaterialTheme.colorScheme.primary,
        controlsEnabled = !state.isBusy,
        onEnabledChange = actions.onSimIdentityLayerChange,
        modifier = modifier,
        readerPackages = KnownPackages.NETWORK_READERS,
        liveButDisarmedText = stringResource(R.string.layer_still_live),
    ) {
        // The card is open while this changes — selecting the other SIM, or a PIN being entered
        // elsewhere and the state settling to READY — so the line arrives and leaves under animation
        // rather than resizing the section in one frame.
        val notReady = sim != null && !sim.isReady
        var lastNotReady by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        if (notReady) lastNotReady = sim.slotIndex to sim.simState
        AnimatedVisibility(
            visible = notReady,
            enter = fadeIn(animationSpec = motion.fastEffectsSpec()) +
                expandVertically(animationSpec = motion.defaultSpatialSpec()),
            exit = fadeOut(animationSpec = motion.fastEffectsSpec()) +
                shrinkVertically(animationSpec = motion.defaultSpatialSpec()),
        ) {
            lastNotReady?.let { (slotIndex, simState) ->
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.network_not_ready,
                            stringResource(R.string.sim_number, slotIndex + 1),
                            stringResource(SimInfo.simStateNameRes(simState)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.network_reconnect_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * The primary action, and the place the busy narration replaces it in.
 *
 * A floating toolbar rather than a flat bar welded to the bottom edge: the form above it is long, and a
 * bar that lifts off the content reads as a control rather than as the end of the page. It gives its
 * space back on the way down and takes it again on the way up — but only while nothing is running,
 * because the stage text, the progress bar and Cancel all live in here and there is nowhere else on
 * screen that says an operation is in progress at all.
 *
 * [scrollBehavior] is not the switch for that: it is always supplied, and always the same instance the
 * caller pins by detaching from nested scroll. It stays here because it also carries the modifier that
 * *places* the bar, so a bar that has scrolled away is only able to glide back while it is attached.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionBar(
    state: OverrideUiState,
    actions: OverrideActions,
    scrollBehavior: FloatingToolbarScrollBehavior,
) {
    val motion = MaterialTheme.motionScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.fillMaxWidth(),
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            scrollBehavior = scrollBehavior,
        ) {
            // Crossfaded on whether an operation is running, not on the operation's own state.
            // `Crossfade` keys its content on the value it is given, so handing it the BusyState
            // itself would tear the narration down and rebuild it at every stage change — five times
            // per run. The step counter would jump-cut, the progress bar's animation would restart at
            // its new target instead of travelling to it, and the loading indicator would begin its
            // morph again from the top, all while cross-dissolving with a near-identical copy.
            //
            // The stage is therefore read *inside* the running branch, where it recomposes the bar in
            // place. It is held past the end of the run so the copy fading out keeps saying what it
            // said, rather than emptying while still on screen.
            val busy = state.busy
            var lastBusy by remember { mutableStateOf<BusyState?>(null) }
            if (busy != null) lastBusy = busy
            Crossfade(
                targetState = busy == null,
                animationSpec = motion.fastEffectsSpec(),
                label = "actionBar",
            ) { idle ->
                if (idle) {
                    val live = state.selectedSim?.disguised == true
                    // A ButtonGroup rather than a Row: pressing one button widens it and lets the other
                    // yield, so the pair reacts as a unit. The primary action swaps sides once a disguise is
                    // live, and that swap is exactly the moment the extra feedback earns its place — the
                    // button under the thumb should visibly answer when the label beneath it has changed.
                    //
                    // Both items are weighted, which is what makes the empty overflow indicator safe:
                    // Apply and Restore are the screen's two reasons to exist and neither may end up
                    // behind a menu, so they share the row and a long label ellipsizes in place.
                    ButtonGroup(
                        overflowIndicator = {},
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        customItem(
                            buttonGroupContent = {
                                val interaction = remember { MutableInteractionSource() }
                                val modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(interaction)
                                val label = stringResource(
                                    if (live) R.string.action_update_disguise
                                    else R.string.action_start_disguise
                                )
                                if (live) {
                                    OutlinedButton(
                                        onClick = actions.onApply,
                                        shapes = ButtonDefaults.shapes(),
                                        enabled = state.canApply,
                                        interactionSource = interaction,
                                        modifier = modifier,
                                    ) { Text(label) }
                                } else {
                                    Button(
                                        onClick = actions.onApply,
                                        shapes = ButtonDefaults.shapes(),
                                        enabled = state.canApply,
                                        interactionSource = interaction,
                                        modifier = modifier,
                                    ) { Text(label) }
                                }
                            },
                            menuContent = {},
                        )
                        customItem(
                            buttonGroupContent = {
                                val interaction = remember { MutableInteractionSource() }
                                val modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(interaction)
                                val label = stringResource(
                                    if (live) R.string.action_end_restore
                                    else R.string.action_restore_saved
                                )
                                if (live) {
                                    Button(
                                        onClick = actions.onRestore,
                                        shapes = ButtonDefaults.shapes(),
                                        enabled = state.canRestore,
                                        interactionSource = interaction,
                                        modifier = modifier,
                                    ) { Text(label) }
                                } else {
                                    OutlinedButton(
                                        onClick = actions.onRestore,
                                        shapes = ButtonDefaults.shapes(),
                                        enabled = state.canRestore,
                                        interactionSource = interaction,
                                        modifier = modifier,
                                    ) { Text(label) }
                                }
                            },
                            menuContent = {},
                        )
                    }
                } else {
                    lastBusy?.let { BusyBar(busy = it, onCancel = actions.onCancel) }
                }
            }
        }
    }
}

/**
 * Named stages rather than a nameless spinner: waiting on Shizuku and waiting on the user's permission
 * grant are indistinguishable from a hang otherwise, and both can last indefinitely.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BusyBar(busy: BusyState, onCancel: () -> Unit) {
    val stages = OverrideRepository.Stage.entries
    val step = stages.indexOf(busy.stage) + 1
    val motion = MaterialTheme.motionScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The morphing indicator, not a spinner: it reads as "working" at a glance and pairs with
            // the stage text rather than competing with it.
            //
            // Everything here takes the toolbar's own content colour rather than a surface role. This
            // sits inside a vibrant floating toolbar, whose container is primaryContainer — an
            // onSurfaceVariant grey is mixed against the wrong background there, and how wrong depends
            // on whichever accent the user's wallpaper produced.
            LoadingIndicator(
                modifier = Modifier.size(28.dp),
                color = LocalContentColor.current,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(
                    R.string.busy_step,
                    step,
                    stages.size,
                    stringResource(busy.labelRes),
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (busy.cancellable) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Determinate, and honestly so: the stage list is fixed and the current stage is known, so the
        // bar states step N of M rather than miming activity. That is also why the wave is the right
        // treatment here and was the wrong one on the notification, where nothing was in progress —
        // the amplitude drops to flat as the run completes, so the motion stops when the work does.
        val progress by animateFloatAsState(
            targetValue = step.toFloat() / stages.size,
            animationSpec = motion.defaultSpatialSpec(),
            label = "busyProgress",
        )
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = LocalContentColor.current,
            trackColor = LocalContentColor.current.copy(alpha = 0.24f),
        )
    }
}

@Composable
private fun DeviceLine(device: DeviceInfo, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = stringResource(
            R.string.device_line,
            device.manufacturer,
            device.model,
            device.apiLevel,
            device.appVersion,
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
