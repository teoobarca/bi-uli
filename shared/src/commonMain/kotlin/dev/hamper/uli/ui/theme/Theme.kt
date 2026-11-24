package dev.hamper.uli.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Identity: warm paper and ink, with terminal amber as the one loud color.
 * Light mode is ink on paper with amber accents; dark mode flips to amber on a warm black.
 */

@Immutable
data class UliColors(
    val right: Color,
    val onRight: Color,
    val rightContainer: Color,
    val onRightContainer: Color,
    val wrong: Color,
    val onWrong: Color,
    val wrongContainer: Color,
    val onWrongContainer: Color,
    val amber: Color,
    val onAmber: Color,
    val terminal: Color,
    val terminalText: Color,
    val terminalDim: Color,
    val terminalPrompt: Color,
    val codeBackground: Color,
    val learning: Color,
)

private val LightUli = UliColors(
    right = Color(0xFF1C7A43), onRight = Color.White,
    rightContainer = Color(0xFFC6EFCF), onRightContainer = Color(0xFF00210D),
    wrong = Color(0xFFBA2B1F), onWrong = Color.White,
    wrongContainer = Color(0xFFFFDAD4), onWrongContainer = Color(0xFF410001),
    amber = Color(0xFFFFC24D), onAmber = Color(0xFF261900),
    terminal = Color(0xFF1E1C17), terminalText = Color(0xFFF3ECDF), terminalDim = Color(0xFFA9A193),
    terminalPrompt = Color(0xFFFFC24D),
    codeBackground = Color(0x14000000),
    learning = Color(0xFFD9A441),
)

private val DarkUli = UliColors(
    right = Color(0xFF7FD99A), onRight = Color(0xFF00391A),
    rightContainer = Color(0xFF14512E), onRightContainer = Color(0xFFC6EFCF),
    wrong = Color(0xFFFFB4A8), onWrong = Color(0xFF690003),
    wrongContainer = Color(0xFF7A231A), onWrongContainer = Color(0xFFFFDAD4),
    amber = Color(0xFFFFC24D), onAmber = Color(0xFF261900),
    terminal = Color(0xFF0B0A08), terminalText = Color(0xFFF3ECDF), terminalDim = Color(0xFF9C9486),
    terminalPrompt = Color(0xFFFFC24D),
    codeBackground = Color(0x1FFFFFFF),
    learning = Color(0xFF8C6A2E),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1E1C17),
    onPrimary = Color(0xFFFFF8EC),
    primaryContainer = Color(0xFFFFC24D),
    onPrimaryContainer = Color(0xFF261900),
    secondary = Color(0xFF6B5D45),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E3C8),
    onSecondaryContainer = Color(0xFF241A07),
    tertiary = Color(0xFF2D6A5B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB7EEDB),
    onTertiaryContainer = Color(0xFF002019),
    background = Color(0xFFFBF7F0),
    onBackground = Color(0xFF1E1C17),
    surface = Color(0xFFFBF7F0),
    onSurface = Color(0xFF1E1C17),
    surfaceVariant = Color(0xFFEDE4D4),
    onSurfaceVariant = Color(0xFF4F4739),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F0E5),
    surfaceContainer = Color(0xFFF1EADD),
    surfaceContainerHigh = Color(0xFFEBE3D4),
    surfaceContainerHighest = Color(0xFFE4DCCB),
    outline = Color(0xFF81776A),
    outlineVariant = Color(0xFFD3C8B6),
    error = Color(0xFFBA2B1F),
    inverseSurface = Color(0xFF33302A),
    inverseOnSurface = Color(0xFFF7EFE3),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFC24D),
    onPrimary = Color(0xFF261900),
    primaryContainer = Color(0xFF5B4100),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFFD7C4A0),
    onSecondary = Color(0xFF3A2F18),
    secondaryContainer = Color(0xFF3F3628),
    onSecondaryContainer = Color(0xFFF1E3C8),
    tertiary = Color(0xFF8DD3BE),
    onTertiary = Color(0xFF00382D),
    tertiaryContainer = Color(0xFF1E5245),
    onTertiaryContainer = Color(0xFFB7EEDB),
    background = Color(0xFF13120F),
    onBackground = Color(0xFFECE3D5),
    surface = Color(0xFF13120F),
    onSurface = Color(0xFFECE3D5),
    surfaceVariant = Color(0xFF4C4538),
    onSurfaceVariant = Color(0xFFCFC5B4),
    surfaceContainerLowest = Color(0xFF0E0D0A),
    surfaceContainerLow = Color(0xFF1B1915),
    surfaceContainer = Color(0xFF201E19),
    surfaceContainerHigh = Color(0xFF2A2822),
    surfaceContainerHighest = Color(0xFF35322B),
    outline = Color(0xFF999080),
    outlineVariant = Color(0xFF4C4538),
    error = Color(0xFFFFB4A8),
    inverseSurface = Color(0xFFECE3D5),
    inverseOnSurface = Color(0xFF33302A),
)

