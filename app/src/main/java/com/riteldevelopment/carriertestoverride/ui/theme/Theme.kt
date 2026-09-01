package com.riteldevelopment.carriertestoverride.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Colour follows the system palette, with the roles that carry meaning held back.
 *
 * Material You supplies the neutrals and the accent from API 31 on. What it must not supply is a hue
 * that states an outcome: "this layer landed", "one landed and one did not", "this is destructive". A
 * wallpaper that turned PARTIAL into the same family as the accent would leave two different results
 * looking alike, so those roles stay authored and are laid back over the dynamic scheme.
 *
 * The scheme below is still the whole palette on API 29 and 30, where there is no system source to draw
 * from. Its accent is a petrol/instrument teal, and the neutrals are biased slightly toward it rather
 * than being pure grey — a pure mid-grey reads as unconsidered.
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
    primaryFixed = Petrol90,
    primaryFixedDim = Petrol80,
    onPrimaryFixed = Petrol10,
    onPrimaryFixedVariant = Petrol30,
    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Color(0xFF051F24),
    secondaryFixed = Slate90,
    secondaryFixedDim = Slate80,
    onSecondaryFixed = Slate10,
    onSecondaryFixedVariant = Slate30,
    // Tertiary is reserved for the PARTIAL outcome — one layer landed, the other did not.
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber20,
    tertiaryFixed = Amber90,
    tertiaryFixedDim = Amber80,
    onTertiaryFixed = Amber20,
    onTertiaryFixedVariant = Amber30,
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
    surfaceBright = Color(0xFFF9FCFC),
    surfaceDim = Color(0xFFD3D8D8),
)

