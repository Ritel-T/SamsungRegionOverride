package com.riteldevelopment.carriertestoverride.data

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.annotation.StringRes
import com.riteldevelopment.carriertestoverride.R

/**
 * One selectable SIM/modem slot that currently carries a valid subscription.
 *
 * The four identity fields are what the subscription reports *right now*, which is the disguise once a
 * layer is live. [original] is what it reported before this tool first touched it, so the two together
 * are the whole "real versus disguise" comparison the screen is built around.
 */
data class SimInfo(
    val slotIndex: Int,
    val subId: Int,
    val simState: Int,
    val operatorNumeric: String,
    val operatorName: String,
    val countryIso: String,
    val flags: OverrideStore.Flags,
    val original: OverrideStore.Snapshot,
) {
    /** Only a fully loaded SIM exposes the IccRecords the SIM identity layer rewrites. */
    val isReady: Boolean get() = simState == TelephonyManager.SIM_STATE_READY

    val stateLabel: String get() = simStateName(simState)

    val displayName: String
        get() = "SIM ${slotIndex + 1}"

    /** The SIM's true MCC/MNC: the snapshot if one layer is rewriting it, otherwise what it reports. */
    val realOperatorNumeric: String get() = original.mccMnc ?: operatorNumeric

    val realOperatorName: String get() = original.operatorName ?: operatorName

    /**
     * Falls back to the MCC when the platform reports no country at all, which some SIMs do. The
     * operator numeric is present in that case and carries the same fact, so there is no reason to
     * show a blank where the country is known.
     */
    val realCountryIso: String
        get() {
            original.countryIso?.takeIf { it.isNotBlank() }?.let { return it }
            // A Network-first session can make getSimCountryIso report the fake MCC's country before
            // the Country layer is ever selected. In that state the saved real MCC is the trustworthy
            // source; using the currently reported ISO would snapshot the disguise as the original.
            if (simLayerLive) {
                countryIsoForMccMnc(realOperatorNumeric).takeIf { it.isNotBlank() }?.let { return it }
            }
            return countryIso.ifEmpty { countryIsoForMccMnc(realOperatorNumeric) }
        }

    /**
     * The country this subscription is currently claiming to be in.
     *
     * Not simply [countryIso]. The two layers assert a region in different ways: the country layer
     * writes an ISO code the platform hands straight back, while the SIM identity layer rewrites only
     * MCC/MNC — so after a network-only apply `getSimCountryIso()` still returns the real country, and
     * reading it would have the screen and the notification announce "23430" and "CN" side by side.
     * That is not an identity anyone is presenting. MCC 234 *is* the United Kingdom, it is what the
     * region checks this tool exists to influence actually read, and a network-only override has
     * therefore already achieved what it was applied for.
     *
     * The explicit override wins where there is one: it is the more specific assertion, and it is what
     * `getSimCountryIso()` reports to anything that asks.
     */
    val disguiseCountryIso: String
        get() = if (countryLayerLive) {
            countryIso
        } else {
            countryIsoForMccMnc(operatorNumeric).ifEmpty { countryIso }
        }

    /**
     * Whether each layer is *still* rewriting this subscription, rather than merely having been applied.
     *
     * The stored flag alone cannot answer this. Every override here is transient, so a reboot silently
     * undoes all of them while the flag stays set — and the screen's headline block, the per-layer
     * badges and the ongoing notification all key off this, so a flag believed blindly would have the
     * tool insisting the phone is disguised when it has been telling the truth since the last restart.
     *
     * So the flag is confirmed against the SIM: a layer counts as live only if what the subscription
     * reports still differs from what was captured before the first override. With no snapshot there is
     * nothing to compare and the flag is trusted, which errs toward warning about an override that is
     * already gone rather than staying quiet about one that is not.
     */
    val simLayerLive: Boolean get() = flags.simIdentity && diverged(original.mccMnc, operatorNumeric)

    val countryLayerLive: Boolean get() = flags.appCountry && diverged(original.countryIso, countryIso)

    /** True when this subscription is presenting something other than its own identity. */
    val disguised: Boolean get() = simLayerLive || countryLayerLive

    private fun diverged(captured: String?, reported: String): Boolean =
        captured == null || !captured.equals(reported, ignoreCase = true)

    companion object {
        @StringRes
        fun simStateNameRes(state: Int): Int = when (state) {
            TelephonyManager.SIM_STATE_READY -> R.string.sim_state_ready
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> R.string.sim_state_pin
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> R.string.sim_state_puk
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> R.string.sim_state_locked
            TelephonyManager.SIM_STATE_NOT_READY -> R.string.sim_state_not_ready
            TelephonyManager.SIM_STATE_PERM_DISABLED -> R.string.sim_state_disabled
            TelephonyManager.SIM_STATE_CARD_IO_ERROR -> R.string.sim_state_io_error
            TelephonyManager.SIM_STATE_CARD_RESTRICTED -> R.string.sim_state_restricted
            else -> R.string.unknown
        }

        fun simStateName(state: Int): String = when (state) {
            TelephonyManager.SIM_STATE_READY -> "READY"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "LOCKED"
            TelephonyManager.SIM_STATE_NOT_READY -> "NOT READY"
            TelephonyManager.SIM_STATE_PERM_DISABLED -> "DISABLED"
            TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "IO ERROR"
            TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "RESTRICTED"
            else -> state.toString()
        }
    }
}