/** A variable font shipped with the app, [file] without .ttf (androidMain/res/font; the web serves the same files). */
internal expect fun fontFile(file: String, weight: FontWeight, variationSettings: FontVariation.Settings): Font

private fun sans(weight: Int, round: Float = 0f) = fontFile(
    "google_sans_flex",
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("ROND", round),
    ),
)

private fun mono(weight: Int) = fontFile(
    "jetbrains_mono",
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Text and UI. */
val Sans = FontFamily(sans(400), sans(500), sans(600), sans(700), sans(800))

/** Big numbers and headlines: the same family with fully rounded terminals. */
val Rounded = FontFamily(sans(500, 100f), sans(600, 100f), sans(700, 100f), sans(800, 100f), sans(900, 100f))

/** Commands, paths and the mini terminal. Never set it on its own: use [monoStyle] or [monoSpan]. */
val Mono = FontFamily(mono(400), mono(500), mono(700))

/**
 * JetBrains Mono draws `!=` as `≠`, `->` as an arrow, `&&`, `||`, `#!`, `::`, `<=`, `>=` as ligatures.
 * Shell code has to show the characters you type, so every mono run switches them off.
 * (scripts/gate.sh fails if Mono is set as a font family anywhere outside this file.)
 */
const val MONO_FEATURES = "liga 0, calt 0, dlig 0"

fun monoStyle(
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified,
) = TextStyle(fontFamily = Mono, fontFeatureSettings = MONO_FEATURES, fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight, color = color)

fun monoSpan(
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    background: Color = Color.Unspecified,
    color: Color = Color.Unspecified,
) = SpanStyle(fontFamily = Mono, fontFeatureSettings = MONO_FEATURES, fontSize = fontSize, fontWeight = fontWeight, background = background, color = color)

private val base = Typography()

private fun TextStyle.sans() = copy(fontFamily = Sans)
private fun TextStyle.rounded() = copy(fontFamily = Rounded)

private val UliTypography = Typography(
    displayLarge = base.displayLarge.rounded().copy(fontWeight = FontWeight.W800, letterSpacing = (-0.02).em),
    displayMedium = base.displayMedium.rounded().copy(fontWeight = FontWeight.W800, letterSpacing = (-0.02).em),
    displaySmall = base.displaySmall.rounded().copy(fontWeight = FontWeight.W700),
    headlineLarge = base.headlineLarge.rounded().copy(fontWeight = FontWeight.W700),
    headlineMedium = base.headlineMedium.rounded().copy(fontWeight = FontWeight.W700),
    headlineSmall = base.headlineSmall.rounded().copy(fontWeight = FontWeight.W700),
    titleLarge = base.titleLarge.rounded().copy(fontWeight = FontWeight.W700),
    titleMedium = base.titleMedium.sans().copy(fontWeight = FontWeight.W600),
    titleSmall = base.titleSmall.sans().copy(fontWeight = FontWeight.W600),
    bodyLarge = base.bodyLarge.sans().copy(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = base.bodyMedium.sans().copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = base.bodySmall.sans(),
    labelLarge = base.labelLarge.sans().copy(fontWeight = FontWeight.W600),
    labelMedium = base.labelMedium.sans().copy(fontWeight = FontWeight.W600),
    labelSmall = base.labelSmall.sans().copy(fontWeight = FontWeight.W600),
)

/** The question itself: large, calm and readable on a shaking metro. */
val QuestionStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.W500, fontSize = 23.sp, lineHeight = 31.sp, letterSpacing = (-0.005).em)

private val UliShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

val LocalUliColors = staticCompositionLocalOf { LightUli }

object Uli {
    val colors: UliColors
        @Composable get() = LocalUliColors.current
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UliTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalUliColors provides if (dark) DarkUli else LightUli) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = UliShapes,
            typography = UliTypography,
            content = content,
        )
    }
}
