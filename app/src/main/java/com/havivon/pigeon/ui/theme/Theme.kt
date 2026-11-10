package com.havivon.pigeon.ui.theme

import android.text.TextUtils
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

/**
 * One theme's colours (a skin has a light and a dark set, see [Skin]). The header and the outgoing bubbles are
 * gradients, kept as their stops so the contrast test can check white text on every stop.
 */
@Immutable
data class HebColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val headerStops: List<Color>,
    val outgoingStops: List<Color>,
    val incoming: Color,
    val spam: Color,
    val divider: Color,
    /** Text and icons on a white pill over the header (the selected chip, the category button, white buttons). */
    val onWhite: Color,
) {
    val header: Brush = Brush.linearGradient(headerStops)
    val outgoing: Brush = Brush.linearGradient(outgoingStops)
}

private val Violet = Color(0xFF5B3FD9)
private val Magenta = Color(0xFFC2408F)
private val Coral = Color(0xFFFF7A59)

/** The Sunset skin's light colours: the app's original look, and the default. */
val LightHebColors = HebColors(
    isDark = false,
    background = Color(0xFFF5F0FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDE6F7),
    onSurface = Color(0xFF1D1530),
    onSurfaceMuted = Color(0xFF6E6585),
    accent = Violet,
    headerStops = listOf(Violet, Magenta, Coral),
    outgoingStops = listOf(Color(0xFF6A4BE6), Color(0xFFB4479A)),
    incoming = Color.White,
    spam = Color(0xFFE53935),
    divider = Color(0xFFE6DFF0),
    onWhite = Violet,
)

val DarkHebColors = HebColors(
    isDark = true,
    background = Color(0xFF110D1F),
    surface = Color(0xFF1C1631),
    surfaceVariant = Color(0xFF282042),
    onSurface = Color(0xFFF1ECFA),
    onSurfaceMuted = Color(0xFFA79FC0),
    accent = Color(0xFFB9A6FF),
    headerStops = listOf(Color(0xFF3A2596), Color(0xFF8A2C6B), Color(0xFFB8513A)),
    outgoingStops = listOf(Color(0xFF5B3FD9), Color(0xFF9C3A86)),
    incoming = Color(0xFF241D3B),
    spam = Color(0xFFFF5A52),
    divider = Color(0xFF2D2548),
    // The accent is too pale on white in the dark theme, so it takes the dark gradient's first stop
    onWhite = Color(0xFF3A2596),
)

val LocalHebColors = staticCompositionLocalOf { LightHebColors }

object HebTheme {
    val colors: HebColors
        @Composable get() = LocalHebColors.current
}

private val HebTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

/**
 * The app is bilingual: it is laid out right to left when the current locale is a right-to-left one (Hebrew), and
 * left to right otherwise. [skin] picks the palette; Sunset is the app's original look.
 */
@Composable
fun HebSmsTheme(darkTheme: Boolean = isSystemInDarkTheme(), skin: Skin = Skin.Sunset, content: @Composable () -> Unit) {
    val layoutDirection = when (TextUtils.getLayoutDirectionFromLocale(LocalConfiguration.current.locales[0])) {
        View.LAYOUT_DIRECTION_RTL -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }
    val colors = skin.colors(darkTheme)
    val scheme = if (darkTheme) {
        darkColorScheme(primary = colors.accent, background = colors.background, surface = colors.surface, onSurface = colors.onSurface)
    } else {
        lightColorScheme(primary = colors.accent, background = colors.background, surface = colors.surface, onSurface = colors.onSurface)
    }
    CompositionLocalProvider(
        LocalHebColors provides colors,
        LocalLayoutDirection provides layoutDirection,
        LocalTextSelectionColors provides TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.3f)),
    ) {
        MaterialTheme(colorScheme = scheme, typography = HebTypography, content = content)
    }
}