private val DarkScheme = darkColorScheme(
    primary = Petrol80,
    onPrimary = Petrol10,
    primaryContainer = Petrol30,
    onPrimaryContainer = Petrol90,
    primaryFixed = Petrol90,
    primaryFixedDim = Petrol80,
    onPrimaryFixed = Petrol10,
    onPrimaryFixedVariant = Petrol30,
    secondary = Slate80,
    onSecondary = Slate20,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,
    secondaryFixed = Slate90,
    secondaryFixedDim = Slate80,
    onSecondaryFixed = Slate10,
    onSecondaryFixedVariant = Slate30,
    tertiary = Amber80,
    onTertiary = Amber20,
    tertiaryContainer = Amber30,
    onTertiaryContainer = Amber90,
    tertiaryFixed = Amber90,
    tertiaryFixedDim = Amber80,
    onTertiaryFixed = Amber20,
    onTertiaryFixedVariant = Amber30,
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
    surfaceBright = Color(0xFF343A3B),
    surfaceDim = Color(0xFF0E1415),
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
 * The Material 3 defaults are kept as-is. No custom font is shipped: the system face and its locale
 * fallback cover every translated UI, and keep this tool visually consistent with the rest of the phone.
 */
internal val OverrideTypography = Typography()

/**
 * The expressive shape scale.
 *
 * Material 3 Expressive treats corner radius as a carrier of hierarchy rather than a constant, so the
 * steps are spread further apart than the classic scale: small chrome stays tight, and the surfaces a
 * finger actually lands on — cards, sheets, the action bar — get noticeably rounder. The jump from
 * `medium` to `large` is where most of this screen's blocks sit, so that is where the difference reads.
 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * Lining, fixed-width digits. MCC/MNC values are compared digit by digit (46000 vs 23430), so the
 * figures must line up; `tnum` gets that without dropping to a monospace family, which would stand out
 * badly against the surrounding prose.
 */
val TabularFigures: TextStyle = TextStyle(
    fontFeatureSettings = "tnum, lnum",
    letterSpacing = 0.sp,
)

/**
 * A dynamic scheme built from the platform's *tonal palette* resources.
 *
 * Not [dynamicLightColorScheme] / [dynamicDarkColorScheme], and the difference is not cosmetic. Android
 * exposes the system palette twice: the original `system_accentN_*` / `system_neutralN_*` tone ramps
 * from API 31, and a second, role-named set — `system_primary_light`, `system_surface_dark` and so on —
 * added in API 34. Compose's dynamic schemes read the role-named set.
 *
 * On the One UI build this was tested against, only the tone ramps follow the user's chosen palette. The
 * role-named colours sit at their AOSP defaults forever, so Compose's own helpers return a stock blue no
 * matter what the phone's colour settings say — the palette appeared to be ignored because the app was
 * reading a channel the vendor never writes to. Measured on SM-S938B: `system_accent1_600` was #744F8E
 * while `dynamicLightColorScheme().primary` was #38608F.
 *
 * This is deliberately a Samsung fallback, not the default for every Android device. Other vendors'
 * role resources carry system contrast choices that a fixed tone mapping cannot reproduce, so they use
 * Compose's official dynamic scheme below. Samsung is the exception because the tested One UI build
 * leaves those role resources at stock blue while updating the tone ramps correctly.
 *
 * Suffixes are inverse tones — `_0` is white, `_1000` black, so `_600` is tone 40 and `_100` is tone 90.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun tonalPaletteScheme(context: Context, dark: Boolean): ColorScheme {
    fun c(id: Int) = Color(context.resources.getColor(id, context.theme))

    // The neutral ramp in tone order. Every published stop is listed, not only the ones the roles below
    // happen to bracket: an omitted stop does not fail, it silently widens the blend that spans it.
    val neutrals = listOf(
        0 to android.R.color.system_neutral1_1000,
        10 to android.R.color.system_neutral1_900,
        20 to android.R.color.system_neutral1_800,
        30 to android.R.color.system_neutral1_700,
        40 to android.R.color.system_neutral1_600,
        50 to android.R.color.system_neutral1_500,
        60 to android.R.color.system_neutral1_400,
        70 to android.R.color.system_neutral1_300,
        80 to android.R.color.system_neutral1_200,
        90 to android.R.color.system_neutral1_100,
        95 to android.R.color.system_neutral1_50,
        99 to android.R.color.system_neutral1_10,
        100 to android.R.color.system_neutral1_0,
    )

    /*
     * A neutral at an arbitrary tone, blended from the two ramp stops that bracket it.
     *
     * The ramp is quantised to ten-tone steps; Material's surface roles are not. The dark scheme alone
     * puts them at tones 4, 6, 10, 12, 17, 22 and 24, and rounding each to its nearest stop lands five
     * of them — background, surface, surfaceDim, surfaceContainerLow and surfaceContainer — on the same
     * single colour. That is not a lost nuance. This screen stacks those roles: the page is `surface`,
     * the layer blocks and the identity card are `surfaceContainerLow`, the target-apps panel is
     * `surfaceContainer`. Collapsed, every one of those blocks loses its edge and the screen reads as
     * unstructured text on a flat ground. Blending the neighbouring stops gives the ladder back.
     *
     * Compose's Color lerp runs in Oklab, so the midpoints are perceptually spaced rather than spaced
     * in sRGB, which is what a tonal palette means by "tone" in the first place.
     */
    fun surfaceAt(tone: Int): Color {
        val lower = neutrals.last { it.first <= tone }
        val upper = neutrals.first { it.first >= tone }
        if (lower.first == upper.first) return c(lower.second)
        val fraction = (tone - lower.first).toFloat() / (upper.first - lower.first)
        return lerp(c(lower.second), c(upper.second), fraction)
    }

    return if (dark) {
        darkColorScheme(
            primary = c(android.R.color.system_accent1_200),
            onPrimary = c(android.R.color.system_accent1_800),
            primaryContainer = c(android.R.color.system_accent1_600),
            onPrimaryContainer = c(android.R.color.system_accent1_100),
            secondary = c(android.R.color.system_accent2_200),
            onSecondary = c(android.R.color.system_accent2_800),
            secondaryContainer = c(android.R.color.system_accent2_700),
            onSecondaryContainer = c(android.R.color.system_accent2_100),
            background = surfaceAt(6),
            onBackground = c(android.R.color.system_neutral1_100),
            surface = surfaceAt(6),
            onSurface = c(android.R.color.system_neutral1_100),
            surfaceVariant = c(android.R.color.system_neutral2_700),
            onSurfaceVariant = c(android.R.color.system_neutral2_200),
            outline = c(android.R.color.system_neutral2_400),
            outlineVariant = c(android.R.color.system_neutral2_700),
            inverseSurface = c(android.R.color.system_neutral1_100),
            inverseOnSurface = c(android.R.color.system_neutral1_800),
            inversePrimary = c(android.R.color.system_accent1_600),
            surfaceTint = c(android.R.color.system_accent1_200),
            surfaceContainerLowest = surfaceAt(4),
            surfaceContainerLow = surfaceAt(10),
            surfaceContainer = surfaceAt(12),
            surfaceContainerHigh = surfaceAt(17),
            surfaceContainerHighest = surfaceAt(22),
            surfaceBright = surfaceAt(24),
            surfaceDim = surfaceAt(6),
            primaryFixed = c(android.R.color.system_accent1_100),
            primaryFixedDim = c(android.R.color.system_accent1_200),
            onPrimaryFixed = c(android.R.color.system_accent1_900),
            onPrimaryFixedVariant = c(android.R.color.system_accent1_700),
            secondaryFixed = c(android.R.color.system_accent2_100),
            secondaryFixedDim = c(android.R.color.system_accent2_200),
            onSecondaryFixed = c(android.R.color.system_accent2_900),
            onSecondaryFixedVariant = c(android.R.color.system_accent2_700),
        )
    } else {
        lightColorScheme(
            primary = c(android.R.color.system_accent1_600),
            onPrimary = c(android.R.color.system_accent1_0),
            primaryContainer = c(android.R.color.system_accent1_100),
            onPrimaryContainer = c(android.R.color.system_accent1_900),
            secondary = c(android.R.color.system_accent2_600),
            onSecondary = c(android.R.color.system_accent2_0),
            secondaryContainer = c(android.R.color.system_accent2_100),
            onSecondaryContainer = c(android.R.color.system_accent2_900),
            background = surfaceAt(98),
            onBackground = c(android.R.color.system_neutral1_900),
            surface = surfaceAt(98),
            onSurface = c(android.R.color.system_neutral1_900),
            surfaceVariant = c(android.R.color.system_neutral2_100),
            onSurfaceVariant = c(android.R.color.system_neutral2_700),
            outline = c(android.R.color.system_neutral2_500),
            outlineVariant = c(android.R.color.system_neutral2_200),
            inverseSurface = c(android.R.color.system_neutral1_800),
            inverseOnSurface = c(android.R.color.system_neutral1_50),
            inversePrimary = c(android.R.color.system_accent1_200),
            surfaceTint = c(android.R.color.system_accent1_600),
            surfaceContainerLowest = surfaceAt(100),
            surfaceContainerLow = surfaceAt(96),
            surfaceContainer = surfaceAt(94),
            surfaceContainerHigh = surfaceAt(92),
            surfaceContainerHighest = surfaceAt(90),
            surfaceBright = surfaceAt(98),
            surfaceDim = surfaceAt(87),
            primaryFixed = c(android.R.color.system_accent1_100),
            primaryFixedDim = c(android.R.color.system_accent1_200),
            onPrimaryFixed = c(android.R.color.system_accent1_900),
            onPrimaryFixedVariant = c(android.R.color.system_accent1_700),
            secondaryFixed = c(android.R.color.system_accent2_100),
            secondaryFixedDim = c(android.R.color.system_accent2_200),
            onSecondaryFixed = c(android.R.color.system_accent2_900),
            onSecondaryFixedVariant = c(android.R.color.system_accent2_700),
        )
    }
}

