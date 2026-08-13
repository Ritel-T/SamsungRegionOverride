package com.riteldevelopment.carriertestoverride.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riteldevelopment.carriertestoverride.BuildConfig
import com.riteldevelopment.carriertestoverride.data.InstrumentationHost
import com.riteldevelopment.carriertestoverride.data.OperationKind
import com.riteldevelopment.carriertestoverride.data.OperationOutcome
import com.riteldevelopment.carriertestoverride.data.OverrideException
import com.riteldevelopment.carriertestoverride.data.OverrideRepository
import com.riteldevelopment.carriertestoverride.data.OverrideStore
import com.riteldevelopment.carriertestoverride.data.RegionTarget
import com.riteldevelopment.carriertestoverride.data.ShizukuController
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.data.SimRepository
import com.riteldevelopment.carriertestoverride.data.SimScan
import com.riteldevelopment.carriertestoverride.data.TargetApp
import com.riteldevelopment.carriertestoverride.data.TargetAppRepository
import com.riteldevelopment.carriertestoverride.data.WipeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Holds the whole screen's state and owns the single privileged operation that may be in flight.
 *
 * In the 2.x Java build an operation was a `pendingAction` field re-entered from five different
 * callbacks; here it is one coroutine in [operationJob], which is why it can be cancelled and why the
 * preconditions read as a sequence instead of a graph.
 */
class OverrideViewModel(application: Application) : AndroidViewModel(application) {

    private val store = OverrideStore(application)
    private val sims = SimRepository(application, store)
    private val apps = TargetAppRepository(application)
    private val shizuku = ShizukuController()
    private val host = InstrumentationHost(application)
    private val repository = OverrideRepository(shizuku, host, store)

    private val _state = MutableStateFlow(
        OverrideUiState(
            device = DeviceInfo(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                apiLevel = Build.VERSION.SDK_INT,
                appVersion = BuildConfig.VERSION_NAME,
            )
        )
    )
    val state: StateFlow<OverrideUiState> = _state.asStateFlow()

    private var operationJob: Job? = null
    private var refreshJob: Job? = null

    init {
        shizuku.start()
        viewModelScope.launch {
            shizuku.status.collect { status -> _state.update { it.copy(shizuku = status) } }
        }
        refreshSims()
        refreshTargetApps()
    }

    override fun onCleared() {
        operationJob?.cancel()
        refreshJob?.cancel()
        shizuku.stop()
        host.release()
        super.onCleared()
    }

    // ---------------------------------------------------------------- environment

    /** Shizuku can be started, stopped or re-authorised while this screen is backgrounded. */
    fun refreshShizuku() = shizuku.refreshStatus()

    /** Re-reads what every SIM currently reports. Called on resume and repeatedly after an operation. */
    fun refreshSims() {
        when (val scan = sims.scan()) {
            is SimScan.Success -> _state.update { current ->
                val preferred = when {
                    scan.sims.any { it.subId == current.selectedSubId } -> current.selectedSubId
                    scan.sims.any { it.subId == sims.defaultDataSubId() } -> sims.defaultDataSubId()
                    else -> scan.sims.firstOrNull()?.subId ?: -1
                }
                current.copy(
                    sims = scan.sims,
                    slotCount = scan.slotCount,
                    selectedSubId = preferred,
                    simScanError = null,
                )
            }

            is SimScan.Failure -> _state.update { it.copy(simScanError = scan.message) }
        }
    }

    /** A target app can be installed or removed while this screen is backgrounded. */
    fun refreshTargetApps() = _state.update { it.copy(targetApps = apps.scan()) }

