package com.riteldevelopment.carriertestoverride.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/**
 * Per-subscription record of what this tool changed.
 *
 * Two kinds of entries are kept:
 *
 *  * **Snapshots** — the SIM's own MCC/MNC, operator name and country ISO as they looked *before* the
 *    first override. Restore writes these back, so they must only ever be captured from a SIM that has
 *    not been overridden yet.
 *  * **Layer flags** — whether this tool currently believes each layer is applied. They drive the
 *    default restore selection and the per-SIM badges in the UI.
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
    )

    fun hasSimSnapshot(subId: Int): Boolean = prefs.contains(originalNumericKey(subId))

    fun hasCountrySnapshot(subId: Int): Boolean = prefs.contains(originalCountryKey(subId))

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

    private companion object {
        const val PREFS = "carrier_override_state"

        val MCC_MNC = Regex("[0-9]{5,6}")
        val ISO = Regex("[A-Za-z]{2}")

        fun originalNumericKey(subId: Int) = "original_numeric_$subId"
        fun originalNameKey(subId: Int) = "original_name_$subId"
        fun originalCountryKey(subId: Int) = "original_country_$subId"
        fun simLayerKey(subId: Int) = "sim_layer_applied_$subId"
        fun countryLayerKey(subId: Int) = "country_layer_applied_$subId"
        fun legacyOverriddenKey(subId: Int) = "overridden_$subId"
    }
}
