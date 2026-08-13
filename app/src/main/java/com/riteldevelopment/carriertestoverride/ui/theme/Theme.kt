package com.riteldevelopment.carriertestoverride.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Colour is fixed, not dynamic.
 *
 * Dynamic colour would make this tool look like every other Material app, and — more importantly — it
 * would put the wallpaper in charge of hues that here carry meaning: "this layer landed", "one layer
 * failed", "this is destructive". Semantic roles must stay stable, so the palette is authored.
 *
 * The accent is a petrol/instrument teal, and the neutrals are biased slightly toward it rather than
 * being pure grey — a pure mid-grey reads as unconsidered.
 */

private val Petrol10 = Color(0xFF00363D)
private val Petrol30 = Color(0xFF004E58)
private val Petrol40 = Color(0xFF0F5E6B)
private val Petrol80 = Color(0xFF7FD1DE)
private val Petrol90 = Color(0xFFA9E9F2)

private val Slate10 = Color(0xFF171D1E)
private val Slate20 = Color(0xFF1C3439)
private val Slate30 = Color(0xFF334B50)
private val Slate40 = Color(0xFF4A6268)
private val Slate80 = Color(0xFFB1CBD1)
private val Slate90 = Color(0xFFCDE7ED)

private val Amber20 = Color(0xFF422C00)
private val Amber30 = Color(0xFF5D4200)
private val Amber40 = Color(0xFF7A5A20)
private val Amber80 = Color(0xFFE8B057)
private val Amber90 = Color(0xFFFFDEA8)

private val Crimson40 = Color(0xFFA62B22)
private val Crimson80 = Color(0xFFF2B8B2)

private val LightScheme = lightColorScheme(
    primary = Petrol40,
    onPrimary = Color.White,
    primaryContainer = Petrol90,
    onPrimaryContainer = Petrol10,
    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Color(0xFF051F24),
    // Tertiary is reserved for the PARTIAL outcome — one layer landed, the other did not.
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber20,
    error = Crimson40,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410100),
    background = Color(0xFFF3F6F6),
    onBackground = Slate10,
    surface = Color(0xFFF3F6F6),
    onSurface = Slate10,
    surfaceVariant = Color(0xFFD9E3E5),
    onSurfaceVariant = Color(0xFF3F484A),
    outline = Color(0xFF6F797B),
    outlineVariant = Color(0xFFBFC8CA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEDF1F1),
    surfaceContainer = Color(0xFFE7ECEC),
    surfaceContainerHigh = Color(0xFFE2E6E7),
    surfaceContainerHighest = Color(0xFFDCE1E1),
)

private val DarkScheme = darkColorScheme(
    primary = Petrol80,
    onPrimary = Petrol10,
    primaryContainer = Petrol30,
    onPrimaryContainer = Petrol90,
    secondary = Slate80,
    onSecondary = Slate20,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,
    tertiary = Amber80,
    onTertiary = Amber20,
    tertiaryContainer = Amber30,
    onTertiaryContainer = Amber90,
    error = Crimson80,
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDEE3E4),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDEE3E4),
    surfaceVariant = Color(0xFF3F484A),
    onSurfaceVariant = Color(0xFFBFC8CA),
    outline = Color(0xFF899294),
    outlineVariant = Color(0xFF3F484A),
    surfaceContainerLowest = Color(0xFF090F10),
    surfaceContainerLow = Color(0xFF171D1E),
    surfaceContainer = Color(0xFF1B2122),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF303637),
)

/**
 * Roles Material 3 has no slot for. "Applied" is a *good* state distinct from primary — primary means
 * "you can act here", success means "this layer is currently rewriting your SIM's identity".
 */
@Immutable
data class OverrideColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** Ink for the hatched risk edge; deliberately low-contrast so it textures rather than shouts. */
    val hazardStripe: Color,
)

private val LightOverrideColors = OverrideColors(
    success = Color(0xFF2E7D46),
    onSuccess = Color.White,
    successContainer = Color(0xFFC7EFD3),
    onSuccessContainer = Color(0xFF00210E),
    hazardStripe = Color(0xFF7A5A20).copy(alpha = 0.28f),
)

private val DarkOverrideColors = OverrideColors(
    success = Color(0xFF7BD69B),
    onSuccess = Color(0xFF00391B),
    successContainer = Color(0xFF0F4A26),
    onSuccessContainer = Color(0xFFC7EFD3),
    hazardStripe = Color(0xFFE8B057).copy(alpha = 0.30f),
)

val LocalOverrideColors: ProvidableCompositionLocal<OverrideColors> =
    staticCompositionLocalOf { LightOverrideColors }

/*
 * Type.
 *
 * The Material 3 defaults are kept as-is. An earlier revision widened every line height because the UI
 * was set in Simplified Chinese, which packs denser and has no ascender/descender variance to create
 * optical leading; the interface is now English throughout, so that tuning would only loosen text the
 * defaults already space correctly. No custom font is shipped — the system face is what the rest of the
 * phone uses, and this tool has no reason to look like it came from somewhere else.
 */
internal val OverrideTypography = Typography()

/**
 * Lining, fixed-width digits. MCC/MNC values are compared digit by digit (46000 vs 23430), so the
 * figures must line up; `tnum` gets that without dropping to a monospace family, which would stand out
 * badly against the surrounding prose.
 */
val TabularFigures: TextStyle = TextStyle(
    fontFeatureSettings = "tnum, lnum",
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.5.sp,
)

@Composable
fun CarrierOverrideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalOverrideColors provides if (darkTheme) DarkOverrideColors else LightOverrideColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = OverrideTypography,
            content = content,
        )
    }
}