    /**
     * Telephony state settles asynchronously after an override, so poll for a few seconds instead of
     * showing a stale identity. Mirrors the 2.x behaviour of 7 reads spread over ~4.8s.
     */
    private fun refreshSimsUntilSettled() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            repeat(SIM_REFRESH_ATTEMPTS) { attempt ->
                refreshSims()
                if (attempt < SIM_REFRESH_ATTEMPTS - 1) delay(SIM_REFRESH_INTERVAL_MS)
            }
        }
    }

    // ---------------------------------------------------------------- editing

    fun selectSim(subId: Int) = _state.update { it.copy(selectedSubId = subId) }

    fun selectPreset(preset: RegionPreset) = _state.update {
        it.copy(
            presetId = preset.id,
            mccMnc = preset.mccMnc,
            countryIso = preset.countryIso,
            carrierName = preset.carrier,
        )
    }

    fun setMccMnc(value: String) = _state.update {
        it.copy(mccMnc = value.filter(Char::isDigit).take(MAX_MCC_MNC), presetId = null)
    }

    fun setCountryIso(value: String) = _state.update {
        it.copy(
            countryIso = value.filter(Char::isLetter).take(MAX_ISO).lowercase(Locale.ROOT),
            presetId = null,
        )
    }

    fun setCarrierName(value: String) = _state.update {
        it.copy(carrierName = value.take(MAX_NAME), presetId = null)
    }

    fun setSimIdentityLayer(enabled: Boolean) = _state.update {
        it.copy(layers = it.layers.copy(simIdentity = enabled))
    }

    fun setAppCountryLayer(enabled: Boolean) = _state.update {
        it.copy(layers = it.layers.copy(appCountry = enabled))
    }

    fun setCarrierNameOverride(enabled: Boolean) = _state.update {
        it.copy(layers = it.layers.copy(carrierNameOverride = enabled))
    }

    fun setWipeMode(mode: WipeMode) = _state.update { it.copy(wipeMode = mode) }

    fun setRelaunchApps(enabled: Boolean) = _state.update { it.copy(relaunchApps = enabled) }

    // ---------------------------------------------------------------- intents

    /** Validates, then asks for confirmation. Nothing privileged happens here. */
    fun requestApply() {
        val current = _state.value
        val sim = current.selectedSim ?: return fail("No usable SIM is selected.")
        if (!sim.isReady) return fail("The selected SIM is not READY yet.")

        val layers = current.layers
        if (layers.none) return fail("Enable at least one layer.")

        val mccMnc = current.mccMnc.trim()
        if (layers.simIdentity && !mccMnc.matches(MCC_MNC_PATTERN)) {
            return fail("SIM identity needs an MCC/MNC of 5 or 6 digits.")
        }
        val iso = current.countryIso.trim().lowercase(Locale.ROOT)
        if (layers.appCountry && !iso.matches(ISO_PATTERN)) {
            return fail("App country needs a two-letter country ISO.")
        }
        val name = current.carrierName.trim()
        if ((layers.simIdentity || (layers.appCountry && layers.carrierNameOverride)) && name.isEmpty()) {
            return fail("The selected layers need a carrier name.")
        }

        // Applying the target MCC/MNC over a SIM that already reports it, with no snapshot on file,
        // would freeze the fake value in as the "original". Refuse rather than lose the real one.
        if (layers.simIdentity && mccMnc == sim.operatorNumeric && !store.hasSimSnapshot(sim.subId)) {
            return fail(
                "This SIM already reports the target MCC/MNC and no snapshot was taken before it did. " +
                    "Reboot first so the real value can be recorded."
            )
        }

        _state.update {
            it.copy(
                dialog = DialogRequest.ConfirmApply(
                    sim = sim,
                    target = RegionTarget(mccMnc, iso, name),
                    layers = layers,
                )
            )
        }
    }

    fun requestRestore() {
        val current = _state.value
        val sim = current.selectedSim ?: return fail("No usable SIM is selected.")
        val flags = store.flags(sim.subId)
        if (!flags.any) {
            _state.update { it.copy(dialog = DialogRequest.RestoreWithoutMarkers(sim)) }
            return
        }
        startRestore(sim, restoreSim = flags.simIdentity, clearCountry = flags.appCountry)
    }

    fun requestClearAll() {
        val sim = _state.value.selectedSim ?: return fail("No usable SIM is selected.")
        _state.update { it.copy(dialog = DialogRequest.ConfirmClearAll(sim)) }
    }

    /** Wiping data signs the user out of the app, so that one variant needs an answer first. */
    fun requestRefreshApps(app: TargetApp) {
        if (!app.installed) return fail("${app.label} is not installed.")
        if (_state.value.wipeMode.destructive) {
            _state.update { it.copy(dialog = DialogRequest.ConfirmWipeData(listOf(app))) }
            return
        }
        startRefreshApps(listOf(app))
    }

    fun dismissDialog() = _state.update { it.copy(dialog = null) }

    /**
     * Only reports a cancellation that actually happened.
     *
     * Claiming "cancelled" over an operation that had in fact already completed would be the worst lie
     * this screen can tell: the user would leave a live telephony override in place believing nothing was
     * written, and never run a restore. So a job that is no longer active keeps its real result.
     */
    fun cancelOperation() {
        val job = operationJob ?: return
        if (!job.isActive) return
        job.cancel()
        _state.update {
            it.copy(busy = null, result = ResultState("Cancelled.", tone = ResultTone.IDLE))
        }
    }

    // ---------------------------------------------------------------- confirmed actions

    fun confirmApply(request: DialogRequest.ConfirmApply) {
        dismissDialog()
        runOperation {
            repository.apply(request.sim, request.target, request.layers, ::reportStage)
        }
    }

    /** The user chose to restore using the current layer switches despite having no markers. */
    fun confirmRestoreWithoutMarkers(sim: SimInfo) {
        dismissDialog()
        val layers = _state.value.layers
        startRestore(sim, restoreSim = layers.simIdentity, clearCountry = layers.appCountry)
    }

    fun confirmClearAll(sim: SimInfo) {
        dismissDialog()
        runOperation { repository.clearAllCarrierConfig(sim, ::reportStage) }
    }

    fun confirmWipeData(apps: List<TargetApp>) {
        dismissDialog()
        startRefreshApps(apps)
    }

    private fun startRestore(sim: SimInfo, restoreSim: Boolean, clearCountry: Boolean) {
        if (!restoreSim && !clearCountry) return fail("Enable at least one layer.")
        runOperation { repository.restore(sim, restoreSim, clearCountry, ::reportStage) }
    }

    private fun startRefreshApps(targets: List<TargetApp>) {
        val current = _state.value
        runOperation {
            repository.refreshApps(
                packages = targets.map { it.packageName },
                wipeMode = current.wipeMode,
                relaunch = current.relaunchApps,
                onStage = ::reportStage,
            )
        }
    }

    // ---------------------------------------------------------------- execution

    private fun runOperation(block: suspend () -> OperationOutcome) {
        if (operationJob?.isActive == true) return
        // Started lazily so `self` is assigned before the body can reach its own `finally`. Without the
        // identity check below, a cancelled job unwinding late would clear the handle of whichever
        // operation started after it, and the guard above would then wave a second concurrent operation
        // through onto the same subId.
        var self: Job? = null
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val outcome = block()
                _state.update { it.copy(busy = null, result = outcome.toResultState()) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (expected: OverrideException) {
                _state.update {
                    it.copy(
                        busy = null,
                        result = ResultState(expected.message.orEmpty(), tone = ResultTone.ERROR),
                    )
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        busy = null,
                        result = ResultState(
                            headline = "Operation failed",
                            detail = "${throwable.javaClass.name}: ${throwable.message.orEmpty()}",
                            tone = ResultTone.ERROR,
                        ),
                    )
                }
            } finally {
                if (operationJob === self) operationJob = null
                refreshSimsUntilSettled()
            }
        }
        self = job
        operationJob = job
        job.start()
    }

    private fun reportStage(stage: OverrideRepository.Stage) {
        _state.update { it.copy(busy = BusyState(stage)) }
    }

    private fun fail(message: String) {
        _state.update { it.copy(result = ResultState(message, tone = ResultTone.ERROR)) }
    }

    private fun OperationOutcome.toResultState(): ResultState {
        val partial = simLayerFailed || countryLayerFailed
        val tone = when {
            isError -> ResultTone.ERROR
            partial -> ResultTone.PARTIAL
            else -> ResultTone.SUCCESS
        }
        val headline = when {
            isError -> "Operation failed"
            partial -> "One layer failed"
            kind == OperationKind.APPLY -> "Region applied"
            kind == OperationKind.RESTORE -> "Restored this tool's overrides"
            kind == OperationKind.REFRESH_APPS -> "Target apps refreshed"
            else -> "All CarrierConfig test overrides cleared"
        }
        return ResultState(headline = headline, detail = message, probe = probe, tone = tone)
    }

    private companion object {
        val MCC_MNC_PATTERN = Regex("[0-9]{5,6}")
        val ISO_PATTERN = Regex("[a-z]{2}")
        const val MAX_MCC_MNC = 6
        const val MAX_ISO = 2
        const val MAX_NAME = 80
        const val SIM_REFRESH_ATTEMPTS = 7
        const val SIM_REFRESH_INTERVAL_MS = 800L
    }
}
