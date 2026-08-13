package com.riteldevelopment.carriertestoverride.data

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** One selectable SIM/modem slot that currently carries a valid subscription. */
data class SimInfo(
    val slotIndex: Int,
    val subId: Int,
    val simState: Int,
    val operatorNumeric: String,
    val operatorName: String,
    val countryIso: String,
    val flags: OverrideStore.Flags,
) {
    /** Only a fully loaded SIM exposes the IccRecords the SIM identity layer rewrites. */
    val isReady: Boolean get() = simState == TelephonyManager.SIM_STATE_READY

    val stateLabel: String get() = simStateName(simState)

    val displayName: String
        get() = "SIM ${slotIndex + 1}"

    companion object {
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
            ?: return SimScan.Failure("The system did not provide a TelephonyManager.")
        val declared = supportedSlotCount(manager)
        val sims = (0 until declared).mapNotNull { slot -> readSlot(manager, slot) }
        // A subscription reported at a slot beyond the declared count would otherwise be dropped
        // silently, so the count grows to cover it rather than the SIM disappearing from the screen.
        val highestUsed = (sims.maxOfOrNull { it.slotIndex } ?: -1) + 1
        SimScan.Success(sims, maxOf(declared, highestUsed))
    } catch (throwable: Throwable) {
        SimScan.Failure(
            "Could not read SIM state: ${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}"
        )
    }

    private fun readSlot(manager: TelephonyManager, slot: Int): SimInfo? {
        val subId = SubscriptionManager.getSubscriptionId(slot)
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
        )
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
