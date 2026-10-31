package com.hebsms.app.ui.theme

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

@Immutable
data class HebColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val header: Brush,
    val outgoing: Brush,
    val incoming: Color,
    val spam: Color,
    val spamTint: Color,
    val divider: Color,
)

private val Violet = Color(0xFF5B3FD9)
private val Magenta = Color(0xFFC2408F)
private val Coral = Color(0xFFFF7A59)

val LightHebColors = HebColors(
    isDark = false,
    background = Color(0xFFF5F0FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDE6F7),
    onSurface = Color(0xFF1D1530),
    onSurfaceMuted = Color(0xFF6E6585),
    accent = Violet,
    header = Brush.linearGradient(listOf(Violet, Magenta, Coral)),
    outgoing = Brush.linearGradient(listOf(Color(0xFF6A4BE6), Color(0xFFB4479A))),
    incoming = Color.White,
    spam = Color(0xFFE53935),
    spamTint = Color(0xFFFFF0F0),
    divider = Color(0xFFE6DFF0),
)

val DarkHebColors = HebColors(
    isDark = true,
    background = Color(0xFF110D1F),
    surface = Color(0xFF1C1631),
    surfaceVariant = Color(0xFF282042),
    onSurface = Color(0xFFF1ECFA),
    onSurfaceMuted = Color(0xFFA79FC0),
    accent = Color(0xFFB9A6FF),
    header = Brush.linearGradient(listOf(Color(0xFF3A2596), Color(0xFF8A2C6B), Color(0xFFB8513A))),
    outgoing = Brush.linearGradient(listOf(Color(0xFF5B3FD9), Color(0xFF9C3A86))),
    incoming = Color(0xFF241D3B),
    spam = Color(0xFFFF5A52),
    spamTint = Color(0xFF3A1A24),
    divider = Color(0xFF2D2548),
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

/** The app is bilingual: it is laid out right to left when the current locale is a right-to-left one (Hebrew), and left to right otherwise. */
@Composable
fun HebSmsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val layoutDirection = when (TextUtils.getLayoutDirectionFromLocale(LocalConfiguration.current.locales[0])) {
        View.LAYOUT_DIRECTION_RTL -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }
    val colors = if (darkTheme) DarkHebColors else LightHebColors
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