/**
 * Result of enumerating SIMs, so the UI can tell "no SIM" apart from "reading SIMs failed".
 *
 * [slotCount] is what the *hardware* supports, not how many subscriptions were found — the selector
 * draws an empty placeholder for a slot that exists but is unused, so the layout does not reshuffle
 * when a second SIM appears.
 */
sealed interface SimScan {
    data class Success(val sims: List<SimInfo>, val slotCount: Int) : SimScan
    data class Failure(val message: String) : SimScan
}

/**
 * Reads the *currently reported* identity of every usable subscription.
 *
 * Everything here is deliberately re-read on demand rather than cached: after an override is applied the
 * whole point is that these values change, and the UI polls this to show the change landing.
 */
class SimRepository(context: Context, private val store: OverrideStore) {

    private val appContext = context.applicationContext

    fun scan(): SimScan = try {
        val manager = appContext.getSystemService(TelephonyManager::class.java)
            ?: return SimScan.Failure(appContext.getString(R.string.error_no_telephony_manager))
        val subscriptions = appContext.getSystemService(SubscriptionManager::class.java)
            ?: return SimScan.Failure(appContext.getString(R.string.error_no_subscription_manager))
        val declared = supportedSlotCount(manager)
        val sims = (0 until declared).mapNotNull { slot -> readSlot(manager, subscriptions, slot) }
        // A subscription reported at a slot beyond the declared count would otherwise be dropped
        // silently, so the count grows to cover it rather than the SIM disappearing from the screen.
        val highestUsed = (sims.maxOfOrNull { it.slotIndex } ?: -1) + 1
        SimScan.Success(sims, maxOf(declared, highestUsed))
    } catch (throwable: Throwable) {
        SimScan.Failure(
            appContext.getString(
                R.string.error_read_sim_state,
                throwable.javaClass.simpleName,
                throwable.message.orEmpty(),
            )
        )
    }

    private fun readSlot(
        manager: TelephonyManager,
        subscriptions: SubscriptionManager,
        slot: Int,
    ): SimInfo? {
        val subId = subscriptionId(subscriptions, slot)
        val state = manager.getSimState(slot)
        if (!SubscriptionManager.isValidSubscriptionId(subId) ||
            state == TelephonyManager.SIM_STATE_ABSENT ||
            state == TelephonyManager.SIM_STATE_UNKNOWN
        ) {
            return null
        }
        val forSub = manager.createForSubscriptionId(subId)
        return SimInfo(
            slotIndex = slot,
            subId = subId,
            simState = state,
            operatorNumeric = forSub.simOperator.orEmpty(),
            operatorName = forSub.simOperatorName.orEmpty(),
            countryIso = forSub.simCountryIso.orEmpty(),
            flags = store.flags(subId),
            original = store.snapshot(subId),
        )
    }

    @Suppress("DEPRECATION")
    private fun subscriptionId(manager: SubscriptionManager, slot: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            SubscriptionManager.getSubscriptionId(slot)
        } else {
            manager.getSubscriptionIds(slot)?.firstOrNull()
                ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }

    /**
     * How many SIM slots the hardware has.
     *
     * `getSupportedModemCount()` is the right question — it reports what the device *can* hold, so a
     * dual-SIM phone with one card still says 2 and keeps its empty slot on screen. `getActiveModemCount()`
     * would drop to 1 there and make the layout jump whenever a SIM is added or removed. It only exists
     * from API 30, so API 29 falls back to the deprecated phone count, and anything unusable falls back to
     * [DEFAULT_SLOTS] — dual-SIM is by far the more common shape, and guessing single would hide a slot.
     */
    @Suppress("DEPRECATION")
    private fun supportedSlotCount(manager: TelephonyManager): Int {
        val reported = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.supportedModemCount
            } else {
                manager.phoneCount
            }
        }.getOrDefault(0)
        return if (reported in 1..MAX_SLOTS) reported else DEFAULT_SLOTS
    }

    /** The subscription the system uses for data, used to pick a sensible default selection. */
    fun defaultDataSubId(): Int = SubscriptionManager.getDefaultDataSubscriptionId()

    private companion object {
        /** No consumer handset ships more than two SIM slots, and the selector is laid out for two. */
        const val MAX_SLOTS = 2
        const val DEFAULT_SLOTS = 2
    }
}
