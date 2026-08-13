package com.riteldevelopment.carriertestoverride.data

import com.riteldevelopment.carriertestoverride.CarrierOverrideUserService
import com.riteldevelopment.carriertestoverride.ICarrierOverrideService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
)

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
        // Capture the SIM's real identity before the first override, never after.
        if (layers.simIdentity) {
            store.captureSimSnapshot(sim.subId, sim.operatorNumeric, sim.operatorName)
        }
        if (layers.appCountry) {
            store.captureCountrySnapshot(sim.subId, sim.countryIso)
        }
        val service = connect(onStage)
        onStage(Stage.RUNNING)
        return runOperation(OperationKind.APPLY, sim.subId, service) {
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
        }.also { outcome ->
            store.setFlags(
                subId = sim.subId,
                simIdentity = if (layers.simIdentity && !outcome.simLayerFailed) true else null,
                appCountry = if (layers.appCountry && !outcome.countryLayerFailed) true else null,
            )
        }
    }

    suspend fun restore(
        sim: SimInfo,
        restoreSimIdentity: Boolean,
        clearAppCountry: Boolean,
        onStage: (Stage) -> Unit,
    ): OperationOutcome {
        val snapshot = store.snapshot(sim.subId)
        val service = connect(onStage)
        onStage(Stage.RUNNING)
        return runOperation(OperationKind.RESTORE, sim.subId, service) {
            it.restoreTransient(
                sim.subId,
                snapshot.mccMnc,
                snapshot.operatorName,
                // With no snapshot, fall back to what the SIM reports now; the instrumentation uses this
                // only to warm Samsung's country cache before dropping the override.
                snapshot.countryIso ?: sim.countryIso,
                restoreSimIdentity,
                clearAppCountry,
            )
        }.also { outcome ->
            store.setFlags(
                subId = sim.subId,
                simIdentity = if (restoreSimIdentity && !outcome.simLayerFailed) false else null,
                appCountry = if (clearAppCountry && !outcome.countryLayerFailed) false else null,
            )
        }
    }

    suspend fun clearAllCarrierConfig(sim: SimInfo, onStage: (Stage) -> Unit): OperationOutcome {
        val service = connect(onStage)
        onStage(Stage.RUNNING)
        return runOperation(OperationKind.CLEAR_ALL, sim.subId, service) {
            it.clearAllCarrierConfigOverrides(sim.subId)
        }.also { outcome ->
            if (!outcome.isError) {
                store.setFlags(subId = sim.subId, appCountry = false)
            }
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

    private suspend fun connect(onStage: (Stage) -> Unit): ICarrierOverrideService {
        onStage(Stage.HOST)
        host.ensureBound()

        onStage(Stage.SHIZUKU)
        shizuku.awaitBinder()
        shizuku.requireModernApi()

        onStage(Stage.PERMISSION)
        if (!shizuku.requestPermission()) {
            throw OverrideException("Shizuku permission was not granted. Nothing was changed.")
        }

        onStage(Stage.BINDING)
        return shizuku.bindService()
    }

    private suspend fun runOperation(
        kind: OperationKind,
        subId: Int,
        service: ICarrierOverrideService,
        withProbe: Boolean = true,
        call: (ICarrierOverrideService) -> String,
    ): OperationOutcome = withContext(io) {
        val probe = if (!withProbe) null else {
            runCatching { service.inspectRuntime() }.getOrElse { "<binder call failed>" }
        }
        val message = try {
            call(service)
        } catch (throwable: Throwable) {
            "ERROR: ${throwable.javaClass.name}: ${throwable.message.orEmpty()}"
        }
        OperationOutcome(
            kind = kind,
            subId = subId,
            message = message,
            probe = probe,
            isError = message.startsWith(ERROR_PREFIX) || message.contains("\n$ERROR_PREFIX"),
            simLayerFailed = LAYER_FAILURE_MARKERS.sim.any(message::contains),
            countryLayerFailed = LAYER_FAILURE_MARKERS.country.any(message::contains),
        )
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
