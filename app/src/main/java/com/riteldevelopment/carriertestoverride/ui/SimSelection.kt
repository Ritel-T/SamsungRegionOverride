package com.riteldevelopment.carriertestoverride.ui

/**
 * Keeps an explicit SIM choice stable while telephony is temporarily rebuilding its subscription list.
 *
 * A UICC cycle can make an explicitly selected subscription disappear for one or more scans. Falling
 * back during that gap loses the user's intent permanently, so an explicit selection is retained even
 * while no current card matches it. The UI then has no selected card and privileged actions stay
 * disabled until either the subscription returns or the user deliberately chooses another one.
 *
 * Automatic selections are different. Android 17 can enumerate a dual-SIM phone in stages, exposing the
 * first card before the data subscription. Treating that first fallback as explicit pins the wrong SIM
 * when the second scan arrives. Until the user makes a choice, every scan may promote the visible data
 * SIM; without one, the current visible fallback is kept so harmless list reordering does not move it.
 */
internal fun resolveSelectedSubIdAfterScan(
    currentSelectedSubId: Int,
    scannedSubIds: List<Int>,
    defaultDataSubId: Int,
    selectionIsExplicit: Boolean,
): Int {
    if (selectionIsExplicit && currentSelectedSubId >= 0) return currentSelectedSubId
    return defaultDataSubId.takeIf { it in scannedSubIds }
        ?: currentSelectedSubId.takeIf { it in scannedSubIds }
        ?: scannedSubIds.firstOrNull()
        ?: -1
}
