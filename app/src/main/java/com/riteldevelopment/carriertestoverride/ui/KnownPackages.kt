package com.riteldevelopment.carriertestoverride.ui

/**
 * Packages the UI names directly, as opposed to the ones the user picks.
 *
 * These are not the refresh targets — that list is [com.riteldevelopment.carriertestoverride.TargetApps]'s
 * and the user can replace it. These say something the refresh list cannot: *which signal an app
 * believes*. Galaxy Store reads the network the SIM claims to be on, TikTok reads the country
 * CarrierConfig reports, and that split is the whole reason this screen has two switches instead of
 * one. The overlap with the default refresh targets is real but incidental; changing one should not
 * silently change the other.
 *
 * Used as exemplars, never as a filter. Overriding a layer affects every app that reads it, and the
 * icons beside a layer are there to make the switch recognisable, not to enumerate what it touches.
 */
object KnownPackages {
    /** Also listed in the manifest's `<queries>`, or neither the launch intent nor the icon resolves. */
    const val SHIZUKU = "moe.shizuku.privileged.api"

    /** Reads the CarrierConfig country ISO. */
    val COUNTRY_READERS = listOf("com.zhiliaoapp.musically")

    /** Reads the MCC/MNC the SIM reports. */
    val NETWORK_READERS = listOf(
        "com.sec.android.app.samsungapps",
        "com.samsung.android.voc",
    )
}
