package com.riteldevelopment.carriertestoverride.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

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
    ) {
        val any: Boolean get() = simIdentity || appCountry
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
            .apply()
    }

    fun captureCountrySnapshot(subId: Int, countryIso: String) {
        if (hasCountrySnapshot(subId) || !countryIso.matches(ISO)) return
        prefs.edit()
            .putString(originalCountryKey(subId), countryIso.lowercase(Locale.ROOT))
            .apply()
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
            .apply()
    }

    fun flags(subId: Int): Flags = Flags(
        // `overridden_` is the pre-2.0 single-layer flag. Treat it as the SIM identity layer.
        simIdentity = prefs.getBoolean(simLayerKey(subId), false) ||
            prefs.getBoolean(legacyOverriddenKey(subId), false),
        appCountry = prefs.getBoolean(countryLayerKey(subId), false),
    )

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
        }
        if (appCountry != null) {
            editor.putBoolean(countryLayerKey(subId), appCountry)
        }
        editor.apply()
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
    }
}
