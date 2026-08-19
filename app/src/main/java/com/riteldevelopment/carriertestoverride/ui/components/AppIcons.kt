package com.riteldevelopment.carriertestoverride.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App icons, loaded off the main thread and cached for the life of the process.
 *
 * Kept out of the data layer on purpose. An icon is a bitmap with reference equality, so carrying one
 * on `TargetApp` would make every rescan produce a list unequal to the last and recompose the panel on
 * every resume. Loading by package name here means the model stays comparable and the bitmap is
 * decoded once no matter how many rows or dialogs ask for it.
 *
 * A miss caches nothing, so an app installed while the screen is open picks up its icon on the next
 * pass instead of being remembered as iconless.
 */
private val iconCache = mutableMapOf<String, ImageBitmap>()

/** Adaptive icons rasterise at whatever size is asked for; this is comfortably past the display size. */
private const val ICON_PIXELS = 96

@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf(iconCache[packageName]) }
    LaunchedEffect(packageName) {
        if (icon != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { loadIcon(context, packageName) }
        if (loaded != null) {
            iconCache[packageName] = loaded
            icon = loaded
        }
    }
    return icon
}

private fun loadIcon(context: Context, packageName: String): ImageBitmap? = runCatching {
    context.packageManager
        .getApplicationIcon(packageName)
        .toBitmap(ICON_PIXELS, ICON_PIXELS)
        .asImageBitmap()
}.getOrNull()
