package com.riteldevelopment.carriertestoverride.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

internal data class ApplyLayerState(
    val live: Boolean,
    val pending: Boolean,
)

/** Pure transition used by the persisted apply journal and its local regression tests. */
internal fun resolveApplyLayer(
    previous: ApplyLayerState,
    attempted: Boolean,
    succeeded: Boolean,
): ApplyLayerState = when {
    !attempted -> previous
    succeeded -> ApplyLayerState(live = true, pending = false)
    else -> ApplyLayerState(live = previous.live, pending = true)
}

/** Legacy recovery data cannot be attached to a newly observed card without user confirmation. */
internal fun isLegacyUnboundState(
    hasFingerprint: Boolean,
    fingerprintWasUnavailable: Boolean,
    hasSnapshot: Boolean,
    hasLiveOrPendingFlag: Boolean,
): Boolean = !hasFingerprint &&
    !fingerprintWasUnavailable &&
    (hasSnapshot || hasLiveOrPendingFlag)

/**
 * Per-subscription record of what this tool changed, plus the few preferences that outlive a session.
 *
 * Three kinds of entries are kept:
 *
 *  * **Snapshots** — the SIM's own MCC/MNC, operator name, country ISO and subscription display name
 *    as they looked *before* the first override. Restore writes these back, so they must only ever be
 *    captured from a SIM that has not been overridden yet.
 *  * **Layer flags** — whether this tool currently believes each layer is applied. They drive the
 *    default restore selection and the per-SIM badges in the UI.
 *  * **Choices** — the recently applied regions and the chosen target apps. Losing these costs the
 *    user a re-pick, never a wrong write, so they carry none of the care the snapshots do.
 *
 * Key names are unchanged from the 2.x Java implementation so an in-place upgrade keeps its snapshots,
 * including the legacy `overridden_` flag that predates the two-layer split.
 */
class OverrideStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Snapshot of a subscription as it looked before this tool touched it. */
    data class Snapshot(
        val mccMnc: String?,
        val operatorName: String?,
        val countryIso: String?,
        val displayName: String?,
        /** The name source that went with [displayName], or [DISPLAY_NAME_SOURCE_NONE] if unknown. */
        val displayNameSource: Int,
    )

    /** Which layers this tool believes are currently applied to a subscription. */
    data class Flags(
        val simIdentity: Boolean,
        val appCountry: Boolean,
        val simPending: Boolean = false,
        val appCountryPending: Boolean = false,
    ) {
        val any: Boolean get() = simIdentity || appCountry
        val uncertain: Boolean get() = simPending || appCountryPending
    }

    fun snapshot(subId: Int): Snapshot = Snapshot(
        mccMnc = prefs.getString(originalNumericKey(subId), null),
        operatorName = prefs.getString(originalNameKey(subId), null),
        countryIso = prefs.getString(originalCountryKey(subId), null),
        displayName = prefs.getString(originalDisplayNameKey(subId), null),
        displayNameSource = prefs.getInt(originalDisplaySourceKey(subId), DISPLAY_NAME_SOURCE_NONE),
    )

    fun hasSimSnapshot(subId: Int): Boolean = prefs.contains(originalNumericKey(subId))

    fun hasCountrySnapshot(subId: Int): Boolean = prefs.contains(originalCountryKey(subId))

    fun hasDisplayNameSnapshot(subId: Int): Boolean =
        prefs.contains(originalDisplayNameKey(subId))

    /**
     * Records the SIM's real identity, but only the first time — a second call after an override is
     * already in place would immortalise the fake values as the "original" ones.
     */
    fun captureSimSnapshot(subId: Int, mccMnc: String, operatorName: String) {
        if (hasSimSnapshot(subId) || !mccMnc.matches(MCC_MNC)) return
        prefs.edit()
            .putString(originalNumericKey(subId), mccMnc)
            .putString(originalNameKey(subId), operatorName)
            .commitOrThrow("SIM identity snapshot")
    }

    fun captureCountrySnapshot(subId: Int, countryIso: String) {
        if (hasCountrySnapshot(subId) || !countryIso.matches(ISO)) return
        prefs.edit()
            .putString(originalCountryKey(subId), countryIso.lowercase(Locale.ROOT))
            .commitOrThrow("country snapshot")
    }

    /**
     * Records the subscription's real display name and the source that set it.
     *
     * Write-once for the same reason as the identity snapshot: the app country layer's name override
     * lands in the subscription database, so a capture taken after one has been applied would record
     * the foreign operator as the original and make restore permanent instead of undoing it.
     *
     * A name with no usable source is stored anyway — [Snapshot.displayNameSource] carries the
     * shortfall through to restore, which then declines to write rather than guessing a priority.
     */
    fun captureDisplayNameSnapshot(subId: Int, displayName: String?, source: Int) {
        if (hasDisplayNameSnapshot(subId) || displayName.isNullOrBlank()) return
        prefs.edit()
            .putString(originalDisplayNameKey(subId), displayName)
            .putInt(originalDisplaySourceKey(subId), source)
            .commitOrThrow("display-name snapshot")
    }

    fun flags(subId: Int): Flags {
        val simPending = prefs.getBoolean(simPendingKey(subId), false)
        val countryPending = prefs.getBoolean(countryPendingKey(subId), false)
        return Flags(
            // `overridden_` is the pre-2.0 single-layer flag. Treat it as the SIM identity layer.
            // Pending is also treated as applied: if the process died after the framework write but
            // before the Binder reply, Restore must err toward undoing a change rather than hiding it.
            simIdentity = prefs.getBoolean(simLayerKey(subId), false) ||
                prefs.getBoolean(legacyOverriddenKey(subId), false) || simPending,
            appCountry = prefs.getBoolean(countryLayerKey(subId), false) || countryPending,
            simPending = simPending,
            appCountryPending = countryPending,
        )
    }

    /** Synchronously journals the layers immediately before the first privileged write. */
    fun markApplyPending(subId: Int, simIdentity: Boolean, appCountry: Boolean) {
        prefs.edit().apply {
            if (simIdentity) putBoolean(simPendingKey(subId), true)
            if (appCountry) putBoolean(countryPendingKey(subId), true)
        }.commitOrThrow("pending apply journal")
    }

    /** Resolves only outcomes that are certain; a failed reply leaves that layer pending. */
    fun finishApply(
        subId: Int,
        simAttempted: Boolean,
        simSucceeded: Boolean,
        countryAttempted: Boolean,
        countrySucceeded: Boolean,
    ) {
        val sim = resolveApplyLayer(
            previous = ApplyLayerState(
                live = prefs.getBoolean(simLayerKey(subId), false) ||
                    prefs.getBoolean(legacyOverriddenKey(subId), false),
                pending = prefs.getBoolean(simPendingKey(subId), false),
            ),
            attempted = simAttempted,
            succeeded = simSucceeded,
        )
        val country = resolveApplyLayer(
            previous = ApplyLayerState(
                live = prefs.getBoolean(countryLayerKey(subId), false),
                pending = prefs.getBoolean(countryPendingKey(subId), false),
            ),
            attempted = countryAttempted,
            succeeded = countrySucceeded,
        )
        prefs.edit().apply {
            if (simAttempted) {
                putBoolean(simLayerKey(subId), sim.live)
                putBoolean(legacyOverriddenKey(subId), sim.live)
                if (sim.pending) putBoolean(simPendingKey(subId), true)
                else remove(simPendingKey(subId))
            }
            if (countryAttempted) {
                putBoolean(countryLayerKey(subId), country.live)
                if (country.pending) putBoolean(countryPendingKey(subId), true)
                else remove(countryPendingKey(subId))
            }
        }.commitOrThrow("apply result")
    }

    /**
     * Updates layer flags after an operation. A `null` argument means "that layer was not part of this
     * operation, leave its flag alone" — a partial failure must not clear a flag for a layer that is
     * still applied.
     */
    fun setFlags(subId: Int, simIdentity: Boolean? = null, appCountry: Boolean? = null) {
        val editor = prefs.edit()
        if (simIdentity != null) {
            editor.putBoolean(simLayerKey(subId), simIdentity)
                .putBoolean(legacyOverriddenKey(subId), simIdentity)
                .remove(simPendingKey(subId))
        }
        if (appCountry != null) {
            editor.putBoolean(countryLayerKey(subId), appCountry)
                .remove(countryPendingKey(subId))
        }
        editor.commitOrThrow("layer flags")
    }

    // ---------------------------------------------------------------- subscription identity and session

    fun hasFingerprint(subId: Int): Boolean = prefs.contains(fingerprintKey(subId))

    fun fingerprintWasUnavailable(subId: Int): Boolean =
        prefs.getBoolean(fingerprintUnavailableKey(subId), false)

    /** True for pre-fingerprint recovery state whose physical card can no longer be proven. */
    fun hasLegacyUnboundState(subId: Int): Boolean = isLegacyUnboundState(
        hasFingerprint = hasFingerprint(subId),
        fingerprintWasUnavailable = fingerprintWasUnavailable(subId),
        hasSnapshot = hasSimSnapshot(subId) ||
            hasCountrySnapshot(subId) ||
            hasDisplayNameSnapshot(subId),
        hasLiveOrPendingFlag = prefs.getBoolean(simLayerKey(subId), false) ||
            prefs.getBoolean(countryLayerKey(subId), false) ||
            prefs.getBoolean(legacyOverriddenKey(subId), false) ||
            prefs.getBoolean(simPendingKey(subId), false) ||
            prefs.getBoolean(countryPendingKey(subId), false),
    )

    /**
     * Binds saved telephony state to a physical card. A changed fingerprint means Android reused a
     * subId for another SIM, so every per-subscription snapshot and flag is discarded before apply.
     */
    fun prepareForApply(subId: Int, fingerprint: String?): Boolean {
        check(!hasLegacyUnboundState(subId)) {
            "Legacy recovery state is not bound to a verified SIM"
        }
        if (fingerprint.isNullOrBlank()) return false
        val stored = prefs.getString(fingerprintKey(subId), null)
        if (stored == null) {
            prefs.edit().putString(fingerprintKey(subId), fingerprint)
                .remove(fingerprintUnavailableKey(subId))
                .commitOrThrow("SIM fingerprint")
            return false
        }
        if (stored == fingerprint) return false
        clearSubscriptionState(subId, replacementFingerprint = fingerprint)
        return true
    }

    fun markFingerprintUnavailable(subId: Int) {
        if (hasFingerprint(subId) || fingerprintWasUnavailable(subId)) return
        prefs.edit().putBoolean(fingerprintUnavailableKey(subId), true)
            .commitOrThrow("unavailable SIM fingerprint marker")
    }

    /** Null is accepted only when this version recorded that card identity was unavailable. */
    fun fingerprintMatches(subId: Int, fingerprint: String?): Boolean {
        if (hasLegacyUnboundState(subId)) return false
        if (fingerprintWasUnavailable(subId)) return fingerprint.isNullOrBlank()
        val stored = prefs.getString(fingerprintKey(subId), null)
        if (stored == null) {
            if (fingerprint.isNullOrBlank()) return true
            prefs.edit().putString(fingerprintKey(subId), fingerprint)
                .commitOrThrow("SIM fingerprint")
            return true
        }
        return !fingerprint.isNullOrBlank() && stored == fingerprint
    }

    fun sessionPackages(subId: Int): List<String> = prefs
        .getString(sessionPackagesKey(subId), null)
        .orEmpty()
        .split(LIST_SEPARATOR)
        .filter { it.isNotBlank() }

    /** Keeps every app touched during this live disguise, even if the picker changes before restore. */
    fun rememberSessionPackages(subId: Int, packages: List<String>) {
        val combined = (sessionPackages(subId) + packages)
            .filter { it.isNotBlank() }
            .distinct()
        prefs.edit().putString(sessionPackagesKey(subId), combined.joinToString(LIST_SEPARATOR))
            .commitOrThrow("session app list")
    }

    fun clearSessionPackages(subId: Int) {
        prefs.edit().remove(sessionPackagesKey(subId)).commitOrThrow("session app list")
    }

    private fun clearSubscriptionState(subId: Int, replacementFingerprint: String? = null) {
        prefs.edit()
            .remove(originalNumericKey(subId))
            .remove(originalNameKey(subId))
            .remove(originalCountryKey(subId))
            .remove(originalDisplayNameKey(subId))
            .remove(originalDisplaySourceKey(subId))
            .remove(simLayerKey(subId))
            .remove(countryLayerKey(subId))
            .remove(legacyOverriddenKey(subId))
            .remove(simPendingKey(subId))
            .remove(countryPendingKey(subId))
            .remove(fingerprintUnavailableKey(subId))
            .remove(sessionPackagesKey(subId))
            .apply {
                if (replacementFingerprint == null) remove(fingerprintKey(subId))
                else putString(fingerprintKey(subId), replacementFingerprint)
            }
            .commitOrThrow("subscription state")
    }

    private fun SharedPreferences.Editor.commitOrThrow(label: String) {
        if (!commit()) throw IllegalStateException("Could not persist $label")
    }

    // ---------------------------------------------------------------- choices

    /**
     * Regions applied before, newest first.
     *
     * Ordered, so a delimited string rather than a `StringSet`: the whole value of the list is that
     * what you reached for last sits where your thumb already is.
     */
    fun recentPresetIds(): List<String> = prefs.getString(RECENT_PRESETS, null)
        .orEmpty()
        .split(LIST_SEPARATOR)
        .filter { it.isNotBlank() }

    /** Moves [id] to the front, deduplicating, and drops anything past [MAX_RECENT_PRESETS]. */
    fun rememberPreset(id: String) {
        if (id.isBlank()) return
        val updated = (listOf(id) + recentPresetIds().filter { it != id }).take(MAX_RECENT_PRESETS)
        prefs.edit().putString(RECENT_PRESETS, updated.joinToString(LIST_SEPARATOR)).apply()
    }

    /**
     * The apps the user chose to refresh, or null when they have never chosen and the built-in
     * defaults apply.
     *
     * Null and empty are genuinely different: a user who deselects every target is asking for nothing
     * to be stopped, and answering that by silently stopping the defaults would be the tool ignoring
     * them. Presence of the key is what separates the two, so an empty list is stored as an empty
     * string rather than by removing the entry.
     */
    fun targetPackages(): List<String>? {
        if (!prefs.contains(TARGET_PACKAGES)) return null
        return prefs.getString(TARGET_PACKAGES, "")
            .orEmpty()
            .split(LIST_SEPARATOR)
            .filter { it.isNotBlank() }
    }

    fun setTargetPackages(packages: List<String>) {
        prefs.edit()
            .putString(TARGET_PACKAGES, packages.filter { it.isNotBlank() }.joinToString(LIST_SEPARATOR))
            .apply()
    }

    /** Forgets the user's choice so the built-in defaults apply again. */
    fun clearTargetPackages() {
        prefs.edit().remove(TARGET_PACKAGES).apply()
    }

    /**
     * Whether the notification permission has ever been asked for.
     *
     * Persisted rather than kept in memory so a refusal survives the process. The ongoing notice is
     * useful but optional, and re-asking on every cold start would be the app pestering the user for
     * something it can do without — the system stops showing the dialog after two refusals in any case,
     * so the extra prompts would achieve nothing but noise.
     */
    fun notificationPromptShown(): Boolean = prefs.getBoolean(NOTIFICATION_PROMPT_SHOWN, false)

    fun markNotificationPromptShown() {
        prefs.edit().putBoolean(NOTIFICATION_PROMPT_SHOWN, true).apply()
    }

    companion object {
        /**
         * No display name source was captured. Mirrors the sentinel the AIDL surface documents and
         * `TelephonyBridge.DISPLAY_NAME_SOURCE_NONE` enforces; that class is package-private to the
         * Java side, so the value is restated rather than imported.
         */
        const val DISPLAY_NAME_SOURCE_NONE = -1

        private const val PREFS = "carrier_override_state"

        /** Safe in both lists: package names and preset ids are `[A-Za-z0-9_.@]` and never contain it. */
        private const val LIST_SEPARATOR = ","
        private const val RECENT_PRESETS = "recent_presets"
        private const val TARGET_PACKAGES = "target_packages"
        private const val NOTIFICATION_PROMPT_SHOWN = "notification_prompt_shown"
        private const val MAX_RECENT_PRESETS = 6

        private val MCC_MNC = Regex("[0-9]{5,6}")
        private val ISO = Regex("[A-Za-z]{2}")

        private fun originalNumericKey(subId: Int) = "original_numeric_$subId"
        private fun originalNameKey(subId: Int) = "original_name_$subId"
        private fun originalCountryKey(subId: Int) = "original_country_$subId"
        private fun originalDisplayNameKey(subId: Int) = "original_display_name_$subId"
        private fun originalDisplaySourceKey(subId: Int) = "original_display_source_$subId"
        private fun simLayerKey(subId: Int) = "sim_layer_applied_$subId"
        private fun countryLayerKey(subId: Int) = "country_layer_applied_$subId"
        private fun legacyOverriddenKey(subId: Int) = "overridden_$subId"
        private fun simPendingKey(subId: Int) = "sim_layer_pending_$subId"
        private fun countryPendingKey(subId: Int) = "country_layer_pending_$subId"
        private fun fingerprintKey(subId: Int) = "sim_fingerprint_$subId"
        private fun fingerprintUnavailableKey(subId: Int) = "sim_fingerprint_unavailable_$subId"
        private fun sessionPackagesKey(subId: Int) = "session_packages_$subId"
    }
}
