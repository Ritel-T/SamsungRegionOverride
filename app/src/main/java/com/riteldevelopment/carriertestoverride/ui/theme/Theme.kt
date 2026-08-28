package com.riteldevelopment.carriertestoverride.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
 * The Material 3 defaults are kept as-is. No custom font is shipped: the system face and its locale
 * fallback cover every translated UI, and keep this tool visually consistent with the rest of the phone.
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

/**
 * Puts the authored outcome hues back over a dynamic scheme.
 *
 * Only the tertiary roles: this app spends tertiary on the PARTIAL result — one layer landed, the other
 * did not — where Material treats it as a free third accent and a dynamic scheme derives it from the
 * same wallpaper source as primary. Left alone, PARTIAL and success would arrive as neighbouring hues
 * and stop being distinguishable at a glance, which is the one thing that result exists to say.
 *
 * Error needs no such treatment; a dynamic scheme already holds it at red. Success and the hazard
 * stripe live in [OverrideColors], outside Material's slots, so nothing overwrites them either.
 */
private fun ColorScheme.withAuthoredOutcomes(authored: ColorScheme): ColorScheme = copy(
    tertiary = authored.tertiary,
    onTertiary = authored.onTertiary,
    tertiaryContainer = authored.tertiaryContainer,
    onTertiaryContainer = authored.onTertiaryContainer,
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
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        dynamic.withAuthoredOutcomes(authored)
    } else {
        authored
    }

    CompositionLocalProvider(
        LocalOverrideColors provides if (darkTheme) DarkOverrideColors else LightOverrideColors
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = OverrideTypography,
            content = content,
        )
    }
}