/**
 * Puts the authored outcome hues back over a dynamic scheme.
 *
 * Only the tertiary roles: this app spends tertiary on the PARTIAL result — one layer landed, the other
 * did not — where Material treats it as a free third accent and a dynamic scheme derives it from the
 * same wallpaper source as primary. Left alone, PARTIAL and success would arrive as neighbouring hues
 * and stop being distinguishable at a glance, which is the one thing that result exists to say.
 *
 * The fixed roles matter to Expressive components as well, so they are kept in the same semantic family
 * instead of falling back to Material's stock purple. Error needs no such treatment on either path:
 * Compose's dynamic schemes hold it at red, and [tonalPaletteScheme] never assigns it, so it keeps the
 * red baked into [darkColorScheme] and [lightColorScheme]. Success and the hazard stripe live in
 * [OverrideColors], outside Material's slots, so nothing overwrites them either.
 */
private fun ColorScheme.withAuthoredOutcomes(authored: ColorScheme): ColorScheme = copy(
    tertiary = authored.tertiary,
    onTertiary = authored.onTertiary,
    tertiaryContainer = authored.tertiaryContainer,
    onTertiaryContainer = authored.onTertiaryContainer,
    tertiaryFixed = authored.tertiaryFixed,
    tertiaryFixedDim = authored.tertiaryFixedDim,
    onTertiaryFixed = authored.onTertiaryFixed,
    onTertiaryFixedVariant = authored.onTertiaryFixedVariant,
)

@Composable
fun CarrierOverrideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Off falls back to the authored palette on every release, which is also what API 29 and 30 get. */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val authored = if (darkTheme) DarkScheme else LightScheme
    val scheme = when {
        !dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> authored
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) ->
            tonalPaletteScheme(context, darkTheme).withAuthoredOutcomes(authored)
        darkTheme -> dynamicDarkColorScheme(context).withAuthoredOutcomes(authored)
        else -> dynamicLightColorScheme(context).withAuthoredOutcomes(authored)
    }

    CompositionLocalProvider(
        LocalOverrideColors provides if (darkTheme) DarkOverrideColors else LightOverrideColors
    ) {
        MaterialTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = ExpressiveShapes,
            typography = OverrideTypography,
            content = content,
        )
    }
}
