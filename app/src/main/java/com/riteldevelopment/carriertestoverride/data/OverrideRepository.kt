package com.riteldevelopment.carriertestoverride.data

import com.riteldevelopment.carriertestoverride.CarrierOverrideUserService
import com.riteldevelopment.carriertestoverride.ICarrierOverrideService
import com.riteldevelopment.carriertestoverride.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.Locale

/** The region the user is asking the two layers to report. */
data class RegionTarget(
    val mccMnc: String,
    val countryIso: String,
    val carrierName: String,
) {
    /**
     * A syntactically valid test IMSI derived from the target network, padded to 15 digits. The
     * subscriber part is deliberately constant — nothing reads it except the SIM identity layer's own
     * plausibility check.
     */
    val testImsi: String
        get() = buildString {
            append(mccMnc)
            while (length < 14) append('0')
            append('1')
        }
}

/** Which of the two layers an operation should touch. */
data class LayerSelection(
    val simIdentity: Boolean,
    val appCountry: Boolean,
    val carrierNameOverride: Boolean,
) {
    val none: Boolean get() = !simIdentity && !appCountry
}

/** Stored live or pending state is authoritative when deciding whether a UICC cycle is safe. */
internal fun networkMayBeLive(flags: OverrideStore.Flags): Boolean =
    flags.simIdentity || flags.simPending

/** What kind of privileged operation ran, used only for wording progress and results. */
enum class OperationKind { APPLY, RESTORE, CLEAR_ALL, REFRESH_APPS }

/**
 * The outcome of one privileged operation.
 *
 * [simLayerFailed] and [countryLayerFailed] are recovered by matching the report the Java UserService
 * produces. That is a string contract rather than a typed one, but not a fragile one: the markers are
 * the very constants the service writes, so the two sides cannot drift apart silently. Making it typed
 * would mean revising the AIDL surface, which costs a re-validation of the privileged path on real
 * hardware and buys nothing the compiler is not already checking.
 */
data class OperationOutcome(
    val kind: OperationKind,
    val subId: Int,
    val message: String,
    /** The runtime capability dump, or null for operations whose success does not depend on one. */
    val probe: String?,
    val isError: Boolean,
    val simLayerFailed: Boolean,
    val countryLayerFailed: Boolean,
    /**
     * The apply landed and IMS was observed unregistered during the post-apply window.
     *
     * Not a failure — every layer the user asked for is in place — but the single most consequential
     * thing the operation can report, so it is lifted out of the detail text and into the headline
     * rather than left for whoever expands the report.
     */
    val imsUnregistered: Boolean = false,
    /** The overrides were removed, but automatic IMS recovery was skipped, unavailable or unconfirmed. */
    val imsRecoveryUnconfirmed: Boolean = false,
) {
    /**
     * Whether the privileged service ran far enough to report on the layers at all.
     *
     * Layer flags may only be reconciled when this is true. A failure *before* the service runs — a
     * dead binder, a refused bind, an exception on the way in — carries no per-layer marker, and
     * "no failure marker" must not be read as "that layer succeeded". Read that way, a restore that
     * never happened clears the flags and the tool forgets a live override is still on the SIM.
     */
    val reportedLayers: Boolean
        get() = !isError || simLayerFailed || countryLayerFailed
}

/**
 * Runs the privileged operations end to end: hold up the instrumented process, wait for Shizuku, get
 * permission, bind the shell-identity service, call it, then reconcile the local layer flags.
 */
