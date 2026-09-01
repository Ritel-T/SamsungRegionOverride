package com.riteldevelopment.carriertestoverride.ui

import com.riteldevelopment.carriertestoverride.data.LayerSelection
import com.riteldevelopment.carriertestoverride.data.OperationKind
import com.riteldevelopment.carriertestoverride.data.OverrideRepository
import com.riteldevelopment.carriertestoverride.data.ShizukuStatus
import java.util.Locale

/** A small, stable vocabulary for the diagnostic text users may choose to share. */
enum class DiagnosticIms { REGISTERED, UNREGISTERED, UNCONFIRMED, UNKNOWN }

enum class DiagnosticShizuku {
    NOT_RUNNING,
    CONNECTED_NOT_GRANTED,
    CONNECTED_GRANTED,
    WRONG_UID,
    UNAVAILABLE,
}

enum class DiagnosticFailure {
    NONE,
    VALIDATION,
    SIM_LAYER,
    COUNTRY_LAYER,
    MULTIPLE_LAYERS,
    IMS,
    OPERATION,
    CANCELLED,
}

enum class DiagnosticRuntime { NOT_REQUESTED, AVAILABLE, UNAVAILABLE }

/** The operation context captured before a privileged call starts. */
internal data class DiagnosticContext(
    val operation: OperationKind,
    val slotIndex: Int? = null,
    val layers: LayerSelection? = null,
    val targetCountry: String? = null,
    val targetAppCount: Int = 0,
)

/**
 * Allow-listed support data. This object deliberately has no raw exception, SIM, subscription or package
 * fields, so serialising it cannot accidentally turn a private report into a phone dump.
 */
data class DiagnosticReport(
    val appVersion: String,
    val manufacturer: String,
    val model: String,
    val apiLevel: Int,
    val operation: OperationKind?,
    val slotIndex: Int?,
    val layers: String,
    val targetCountry: String?,
    val targetAppCount: Int,
    val result: ResultTone,
    val ims: DiagnosticIms,
    val shizuku: DiagnosticShizuku,
    val stage: OverrideRepository.Stage?,
    val failure: DiagnosticFailure,
    val runtime: DiagnosticRuntime,
    val exception: String? = null,
    val durationMs: Long? = null,
) {
    /**
     * Stable, reviewable text for clipboard/share actions. No URL query or automatic upload is involved.
     */
    fun toSafeText(): String = buildString {
        appendLine("SRO-DIAGNOSTIC/1")
        appendLine(
            "app=${safeToken(appVersion)}; device=${safeToken(manufacturer)}_${safeToken(model)}; " +
                "api=${apiLevel.coerceIn(1, 999)}"
        )
        appendLine(
            "operation=${operation?.name ?: "NONE"}; result=${result.name}; layers=$layers"
        )
        appendLine(
            "target_country=${safeCountry(targetCountry)}; slot=${slotIndex?.coerceIn(1, 2) ?: "UNKNOWN"}; " +
                "target_apps=${targetAppCount.coerceIn(0, 999)}"
        )
        appendLine(
            "ims=${ims.name}; shizuku=${shizuku.name}; stage=${stage?.name ?: "NONE"}"
        )
        appendLine("failure=${failure.name}; runtime=${runtime.name}")
        exception?.let { appendLine("exception=${safeToken(it)}") }
        durationMs?.let { appendLine("duration_ms=${it.coerceIn(0, 600_000)}") }
    }.trimEnd()

    companion object {
        fun shizuku(status: ShizukuStatus): DiagnosticShizuku = when (status) {
            ShizukuStatus.NotRunning -> DiagnosticShizuku.NOT_RUNNING
            is ShizukuStatus.Unavailable -> DiagnosticShizuku.UNAVAILABLE
            is ShizukuStatus.Connected -> when {
                !status.privileged -> DiagnosticShizuku.WRONG_UID
                !status.granted -> DiagnosticShizuku.CONNECTED_NOT_GRANTED
                else -> DiagnosticShizuku.CONNECTED_GRANTED
            }
        }

        fun layers(selection: LayerSelection?): String = when {
            selection == null -> "NONE"
            selection.simIdentity && selection.appCountry -> "NETWORK,COUNTRY"
            selection.simIdentity -> "NETWORK"
            selection.appCountry -> "COUNTRY"
            else -> "NONE"
        }

        fun runtime(probe: String?, requested: Boolean): DiagnosticRuntime = when {
            !requested -> DiagnosticRuntime.NOT_REQUESTED
            probe.isNullOrBlank() || probe == "<binder call failed>" -> DiagnosticRuntime.UNAVAILABLE
            else -> DiagnosticRuntime.AVAILABLE
        }

        private fun safeCountry(value: String?): String {
            val normalized = value.orEmpty().trim().lowercase(Locale.ROOT)
            return if (normalized.matches(Regex("[a-z]{2}"))) normalized else "UNKNOWN"
        }

        private fun safeToken(value: String): String = value
            .filter { it.code in 0x21..0x7E && (it.isLetterOrDigit() || it in "._-") }
            .take(64)
            .ifEmpty { "UNKNOWN" }
    }
}
