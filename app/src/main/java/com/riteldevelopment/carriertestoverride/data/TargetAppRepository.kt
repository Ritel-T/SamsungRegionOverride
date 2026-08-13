package com.riteldevelopment.carriertestoverride.data

import android.content.Context
import com.riteldevelopment.carriertestoverride.TargetApps

/**
 * How much of an app's storage to throw away before restarting it.
 *
 * The ordinal is the wire value the AIDL carries, so it must stay aligned with `TargetApps.WIPE_*`.
 * [wireValue] states that dependency instead of relying on declaration order.
 */
enum class WipeMode(val wireValue: Int, val label: String) {
    /** Stop and relaunch only. Always safe, and enough for an app that re-reads region on cold start. */
    NONE(TargetApps.WIPE_NONE, "Keep"),

    /** Known no-op on One UI 8.5 — see `TargetApps.WIPE_CACHE`. Reported honestly rather than hidden. */
    CACHE(TargetApps.WIPE_CACHE, "Cache"),

    /** Signs the user out, and is the only wipe that reliably forces a region re-detect. */
    DATA(TargetApps.WIPE_DATA, "Data");

    val destructive: Boolean get() = this == DATA
}

/** One app whose cached region this tool can reset. */
data class TargetApp(
    val packageName: String,
    val label: String,
    val installed: Boolean,
)

/**
 * Resolves the target packages to something displayable.
 *
 * Reading the real label needs the package to be visible to this app, which on API 30+ means the
 * `<queries>` block in the manifest. When a target is not installed the fallback name is used, because
 * "TikTok — not installed" is a more useful thing to show than hiding the row: it tells the user why
 * nothing happened to that app.
 */
class TargetAppRepository(context: Context) {

    private val packageManager = context.applicationContext.packageManager

    fun scan(): List<TargetApp> = TargetApps.defaultPackages().map { packageName ->
        val info = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
        TargetApp(
            packageName = packageName,
            label = info?.let { packageManager.getApplicationLabel(it).toString() }
                ?: FALLBACK_LABELS[packageName]
                ?: packageName.substringAfterLast('.'),
            installed = info != null,
        )
    }

    private companion object {
        /** Only used when the package is absent, so its label cannot be read from the system. */
        val FALLBACK_LABELS = mapOf(
            "com.sec.android.app.samsungapps" to "Galaxy Store",
            "com.samsung.android.voc" to "Samsung Members",
            "com.zhiliaoapp.musically" to "TikTok",
        )
    }
}
