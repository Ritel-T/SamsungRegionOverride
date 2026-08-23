package com.riteldevelopment.carriertestoverride.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.riteldevelopment.carriertestoverride.R
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
    val headline: LocalizedText,
    val detail: String? = null,
    val probe: String? = null,
    val tone: ResultTone = ResultTone.IDLE,
) {
    companion object {
        val Initial = ResultState(LocalizedText.Empty, tone = ResultTone.IDLE)
    }
}

/** Text that stays tied to a resource until Compose resolves it under the current app locale. */
sealed interface LocalizedText {
    @Composable
    fun resolve(): String

    @Composable
    fun ifBlank(defaultValue: @Composable () -> String): String {
        val resolved = resolve()
        return if (resolved.isBlank()) defaultValue() else resolved
    }

    fun resolveWith(resolver: (id: Int, args: List<Any>) -> String): String

    data class Resource(
        @param:StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : LocalizedText {
        @Composable
        override fun resolve(): String {
            val resolvedArgs = ArrayList<Any>(args.size)
            for (arg in args) {
                resolvedArgs += if (arg is LocalizedText) arg.resolve() else arg
            }
            return stringResource(id, *resolvedArgs.toTypedArray())
        }

        override fun resolveWith(resolver: (id: Int, args: List<Any>) -> String): String {
            val resolvedArgs = args.map { arg ->
                if (arg is LocalizedText) arg.resolveWith(resolver) else arg
            }
            return resolver(id, resolvedArgs)
        }
    }

    data class Literal(val value: String) : LocalizedText {
        @Composable
        override fun resolve(): String = value

        override fun resolveWith(resolver: (id: Int, args: List<Any>) -> String): String = value
    }

    data object Empty : LocalizedText {
        @Composable
        override fun resolve(): String = ""

        override fun resolveWith(resolver: (id: Int, args: List<Any>) -> String): String = ""
    }

    companion object {
        fun resource(@StringRes id: Int, vararg args: Any): LocalizedText =
            Resource(id, args.toList())
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

    /**
     * Choosing which apps apply, restore and the panel act on.
     *
     * [available] arrives asynchronously — enumerating and labelling every launchable app is too slow
     * for the main thread — so the dialog opens empty with [loading] set and fills in. Opening it
     * immediately and admitting it is still reading beats a button that appears dead for a second.
     *
     * [selected] is the working set, not the saved one: it changes as boxes are ticked and is only
     * written to storage when the user confirms, so dismissing the dialog changes nothing.
     */
    data class ChooseTargetApps(
        val available: List<TargetApp>,
        val selected: Set<String>,
        val loading: Boolean,
    ) : DialogRequest
}

/**
 * A one-tap region above the picker.
 *
 * [recent] separates "you applied this before" from "this is a common destination", which the chip row
 * shows differently — the first is the user's own history and earns the front of the row.
 */
data class QuickPick(val preset: RegionPreset, val recent: Boolean)

/** Progress narration while an operation walks through its preconditions. */
data class BusyState(val stage: OverrideRepository.Stage) {
    @get:StringRes
    val labelRes: Int
        get() = when (stage) {
            OverrideRepository.Stage.HOST -> R.string.busy_host
            OverrideRepository.Stage.SHIZUKU -> R.string.busy_shizuku
            OverrideRepository.Stage.PERMISSION -> R.string.busy_permission
            OverrideRepository.Stage.BINDING -> R.string.busy_binding
            OverrideRepository.Stage.RUNNING -> R.string.busy_running
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
    /** Preset ids applied before, newest first. Hand-typed targets have no id and are not recorded. */
    val recentPresetIds: List<String> = emptyList(),
    val targetApps: List<TargetApp> = emptyList(),
    /** False once the user has chosen their own target list, which is when "reset" starts meaning something. */
    val targetAppsAreDefault: Boolean = true,
    val wipeMode: WipeMode = WipeMode.NONE,
    val relaunchApps: Boolean = true,
    /**
     * A SIM is disguised, the ongoing notice cannot be posted, and the user has never been asked.
     *
     * Carried in state rather than checked when the screen resumes, because the moment it first becomes
     * true is usually an apply performed with the screen already open — no resume follows it, so a
     * resume-driven prompt would never fire on the run that needed it.
     */
    val notificationPromptDue: Boolean = false,
    val busy: BusyState? = null,
    val result: ResultState = ResultState.Initial,
    val dialog: DialogRequest? = null,
    val device: DeviceInfo,
) {
    val selectedSim: SimInfo? get() = sims.firstOrNull { it.subId == selectedSubId }

    val preset: RegionPreset? get() = RegionPresets.byId(presetId)

    /**
     * The chip row: what this user reached for last, then the common destinations they have not.
     *
     * History wins the front of the row and the duplicates, because a region you have already applied
     * is a region you are more likely to want again than one the catalog merely thinks is popular. A
     * recent id that no longer resolves — a catalog entry removed between versions — is dropped rather
     * than rendered as a dead chip.
     */
    val quickPicks: List<QuickPick>
        get() {
            val recent = recentPresetIds.mapNotNull(RegionPresets::byId)
            val recentIds = recent.mapTo(HashSet()) { it.id }
            return recent.map { QuickPick(it, recent = true) } +
                RegionPresets.COMMON
                    .filterNot { it.id in recentIds }
                    .map { QuickPick(it, recent = false) }
        }

    val isBusy: Boolean get() = busy != null

    /**
     * The SIM identity layer rewrites IccRecords, which only exist once the SIM is fully loaded — so
     * READY is required only when that layer is actually part of the operation. The app country layer
     * writes CarrierConfig against a subId and does not care what state the card is in.
     *
     * This used to demand READY unconditionally, which blocked a legitimate App-country-only apply on a
     * PIN-locked SIM and explained itself only inside the SIM identity section, which is collapsed
     * whenever that switch is off.
     */
    val canApply: Boolean
        get() = !isBusy && selectedSim != null &&
            selectedSim?.flags?.uncertain != true &&
            (!layers.simIdentity || selectedSim?.isReady == true)

    val canRestore: Boolean get() = !isBusy && selectedSim != null

    val canClearAll: Boolean get() = !isBusy && selectedSim != null
}
