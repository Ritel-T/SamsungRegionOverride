package com.riteldevelopment.carriertestoverride.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.annotation.StringRes
import com.riteldevelopment.carriertestoverride.BuildConfig
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.TargetApps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * How much of an app's storage to throw away before restarting it.
 *
 * The ordinal is the wire value the AIDL carries, so it must stay aligned with `TargetApps.WIPE_*`.
 * [wireValue] states that dependency instead of relying on declaration order.
 */
enum class WipeMode(val wireValue: Int, @get:StringRes val labelRes: Int) {
    /** Stop and relaunch only. Always safe, and enough for an app that re-reads region on cold start. */
    NONE(TargetApps.WIPE_NONE, R.string.wipe_keep),

    /** Known no-op on One UI 8.5 — see `TargetApps.WIPE_CACHE`. Reported honestly rather than hidden. */
    CACHE(TargetApps.WIPE_CACHE, R.string.wipe_cache),

    /** Signs the user out, and is the only wipe that reliably forces a region re-detect. */
    DATA(TargetApps.WIPE_DATA, R.string.wipe_data);

    val destructive: Boolean get() = this == DATA
}

/**
 * One app whose cached region this tool can reset.
 *
 * Deliberately carries no icon. An icon is a bitmap with reference equality, so putting one here would
 * make every rescan produce a list that is unequal to the last one and recompose the whole panel on
 * each resume. The UI loads and caches icons by package name instead.
 */
data class TargetApp(
    val packageName: String,
    val label: String,
    val installed: Boolean,
)

/**
 * Resolves the chosen target packages to something displayable, and offers the full app list to choose
 * from.
 *
 * Reading a real label needs the package to be visible to this app, which on API 30+ means the
 * `<queries>` block in the manifest — a `<intent>` filter for launchable apps plus the named defaults.
 * When a target is not installed the fallback name is used, because "TikTok — not installed" is a more
 * useful thing to show than hiding the row: it tells the user why nothing happened to that app.
 */
class TargetAppRepository(
    context: Context,
    private val store: OverrideStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val packageManager = context.applicationContext.packageManager

    /** Packages shown in the manual refresh panel: the user's choice, or built-in defaults if unset. */
    fun selectedPackages(): List<String> =
        store.targetPackages() ?: TargetApps.defaultPackages().toList()

    /** True while the built-in defaults are in force, so the UI can offer "reset" only when it means something. */
    fun usingDefaults(): Boolean = store.targetPackages() == null

    fun select(packages: List<String>) = store.setTargetPackages(packages)

    fun resetToDefaults() = store.clearTargetPackages()

    fun scan(): List<TargetApp> = selectedPackages().map { packageName ->
        val info = applicationInfo(packageName)
        TargetApp(
            packageName = packageName,
            label = label(packageName, info),
            installed = info != null,
        )
    }

    /**
     * Every app the user could pick, sorted by label.
     *
     * Built from the launcher query rather than an installed-package enumeration, because the latter
     * needs QUERY_ALL_PACKAGES — a restricted permission this tool has no business holding just to
     * populate a list. Anything already selected is unioned in regardless, so a chosen target that has
     * no launcher entry (or has since been uninstalled) still appears, with its box ticked, instead of
     * silently disappearing from the list that governs it.
     *
     * Suspending because this decodes a few hundred labels and must not run on the main thread.
     */
    suspend fun installedApps(): List<TargetApp> = withContext(io) {
        val launchable = runCatching {
            packageManager.queryIntentActivities(LAUNCHER_INTENT, 0)
                .mapNotNull { it.activityInfo?.applicationInfo }
        }.getOrDefault(emptyList())

        val found = LinkedHashMap<String, TargetApp>()
        launchable.forEach { info ->
            if (info.packageName == BuildConfig.APPLICATION_ID) return@forEach
            found[info.packageName] = TargetApp(
                packageName = info.packageName,
                label = label(info.packageName, info),
                installed = true,
            )
        }
        selectedPackages().forEach { packageName ->
            if (found.containsKey(packageName)) return@forEach
            val info = applicationInfo(packageName)
            found[packageName] = TargetApp(
                packageName = packageName,
                label = label(packageName, info),
                installed = info != null,
            )
        }
        found.values.sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    private fun applicationInfo(packageName: String): ApplicationInfo? =
        runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()

    private fun label(packageName: String, info: ApplicationInfo?): String =
        info?.let { runCatching { packageManager.getApplicationLabel(it).toString() }.getOrNull() }
            ?: FALLBACK_LABELS[packageName]
            ?: packageName.substringAfterLast('.')

    private companion object {
        val LAUNCHER_INTENT: Intent =
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        /** Only used when the package is absent, so its label cannot be read from the system. */
        val FALLBACK_LABELS = mapOf(
            "com.sec.android.app.samsungapps" to "Galaxy Store",
            "com.samsung.android.voc" to "Samsung Members",
            "com.zhiliaoapp.musically" to "TikTok",
        )
    }
}