class OverrideRepository(
    private val shizuku: ShizukuController,
    private val host: InstrumentationHost,
    private val store: OverrideStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Reported as the operation walks through its preconditions, so the UI can narrate the wait. */
    enum class Stage { HOST, SHIZUKU, PERMISSION, BINDING, RUNNING }

    suspend fun apply(
        sim: SimInfo,
        target: RegionTarget,
        layers: LayerSelection,
        onStage: (Stage) -> Unit,
    ): OperationOutcome {
        if (store.hasLegacyUnboundState(sim.subId)) {
            throw OverrideException(R.string.error_sim_identity_unbound)
        }
        val service = connect(onStage)
        val fingerprint = withContext(io) {
            runCatching { service.readSimFingerprint(sim.subId) }.getOrNull()
        }
        if (store.hasFingerprint(sim.subId) && fingerprint == null) {
            throw OverrideException(R.string.error_sim_identity_unavailable)
        }
        if (store.fingerprintWasUnavailable(sim.subId) && fingerprint != null) {
            throw OverrideException(R.string.error_sim_identity_unbound)
        }
        val replacedCard = store.prepareForApply(sim.subId, fingerprint)
        // Bind the recovery state before writing any snapshot. Without this marker, a process death or
        // validation failure between the first snapshot and the old marker position would make state
        // created by this version indistinguishable from an unsafe legacy migration on the next run.
        if (fingerprint == null) store.markFingerprintUnavailable(sim.subId)

        // Capture every real field before the first layer, regardless of which layer this operation
        // selects. Otherwise Network-first can make a later Country apply save the fake country as the
        // original. A changed card intentionally starts from what the replacement reports now.
        val realNumeric = if (replacedCard) sim.operatorNumeric else sim.realOperatorNumeric
        val realName = if (replacedCard) sim.operatorName else sim.realOperatorName
        val realCountry = if (replacedCard) sim.countryIso else sim.realCountryIso
        store.captureSimSnapshot(sim.subId, realNumeric, realName)
        store.captureCountrySnapshot(
            sim.subId,
            realCountry.ifEmpty { countryIsoForMccMnc(realNumeric) },
        )
        if (layers.simIdentity && !store.hasSimSnapshot(sim.subId)) {
            throw OverrideException(R.string.error_real_sim_identity_unavailable)
        }
        // The display name is the one original this app cannot read for itself — it needs
        // READ_PHONE_STATE, which only the shell service holds. So it is captured here: after the
        // service exists, and still before a single byte of override has been written.
        if (replacedCard || !sim.flags.any) captureDisplayName(service, sim.subId)
        // Synchronous and immediately before the Binder write. If either process dies after a layer
        // lands but before the long report returns, the next launch still errs toward Restore.
        store.markApplyPending(sim.subId, layers.simIdentity, layers.appCountry)
        onStage(Stage.RUNNING)
        return runOperation(
            kind = OperationKind.APPLY,
            subId = sim.subId,
            service = service,
            reconcile = { outcome ->
                if (outcome.reportedLayers) {
                    val simSucceeded = layers.simIdentity && !outcome.simLayerFailed
                    val countrySucceeded = layers.appCountry && !outcome.countryLayerFailed
                    store.finishApply(
                        subId = sim.subId,
                        simAttempted = layers.simIdentity,
                        simSucceeded = simSucceeded,
                        countryAttempted = layers.appCountry,
                        countrySucceeded = countrySucceeded,
                    )
                }
            },
        ) {
            it.applyRegionOverride(
                sim.subId,
                target.mccMnc,
                target.testImsi,
                target.carrierName,
                target.countryIso.lowercase(Locale.ROOT),
                layers.simIdentity,
                layers.appCountry,
                layers.carrierNameOverride,
            )
        }
    }

    suspend fun restore(
        sim: SimInfo,
        restoreSimIdentity: Boolean,
        clearAppCountry: Boolean,
        onStage: (Stage) -> Unit,
    ): OperationOutcome {
        if (store.hasLegacyUnboundState(sim.subId)) {
            throw OverrideException(R.string.error_sim_identity_unbound)
        }
        val service = connect(onStage)
        val fingerprint = withContext(io) {
            runCatching { service.readSimFingerprint(sim.subId) }.getOrNull()
        }
        if (store.hasFingerprint(sim.subId) && fingerprint == null) {
            throw OverrideException(R.string.error_sim_identity_unavailable)
        }
        if (store.fingerprintWasUnavailable(sim.subId) && fingerprint != null) {
            throw OverrideException(R.string.error_sim_identity_unbound)
        }
        if (!store.fingerprintMatches(sim.subId, fingerprint)) {
            throw OverrideException(R.string.error_sim_changed)
        }
        val snapshot = store.snapshot(sim.subId)
        val flagsBefore = store.flags(sim.subId)
        onStage(Stage.RUNNING)
        return runOperation(
            kind = OperationKind.RESTORE,
            subId = sim.subId,
            service = service,
            reconcile = { outcome ->
                // Guarded: a binder-level failure reports no layer at all, and clearing the flags on
                // that would make the tool forget live overrides are still on the SIM.
                if (outcome.reportedLayers) {
                    store.setFlags(
                        subId = sim.subId,
                        simIdentity = if (restoreSimIdentity && !outcome.simLayerFailed) false else null,
                        appCountry = if (clearAppCountry && !outcome.countryLayerFailed) false else null,
                    )
                }
            },
        ) {
            it.restoreTransient(
                sim.subId,
                snapshot.mccMnc,
                snapshot.operatorName,
                // With no snapshot, fall back to what the SIM reports now; the instrumentation uses this
                // only to warm Samsung's country cache before dropping the override.
                snapshot.countryIso ?: sim.realCountryIso,
                // No fallback for these two. An un-captured display name is left alone rather than
                // guessed at, because the only value available to guess with is the overridden one.
                snapshot.displayName,
                snapshot.displayNameSource,
                networkMayBeLive(flagsBefore),
                restoreSimIdentity,
                clearAppCountry,
            )
        }
    }

    suspend fun clearAllCarrierConfig(sim: SimInfo, onStage: (Stage) -> Unit): OperationOutcome {
        val service = connect(onStage)
        onStage(Stage.RUNNING)
        return runOperation(
            kind = OperationKind.CLEAR_ALL,
            subId = sim.subId,
            service = service,
            reconcile = { outcome ->
                if (!outcome.isError) {
                    store.setFlags(subId = sim.subId, appCountry = false)
                }
            },
        ) {
            it.clearAllCarrierConfigOverrides(sim.subId)
        }
    }

    /**
     * Stops, optionally wipes, and optionally relaunches the apps whose cached region this tool is
     * trying to change. Touches no telephony state, so it deliberately does not reconcile layer flags.
     */
    suspend fun refreshApps(
        packages: List<String>,
        wipeMode: WipeMode,
        relaunch: Boolean,
        onStage: (Stage) -> Unit,
    ): OperationOutcome {
        val service = connect(onStage)
        onStage(Stage.RUNNING)
        // No capability probe here: it would start the CarrierConfig instrumentation, and nothing this
        // operation does depends on a telephony signature. Stopping an app cannot be unsupported.
        return runOperation(
            kind = OperationKind.REFRESH_APPS,
            subId = -1,
            service = service,
            withProbe = false,
        ) {
            it.refreshTargetApps(packages.toTypedArray(), wipeMode.wireValue, relaunch)
        }
    }

    /**
     * Records the subscription's display name and name source, unless one is already on file.
     *
     * Failures are swallowed deliberately. This is the cosmetic half of restore, and a build that will
     * not surrender the name should cost the user a wrong SIM label at worst — never a refused apply.
     * The store is write-once, so a second apply over a live override cannot replace a good capture
     * with the overridden name.
     */
    private suspend fun captureDisplayName(service: ICarrierOverrideService, subId: Int) {
        if (store.hasDisplayNameSnapshot(subId)) return
        val captured = withContext(io) {
            runCatching { service.readDisplayName(subId) }.getOrNull()
        } ?: return
        store.captureDisplayNameSnapshot(
            subId = subId,
            displayName = captured.getOrNull(0),
            source = captured.getOrNull(1)?.toIntOrNull() ?: OverrideStore.DISPLAY_NAME_SOURCE_NONE,
        )
    }

    private suspend fun connect(onStage: (Stage) -> Unit): ICarrierOverrideService {
        onStage(Stage.HOST)
        host.ensureBound()

        onStage(Stage.SHIZUKU)
        shizuku.awaitBinder()
        shizuku.requireModernApi()

        onStage(Stage.PERMISSION)
        if (!shizuku.requestPermission()) {
            throw OverrideException(R.string.error_shizuku_not_granted)
        }

        onStage(Stage.BINDING)
        return shizuku.bindService()
    }

    /**
     * Runs one privileged call and records what it did.
     *
     * `NonCancellable` is load-bearing, not caution. The privileged call is an uninterruptible binder
     * round trip: cancelling the coroutine never stops the write, it only discards the answer. Under a
     * plain `withContext(io)` a cancellation arriving mid-call — the screen being closed is enough,
     * since `onCleared` cancels the job — threw away the outcome *after* the override had landed on the
     * device, so nothing was ever written to the store and the tool forgot a live override existed.
     *
     * [reconcile] runs inside that same region for the same reason. Recording what landed has to be
     * exactly as uncancellable as the landing was, and a caller doing it afterwards is a caller that
     * can be cancelled in between.
     */
    private suspend fun runOperation(
        kind: OperationKind,
        subId: Int,
        service: ICarrierOverrideService,
        withProbe: Boolean = true,
        reconcile: (OperationOutcome) -> Unit = {},
        call: (ICarrierOverrideService) -> String,
    ): OperationOutcome = withContext(io + NonCancellable) {
        val probe = if (!withProbe) null else {
            runCatching { service.inspectRuntime() }.getOrElse { "<binder call failed>" }
        }
        val message = try {
            call(service)
        } catch (throwable: Throwable) {
            "ERROR: ${throwable.javaClass.name}: ${throwable.message.orEmpty()}"
        }
        val outcome = OperationOutcome(
            kind = kind,
            subId = subId,
            message = message,
            probe = probe,
            isError = message.startsWith(ERROR_PREFIX) || message.contains("\n$ERROR_PREFIX"),
            simLayerFailed = LAYER_FAILURE_MARKERS.sim.any(message::contains),
            countryLayerFailed = LAYER_FAILURE_MARKERS.country.any(message::contains),
            imsUnregistered = message.contains(CarrierOverrideUserService.VOICE_STOPPED),
            imsRecoveryUnconfirmed = message.contains(
                CarrierOverrideUserService.IMS_RECOVERY_UNCONFIRMED
            ),
        )
        reconcile(outcome)
        outcome
    }

    private companion object {
        const val ERROR_PREFIX = "ERROR:"

        /** The very constants `CarrierOverrideUserService` emits when one layer throws. */
        val LAYER_FAILURE_MARKERS = Markers(
            sim = listOf(
                CarrierOverrideUserService.SIM_LAYER_APPLY_FAILED,
                CarrierOverrideUserService.SIM_LAYER_RESTORE_FAILED,
            ),
            country = listOf(
                CarrierOverrideUserService.COUNTRY_LAYER_APPLY_FAILED,
                CarrierOverrideUserService.COUNTRY_LAYER_RESTORE_FAILED,
            ),
        )
    }

    private data class Markers(val sim: List<String>, val country: List<String>)
}
