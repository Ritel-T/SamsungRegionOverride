package com.riteldevelopment.carriertestoverride.ui

import com.riteldevelopment.carriertestoverride.data.LayerSelection
import com.riteldevelopment.carriertestoverride.data.OverrideRepository
import com.riteldevelopment.carriertestoverride.data.ShizukuStatus
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.data.TargetApp
import com.riteldevelopment.carriertestoverride.data.WipeMode

/** How a result should read: this drives colour and iconography, never the copy itself. */
enum class ResultTone { IDLE, PROGRESS, SUCCESS, PARTIAL, ERROR }

/**
 * The text shown in the result panel. [detail] is the multi-line report produced by the privileged
 * service; [probe] is the runtime capability dump, kept separate so it can be collapsed.
 */
data class ResultState(
    val headline: String,
    val detail: String? = null,
    val probe: String? = null,
    val tone: ResultTone = ResultTone.IDLE,
) {
    companion object {
        val Initial = ResultState("Nothing has run yet.", tone = ResultTone.IDLE)
    }
}

/** A confirmation the user must answer before anything privileged happens. */
sealed interface DialogRequest {
    /** Final review before writing both layers. */
    data class ConfirmApply(
        val sim: SimInfo,
        val target: com.riteldevelopment.carriertestoverride.data.RegionTarget,
        val layers: LayerSelection,
    ) : DialogRequest

    /** This tool has no record of an override on this SIM, but the user asked to restore anyway. */
    data class RestoreWithoutMarkers(val sim: SimInfo) : DialogRequest

    /** Wipes persistent CarrierConfig overrides too, including other tools'. */
    data class ConfirmClearAll(val sim: SimInfo) : DialogRequest

    /** Erasing an app's data signs the user out of it, so it is never one tap away. */
    data class ConfirmWipeData(val apps: List<TargetApp>) : DialogRequest
}

/** Progress narration while an operation walks through its preconditions. */
data class BusyState(val stage: OverrideRepository.Stage) {
    val label: String
        get() = when (stage) {
            OverrideRepository.Stage.HOST -> "Preparing the instrumentation host"
            OverrideRepository.Stage.SHIZUKU -> "Waiting for Shizuku"
            OverrideRepository.Stage.PERMISSION -> "Waiting for permission"
            OverrideRepository.Stage.BINDING -> "Starting the shell service"
            OverrideRepository.Stage.RUNNING -> "Running"
        }

    /** Only the wait-for-Shizuku stage can last indefinitely, so only it offers a cancel affordance. */
    val cancellable: Boolean
        get() = stage == OverrideRepository.Stage.SHIZUKU ||
            stage == OverrideRepository.Stage.PERMISSION
}

/** Static facts about the phone and this build, shown once at the foot of the screen. */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val apiLevel: Int,
    val appVersion: String,
)

data class OverrideUiState(
    val shizuku: ShizukuStatus = ShizukuStatus.NotRunning,
    val sims: List<SimInfo> = emptyList(),
    /** Hardware slots, not subscriptions found — an unused slot still gets a placeholder. */
    val slotCount: Int = 2,
    val selectedSubId: Int = -1,
    val simScanError: String? = null,
    /** The chosen catalog entry, or null once the user has typed a value of their own. */
    val presetId: String? = RegionPresets.DEFAULT.id,
    val mccMnc: String = RegionPresets.DEFAULT.mccMnc,
    val countryIso: String = RegionPresets.DEFAULT.countryIso,
    val carrierName: String = RegionPresets.DEFAULT.carrier,
    val layers: LayerSelection = LayerSelection(
        simIdentity = true,
        appCountry = true,
        carrierNameOverride = true,
    ),
    val targetApps: List<TargetApp> = emptyList(),
    val wipeMode: WipeMode = WipeMode.NONE,
    val relaunchApps: Boolean = true,
    val busy: BusyState? = null,
    val result: ResultState = ResultState.Initial,
    val dialog: DialogRequest? = null,
    val device: DeviceInfo,
) {
    val selectedSim: SimInfo? get() = sims.firstOrNull { it.subId == selectedSubId }

    val preset: RegionPreset? get() = RegionPresets.byId(presetId)

    val isBusy: Boolean get() = busy != null

    /** The SIM identity layer rewrites IccRecords, which only exist once the SIM is fully loaded. */
    val canApply: Boolean get() = !isBusy && selectedSim?.isReady == true

    val canRestore: Boolean get() = !isBusy && selectedSim != null

    val canClearAll: Boolean get() = !isBusy && selectedSim != null
}
