package com.riteldevelopment.carriertestoverride.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riteldevelopment.carriertestoverride.BuildConfig
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.data.InstrumentationHost
import com.riteldevelopment.carriertestoverride.data.LayerSelection
import com.riteldevelopment.carriertestoverride.data.OperationKind
import com.riteldevelopment.carriertestoverride.data.OperationOutcome
import com.riteldevelopment.carriertestoverride.data.OverrideException
import com.riteldevelopment.carriertestoverride.data.OverrideNotifier
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
import java.util.concurrent.atomic.AtomicBoolean

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
    private val apps = TargetAppRepository(application, store)
    private val notifier = OverrideNotifier(application)
    private val shizuku = ShizukuController()
    private val host = InstrumentationHost(application)
    private val repository = OverrideRepository(shizuku, host, store)
    private val initialRecentPresetIds = store.recentPresetIds()
    private val initialPreset = RegionPresets.lastUsedOrDefault(initialRecentPresetIds)

    private val _state = MutableStateFlow(
        OverrideUiState(
            device = DeviceInfo(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                apiLevel = Build.VERSION.SDK_INT,
                appVersion = BuildConfig.VERSION_NAME,
            ),
            presetId = initialPreset.id,
            mccMnc = initialPreset.mccMnc,
            countryIso = initialPreset.countryIso,
            carrierName = initialPreset.carrier,
            recentPresetIds = initialRecentPresetIds,
        )
    )
    val state: StateFlow<OverrideUiState> = _state.asStateFlow()

    private var operationJob: Job? = null
    private var refreshJob: Job? = null
    /** Null until the user chooses a card or a notification names the restore target. */
    private var explicitSelectedSubId: Int? = null
    @Volatile
    private var activeOperation: OperationControl? = null
    private var activeDiagnosticContext: DiagnosticContext? = null
    private var lastStage: OverrideRepository.Stage? = null
    private var operationStartedAt: Long = 0L
    private val resourcesReleased = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        shizuku.start()
        viewModelScope.launch {
            shizuku.status.collect { status -> _state.update { it.copy(shizuku = status) } }
        }
        refreshSims()
        refreshTargetApps()
    }

    override fun onCleared() {
        val running = operationJob
        running?.cancel()
        refreshJob?.cancel()
        if (running != null && !running.isCompleted) {
            // The Binder write and its flag reconciliation run in NonCancellable. Releasing either
            // dependency here can kill the instrumentation host halfway through that protected tail.
            running.invokeOnCompletion { mainHandler.post { releaseResources() } }
        } else {
            releaseResources()
        }
    }

    private fun releaseResources() {
        if (!resourcesReleased.compareAndSet(false, true)) return
        shizuku.stop()
        host.release()
    }

    // ---------------------------------------------------------------- environment

    /** Shizuku can be started, stopped or re-authorised while this screen is backgrounded. */
    fun refreshShizuku() = shizuku.refreshStatus()

    /** Re-reads what every SIM currently reports. Called on resume and repeatedly after an operation. */
    fun refreshSims() {
        when (val scan = sims.scan()) {
            is SimScan.Success -> {
                // The ongoing notice tracks the scan rather than the operations, so it stays right
                // whatever put the phone in this state: an apply here, a restore from the notification
                // itself, or a reboot that quietly dropped every override while the app was closed.
                notifier.sync(scan.sims)
                val promptDue = scan.sims.any { it.disguised } &&
                    !notifier.canPost() &&
                    !store.notificationPromptShown()
                val scannedSubIds = scan.sims.map(SimInfo::subId)
                val defaultDataSubId = sims.defaultDataSubId()
                _state.update { current ->
                    current.copy(
                        sims = scan.sims,
                        slotCount = scan.slotCount,
                        selectedSubId = resolveSelectedSubIdAfterScan(
                            currentSelectedSubId = current.selectedSubId,
                            scannedSubIds = scannedSubIds,
                            defaultDataSubId = defaultDataSubId,
                            selectionIsExplicit = explicitSelectedSubId != null,
                        ),
                        simScanError = null,
                        notificationPromptDue = promptDue,
                    )
                }
            }

            is SimScan.Failure -> _state.update { it.copy(simScanError = scan.message) }
        }
    }

    /** A target app can be installed or removed while this screen is backgrounded. */
    fun refreshTargetApps() = _state.update {
        it.copy(targetApps = apps.scan(), targetAppsAreDefault = apps.usingDefaults())
    }

    /**
     * Opens Shizuku, which is the answer to most of the states this screen can be stuck in.
     *
     * Resolving a launch intent rather than naming an activity: Shizuku's entry point has moved between
     * releases, and a hardcoded component would break on the next one while still looking installed.
     * A missing intent is reported as advice, not as an [android.content.ActivityNotFoundException] —
     * "not installed" is a thing a user can act on.
     */
    fun openShizuku() {
        val context = getApplication<Application>()
        val intent = context.packageManager.getLaunchIntentForPackage(KnownPackages.SHIZUKU)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent == null) {
            return fail(R.string.error_shizuku_missing)
        }
        runCatching { context.startActivity(intent) }.onFailure { throwable ->
            fail(R.string.error_open_shizuku, throwable.javaClass.simpleName)
        }
    }

    /** Opens Android's per-app language page on 13+, and the system language page on older releases. */
    fun openLanguageSettings() {
        val context = getApplication<Application>()
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS).setData(
                "package:${context.packageName}".toUri()
            )
        } else {
            Intent(Settings.ACTION_LOCALE_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { throwable ->
            fail(R.string.error_open_language_settings, throwable.javaClass.simpleName)
        }
    }

    fun openGitHubProject() = openExternalUrl(GITHUB_PROJECT_URL)

    fun openGitHubIssue() = openExternalUrl(GITHUB_ISSUE_URL)

    private fun openExternalUrl(url: String) {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { throwable ->
            fail(R.string.error_open_github, throwable.javaClass.simpleName)
        }
    }

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

    fun selectSim(subId: Int) {
        explicitSelectedSubId = subId
        _state.update { it.copy(selectedSubId = subId) }
    }

    fun selectPreset(preset: RegionPreset) = _state.update {
        it.copy(
            presetId = preset.id,
            mccMnc = preset.mccMnc,
            countryIso = preset.countryIso,
            carrierName = preset.carrier,
        )
    }

    fun setMccMnc(value: String) = _state.update {
        it.copy(mccMnc = normalizeMccMncInput(value), presetId = null)
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
        val sim = current.selectedSim ?: return fail(R.string.error_no_sim_selected)

        val layers = current.layers
        if (layers.none) return fail(R.string.error_enable_layer)
        // Only the SIM identity layer needs loaded IccRecords, so only it needs a READY card. Naming the
        // layer and the actual state beats "the selected SIM is not READY yet", which left the user to
        // guess which of the two switches was the problem.
        if (layers.simIdentity && !sim.isReady) {
            return fail(
                R.string.error_network_needs_ready,
                LocalizedText.resource(R.string.sim_number, sim.slotIndex + 1),
                LocalizedText.resource(SimInfo.simStateNameRes(sim.simState)),
            )
        }

        val mccMnc = current.mccMnc.trim()
        if (layers.simIdentity && !isValidMccMnc(mccMnc)) {
            return fail(R.string.error_invalid_mcc_mnc)
        }
        val iso = current.countryIso.trim().lowercase(Locale.ROOT)
        if (layers.appCountry && !iso.matches(ISO_PATTERN)) {
            return fail(R.string.error_invalid_iso)
        }
        val name = current.carrierName.trim()
        if ((layers.simIdentity || (layers.appCountry && layers.carrierNameOverride)) && name.isEmpty()) {
            return fail(R.string.error_carrier_name_required)
        }

        // Applying the target MCC/MNC over a SIM that already reports it, with no snapshot on file,
        // would freeze the fake value in as the "original". Refuse rather than lose the real one.
        if (layers.simIdentity && mccMnc == sim.operatorNumeric && !store.hasSimSnapshot(sim.subId)) {
            return fail(R.string.error_target_already_reported)
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
        val sim = current.selectedSim ?: return fail(R.string.error_no_sim_selected)
        val flags = store.flags(sim.subId)
        if (!flags.any) {
            _state.update { it.copy(dialog = DialogRequest.RestoreWithoutMarkers(sim)) }
            return
        }
        startRestore(sim, restoreSim = flags.simIdentity, clearCountry = flags.appCountry)
    }

    /**
     * The Restore button on the ongoing notification.
     *
     * Runs without a confirmation, which every other privileged path here asks for. Restore is the one
     * operation that only ever takes state away — it puts the SIM back to what was captured before the
     * first override — so the thing a confirmation exists to prevent cannot happen, and the button is
     * pressed precisely by someone who has just noticed their phone is disguised and wants it to stop.
     *
     * A stale notification is answered quietly. The layer flags, not the notification, decide what gets
     * undone, so a button pressed after the override is already gone finds nothing to do and says
     * nothing — rather than opening the "no markers on this SIM" dialog, which is a question about a
     * situation the user did not create.
     */
    fun restoreFromNotification(subId: Int) {
        // Not silent, unlike the stale case below: the SIM this notice was about is gone from the phone,
        // so the override cannot be undone from here, and a button that does nothing at all would leave
        // the user believing it had been.
        val sim = _state.value.sims.firstOrNull { it.subId == subId }
            ?: return fail(R.string.error_sim_removed)
        explicitSelectedSubId = subId
        _state.update { it.copy(selectedSubId = subId) }
        val flags = store.flags(subId)
        if (!flags.any) return
        startRestore(sim, restoreSim = flags.simIdentity, clearCountry = flags.appCountry)
    }

    /**
     * Records that the notification permission has been asked for, and stops asking.
     *
     * Called by the screen as it launches the system dialog rather than when the answer comes back. A
     * refusal and a grant are the same thing here — the app asked once, and the flag has to be set
     * before the state next recomputes, or the same dialog is requested again on the next scan.
     */
    fun markNotificationPromptShown() {
        store.markNotificationPromptShown()
        _state.update { it.copy(notificationPromptDue = false) }
    }

    fun requestClearAll() {
        val sim = _state.value.selectedSim ?: return fail(R.string.error_no_sim_selected)
        _state.update { it.copy(dialog = DialogRequest.ConfirmClearAll(sim)) }
    }

    /** Wiping data signs the user out of the app, so that one variant needs an answer first. */
    fun requestRefreshApps(app: TargetApp) {
        if (!app.installed) return fail(R.string.error_app_not_installed, app.label)
        if (_state.value.wipeMode.destructive) {
            _state.update { it.copy(dialog = DialogRequest.ConfirmWipeData(listOf(app))) }
            return
        }
        startRefreshApps(listOf(app))
    }

    /**
     * Opens the target-app chooser, then fills it in.
     *
     * The dialog is shown before its contents exist. Labelling every launchable app takes long enough
     * to notice, and a button that does nothing for a second reads as broken — so the dialog opens
     * immediately and says it is still reading.
     */
    fun requestChooseTargetApps() {
        if (_state.value.isBusy) return
        _state.update {
            it.copy(
                dialog = DialogRequest.ChooseTargetApps(
                    available = emptyList(),
                    selected = apps.selectedPackages().toSet(),
                    loading = true,
                )
            )
        }
        viewModelScope.launch {
            val available = apps.installedApps()
            _state.update { current ->
                // The user may have dismissed it, or opened a different dialog, while this loaded.
                val dialog = current.dialog as? DialogRequest.ChooseTargetApps ?: return@update current
                current.copy(dialog = dialog.copy(available = available, loading = false))
            }
        }
    }

    fun toggleTargetApp(packageName: String) = _state.update { current ->
        val dialog = current.dialog as? DialogRequest.ChooseTargetApps ?: return@update current
        val selected = if (packageName in dialog.selected) {
            dialog.selected - packageName
        } else {
            dialog.selected + packageName
        }
        current.copy(dialog = dialog.copy(selected = selected))
    }

    /**
     * Saves the choice, ordered as the picker showed it so the panel does not reshuffle on save.
     *
     * An empty selection is saved as an empty selection. It means "stop nothing", and answering it by
     * quietly falling back to the defaults would be the tool overruling the user.
     */
    fun confirmTargetApps(dialog: DialogRequest.ChooseTargetApps) {
        dismissDialog()
        val ordered = dialog.available.map { it.packageName }.filter { it in dialog.selected }
        // available unions the current selection in, so this should always be empty. Appending rather
        // than dropping means that if it ever is not, the tool keeps acting on what the user ticked.
        val unlisted = dialog.selected - ordered.toSet()
        apps.select(ordered + unlisted)
        refreshTargetApps()
    }

    /** Forgets the custom list so the built-in defaults apply again. */
    fun resetTargetApps() {
        dismissDialog()
        apps.resetToDefaults()
        refreshTargetApps()
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
        val control = activeOperation ?: return
        val job = control.job ?: return
        if (!job.isActive || !control.cancellationRequested.compareAndSet(false, true)) return
        val context = activeDiagnosticContext
        val diagnostic = context?.let {
            diagnosticFor(
                context = it,
                result = ResultTone.IDLE,
                failure = DiagnosticFailure.CANCELLED,
            )
        }
        // Result publication and cancellation race on different dispatcher threads. Whichever side
        // wins this CAS owns the visible result; the other side must leave it untouched.
        if (control.resultPublished.compareAndSet(false, true) && activeOperation === control) {
            _state.update {
                it.copy(
                    busy = null,
                    result = ResultState(
                        LocalizedText.resource(R.string.result_cancelled),
                        tone = ResultTone.IDLE,
                        diagnostic = diagnostic,
                    ),
                )
            }
        }
        job.cancel()
    }

    // ---------------------------------------------------------------- confirmed actions

    fun confirmApply(request: DialogRequest.ConfirmApply) {
        dismissDialog()
        val presetId = _state.value.presetId
        runOperation(
            DiagnosticContext(
                operation = OperationKind.APPLY,
                slotIndex = request.sim.slotIndex,
                layers = request.layers,
                targetCountry = request.target.countryIso,
            )
        ) {
            repository.apply(request.sim, request.target, request.layers, ::reportStage)
                .also { outcome -> if (!outcome.isError) rememberApplied(presetId) }
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
        runOperation(
            DiagnosticContext(operation = OperationKind.CLEAR_ALL, slotIndex = sim.slotIndex)
        ) { repository.clearAllCarrierConfig(sim, ::reportStage) }
    }

    fun confirmWipeData(apps: List<TargetApp>) {
        dismissDialog()
        startRefreshApps(apps)
    }

    private fun startRestore(sim: SimInfo, restoreSim: Boolean, clearCountry: Boolean) {
        if (!restoreSim && !clearCountry) return fail(R.string.error_enable_layer)
        runOperation(
            DiagnosticContext(
                operation = OperationKind.RESTORE,
                slotIndex = sim.slotIndex,
                layers = LayerSelection(
                    simIdentity = restoreSim,
                    appCountry = clearCountry,
                    carrierNameOverride = false,
                ),
            )
        ) { repository.restore(sim, restoreSim, clearCountry, ::reportStage) }
    }

    /**
     * Records an applied region for the chip row.
     *
     * Presets only: a chip renders a country, a carrier and a code, and a hand-typed target has no
     * catalog entry to render from. Successful applies only: the row is a shortcut to things that
     * worked, not a log of what was attempted.
     */
    private fun rememberApplied(presetId: String?) {
        if (presetId == null) return
        store.rememberPreset(presetId)
        _state.update { it.copy(recentPresetIds = store.recentPresetIds()) }
    }

    private fun startRefreshApps(targets: List<TargetApp>) {
        val current = _state.value
        runOperation(
            DiagnosticContext(
                operation = OperationKind.REFRESH_APPS,
                targetAppCount = targets.size,
            )
        ) {
            repository.refreshApps(
                packages = targets.map { it.packageName },
                wipeMode = current.wipeMode,
                relaunch = current.relaunchApps,
                onStage = ::reportStage,
            )
        }
    }

    // ---------------------------------------------------------------- execution

    private fun runOperation(context: DiagnosticContext, block: suspend () -> OperationOutcome) {
        if (operationJob?.isActive == true) return
        // Started lazily so `self` is assigned before the body can reach its own `finally`. Without the
        // identity check below, a cancelled job unwinding late would clear the handle of whichever
        // operation started after it, and the guard above would then wave a second concurrent operation
        // through onto the same subId.
        var self: Job? = null
        val control = OperationControl()
        activeOperation = control
        activeDiagnosticContext = context
        lastStage = null
        operationStartedAt = SystemClock.elapsedRealtime()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val outcome = block()
                publishResult(control) {
                    _state.update { it.copy(busy = null, result = outcome.toResultState(context)) }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (expected: OverrideException) {
                publishResult(control) {
                    _state.update {
                        it.copy(
                            busy = null,
                            result = ResultState(
                                expected.localizedText(),
                                tone = ResultTone.ERROR,
                                diagnostic = diagnosticFor(
                                    context = context,
                                    result = ResultTone.ERROR,
                                    failure = DiagnosticFailure.VALIDATION,
                                    exception = expected.javaClass.simpleName,
                                ),
                            ),
                        )
                    }
                }
            } catch (throwable: Throwable) {
                publishResult(control) {
                    _state.update {
                        it.copy(
                            busy = null,
                            result = ResultState(
                                headline = LocalizedText.resource(R.string.result_operation_failed),
                                detail = "${throwable.javaClass.name}: ${throwable.message.orEmpty()}",
                                tone = ResultTone.ERROR,
                                diagnostic = diagnosticFor(
                                    context = context,
                                    result = ResultTone.ERROR,
                                    failure = DiagnosticFailure.OPERATION,
                                    exception = throwable.javaClass.simpleName,
                                ),
                            ),
                        )
                    }
                }
            } finally {
                if (operationJob === self && activeOperation === control) {
                    operationJob = null
                    activeOperation = null
                    activeDiagnosticContext = null
                    lastStage = null
                    operationStartedAt = 0L
                }
                refreshSimsUntilSettled()
            }
        }
        self = job
        control.job = job
        operationJob = job
        job.start()
    }

    /** Publishes at most one terminal result for this operation, winning against cancellation atomically. */
    private inline fun publishResult(control: OperationControl, publish: () -> Unit) {
        if (activeOperation !== control || control.cancellationRequested.get()) return
        if (control.resultPublished.compareAndSet(false, true)) publish()
    }

    private fun reportStage(stage: OverrideRepository.Stage) {
        lastStage = stage
        _state.update { it.copy(busy = BusyState(stage)) }
    }

    private fun fail(@StringRes messageRes: Int, vararg args: Any) {
        _state.update {
            it.copy(
                result = ResultState(
                    LocalizedText.resource(messageRes, *args),
                    tone = ResultTone.ERROR,
                    diagnostic = diagnosticFor(
                        context = null,
                        result = ResultTone.ERROR,
                        failure = DiagnosticFailure.VALIDATION,
                    ),
                )
            )
        }
    }

    private fun OperationOutcome.toResultState(context: DiagnosticContext): ResultState {
        val partial = simLayerFailed || countryLayerFailed
        // `partial` is tested first, and that order is the whole point. Every per-layer failure is
        // reported with an "ERROR: " prefix, so `isError` is always true when one layer failed too —
        // testing it first made PARTIAL unreachable and told the user "Operation failed" after a run
        // where the other layer had in fact been written. That is the one wrong answer here, because a
        // user who believes nothing landed never runs a restore.
        val tone = when {
            partial -> ResultTone.PARTIAL
            isError -> ResultTone.ERROR
            // Everything the user asked for landed, so this is not an error — but a green "Region
            // applied" on a phone that can no longer take calls is a lie of omission. PARTIAL is the
            // tone that means "it worked, and you still need to look at this".
            imsUnregistered || imsRecoveryUnconfirmed -> ResultTone.PARTIAL
            else -> ResultTone.SUCCESS
        }
        val headlineRes = when {
            partial -> R.string.result_one_layer_failed
            isError -> R.string.result_operation_failed
            imsUnregistered -> R.string.result_disguise_ims_unregistered
            imsRecoveryUnconfirmed -> R.string.result_restore_unconfirmed
            kind == OperationKind.APPLY -> R.string.result_disguise_active
            kind == OperationKind.RESTORE -> R.string.result_restored
            kind == OperationKind.REFRESH_APPS -> R.string.result_apps_refreshed
            else -> R.string.result_all_carrier_config_cleared
        }
        return ResultState(
            headline = LocalizedText.resource(headlineRes),
            detail = message,
            probe = probe,
            tone = tone,
            diagnostic = diagnosticFor(
                context = context,
                result = tone,
                failure = when {
                    simLayerFailed && countryLayerFailed -> DiagnosticFailure.MULTIPLE_LAYERS
                    simLayerFailed -> DiagnosticFailure.SIM_LAYER
                    countryLayerFailed -> DiagnosticFailure.COUNTRY_LAYER
                    imsUnregistered || imsRecoveryUnconfirmed -> DiagnosticFailure.IMS
                    isError -> DiagnosticFailure.OPERATION
                    else -> DiagnosticFailure.NONE
                },
                ims = when {
                    imsUnregistered -> DiagnosticIms.UNREGISTERED
                    imsRecoveryUnconfirmed -> DiagnosticIms.UNCONFIRMED
                    kind == OperationKind.APPLY || kind == OperationKind.RESTORE ->
                        DiagnosticIms.REGISTERED
                    else -> DiagnosticIms.UNKNOWN
                },
                runtime = DiagnosticReport.runtime(
                    probe = probe,
                    requested = kind != OperationKind.REFRESH_APPS,
                ),
            ),
        )
    }

    private fun diagnosticFor(
        context: DiagnosticContext?,
        result: ResultTone,
        failure: DiagnosticFailure,
        ims: DiagnosticIms = DiagnosticIms.UNKNOWN,
        runtime: DiagnosticRuntime = DiagnosticRuntime.NOT_REQUESTED,
        exception: String? = null,
    ): DiagnosticReport = DiagnosticReport(
        appVersion = _state.value.device.appVersion,
        manufacturer = _state.value.device.manufacturer,
        model = _state.value.device.model,
        apiLevel = _state.value.device.apiLevel,
        operation = context?.operation,
        slotIndex = context?.slotIndex?.plus(1),
        layers = DiagnosticReport.layers(context?.layers),
        targetCountry = context?.targetCountry,
        targetAppCount = context?.targetAppCount ?: 0,
        result = result,
        ims = ims,
        shizuku = DiagnosticReport.shizuku(_state.value.shizuku),
        stage = lastStage,
        failure = failure,
        runtime = runtime,
        exception = exception,
        durationMs = operationStartedAt.takeIf { it > 0L }
            ?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) },
    )

    private class OperationControl {
        @Volatile
        var job: Job? = null
        val cancellationRequested = AtomicBoolean(false)
        val resultPublished = AtomicBoolean(false)
    }

    private fun OverrideException.localizedText(): LocalizedText =
        messageRes?.let { LocalizedText.resource(it, *formatArgs) }
            ?: LocalizedText.Literal(message.orEmpty())

    private companion object {
        val ISO_PATTERN = Regex("[a-z]{2}")
        const val MAX_ISO = 2
        const val MAX_NAME = 80
        const val SIM_REFRESH_ATTEMPTS = 7
        const val SIM_REFRESH_INTERVAL_MS = 800L
        const val GITHUB_PROJECT_URL = "https://github.com/Ritel-T/SamsungRegionOverride"
        const val GITHUB_ISSUE_URL =
            "https://github.com/Ritel-T/SamsungRegionOverride/issues/new?template=bug-report.yml"
    }
}

private val MCC_MNC_PATTERN = Regex("[0-9]{5,6}")
private const val MAX_MCC_MNC = 6

internal fun isValidMccMnc(value: String): Boolean = MCC_MNC_PATTERN.matches(value)

/** Converts every Unicode decimal digit, including supplementary-plane digits, to ASCII. */
internal fun normalizeMccMncInput(value: String): String = buildString(MAX_MCC_MNC) {
    var offset = 0
    while (offset < value.length && length < MAX_MCC_MNC) {
        val codePoint = value.codePointAt(offset)
        offset += Character.charCount(codePoint)
        if (Character.getType(codePoint) != Character.DECIMAL_DIGIT_NUMBER.toInt()) continue
        val digit = Character.digit(codePoint, 10)
        if (digit in 0..9) append('0' + digit)
    }
}
