package com.havivon.pigeon.ui.theme

import androidx.compose.ui.graphics.Color

// The skins besides Sunset (Theme.kt). Every set keeps body text at 4.5:1 or more on its surfaces and bubbles, white
// on the outgoing bubbles at 4.5:1 and white on the header at 3:1 (large text); SkinContrastTest checks it.
// Spam keeps the same red in every skin.

/** Ocean (light): teal into deep blue, on cool sea-mist surfaces. */
private val OceanLight = HebColors(
    isDark = false,
    background = Color(0xFFEEF5F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE0EDF2),
    onSurface = Color(0xFF0F2530),
    onSurfaceMuted = Color(0xFF52656E),
    accent = Color(0xFF0B6E8A),
    headerStops = listOf(Color(0xFF0A8A8C), Color(0xFF0B6AA0), Color(0xFF1D3F94)),
    outgoingStops = listOf(Color(0xFF0B7F88), Color(0xFF1F4FA8)),
    incoming = Color(0xFFFFFFFF),
    spam = LightHebColors.spam,
    divider = Color(0xFFD7E6EC),
    onWhite = Color(0xFF0B6E8A),
)

/** Ocean (dark). */
private val OceanDark = HebColors(
    isDark = true,
    background = Color(0xFF091418),
    surface = Color(0xFF112129),
    surfaceVariant = Color(0xFF1A2F39),
    onSurface = Color(0xFFE5F2F6),
    onSurfaceMuted = Color(0xFF98B2BC),
    accent = Color(0xFF5FD0D6),
    headerStops = listOf(Color(0xFF0A5E68), Color(0xFF0C4778), Color(0xFF172C68)),
    outgoingStops = listOf(Color(0xFF0B6F78), Color(0xFF1E4A96)),
    incoming = Color(0xFF152934),
    spam = DarkHebColors.spam,
    divider = Color(0xFF1E3540),
    onWhite = Color(0xFF0A4F66),
)

/** Forest (light): emerald into olive, on soft leaf-green surfaces. */
private val ForestLight = HebColors(
    isDark = false,
    background = Color(0xFFF1F6EE),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2ECDC),
    onSurface = Color(0xFF15241A),
    onSurfaceMuted = Color(0xFF55665A),
    accent = Color(0xFF1A7748),
    headerStops = listOf(Color(0xFF0E7A4E), Color(0xFF3A7A22), Color(0xFF677619)),
    outgoingStops = listOf(Color(0xFF15804F), Color(0xFF467320)),
    incoming = Color(0xFFFFFFFF),
    spam = LightHebColors.spam,
    divider = Color(0xFFDAE6D3),
    onWhite = Color(0xFF1A7748),
)

/** Forest (dark). */
private val ForestDark = HebColors(
    isDark = true,
    background = Color(0xFF0A130D),
    surface = Color(0xFF131F17),
    surfaceVariant = Color(0xFF1C2C21),
    onSurface = Color(0xFFE6F2E8),
    onSurfaceMuted = Color(0xFF9CB4A2),
    accent = Color(0xFF7DD49A),
    headerStops = listOf(Color(0xFF0A5A3A), Color(0xFF2E5E1A), Color(0xFF56601A)),
    outgoingStops = listOf(Color(0xFF11704A), Color(0xFF3D6A1C)),
    incoming = Color(0xFF19291F),
    spam = DarkHebColors.spam,
    divider = Color(0xFF213327),
    onWhite = Color(0xFF0A5A3A),
)

/** Midnight (light): navy into indigo; even the light theme sits on a deeper blue-grey. */
private val MidnightLight = HebColors(
    isDark = false,
    background = Color(0xFFE3E7F3),
    surface = Color(0xFFF7F8FD),
    surfaceVariant = Color(0xFFD8DEEF),
    onSurface = Color(0xFF0E1430),
    onSurfaceMuted = Color(0xFF4C5478),
    accent = Color(0xFF3A3FB8),
    headerStops = listOf(Color(0xFF0F1B4C), Color(0xFF222A7A), Color(0xFF3B2F9E)),
    outgoingStops = listOf(Color(0xFF1E2A78), Color(0xFF4338CA)),
    incoming = Color(0xFFFFFFFF),
    spam = LightHebColors.spam,
    divider = Color(0xFFD0D7EA),
    onWhite = Color(0xFF1E2A78),
)

/** Midnight (dark). */
private val MidnightDark = HebColors(
    isDark = true,
    background = Color(0xFF070B1A),
    surface = Color(0xFF0F1530),
    surfaceVariant = Color(0xFF182042),
    onSurface = Color(0xFFE8EBFA),
    onSurfaceMuted = Color(0xFF9CA4C8),
    accent = Color(0xFF8F9BFF),
    headerStops = listOf(Color(0xFF0A1236), Color(0xFF1A1F5C), Color(0xFF2E2A80)),
    outgoingStops = listOf(Color(0xFF26307F), Color(0xFF4B3FB0)),
    incoming = Color(0xFF141B3A),
    spam = DarkHebColors.spam,
    divider = Color(0xFF1C2448),
    onWhite = Color(0xFF1A1F5C),
)

/** Pomegranate (light): deep garnet into pink, on a blush page. */
private val PomegranateLight = HebColors(
    isDark = false,
    background = Color(0xFFFBF1F3),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF5E2E7),
    onSurface = Color(0xFF2A1018),
    onSurfaceMuted = Color(0xFF765860),
    accent = Color(0xFFA3153F),
    headerStops = listOf(Color(0xFF7A1030), Color(0xFFA8174A), Color(0xFFD2407A)),
    outgoingStops = listOf(Color(0xFF8E1538), Color(0xFFC2356E)),
    incoming = Color(0xFFFFFFFF),
    spam = LightHebColors.spam,
    divider = Color(0xFFEFD9DF),
    onWhite = Color(0xFFA3153F),
)

/** Pomegranate (dark). */
private val PomegranateDark = HebColors(
    isDark = true,
    background = Color(0xFF160A0E),
    surface = Color(0xFF221118),
    surfaceVariant = Color(0xFF301822),
    onSurface = Color(0xFFF8E9EE),
    onSurfaceMuted = Color(0xFFC2A0AA),
    accent = Color(0xFFFF8FB1),
    headerStops = listOf(Color(0xFF5A0B22), Color(0xFF86153F), Color(0xFFA8305E)),
    outgoingStops = listOf(Color(0xFF7E1535), Color(0xFFA82A5E)),
    incoming = Color(0xFF2A1720),
    spam = DarkHebColors.spam,
    divider = Color(0xFF3A1F2A),
    onWhite = Color(0xFF7A1030),
)

/** Desert (light): burnt amber into golden orange, on warm sand. */
private val DesertLight = HebColors(
    isDark = false,
    background = Color(0xFFF8F1E4),
    surface = Color(0xFFFFFBF3),
    surfaceVariant = Color(0xFFF1E6D2),
    onSurface = Color(0xFF2B1E10),
    onSurfaceMuted = Color(0xFF6B5A45),
    accent = Color(0xFF9A4A0B),
    headerStops = listOf(Color(0xFF9A4A0B), Color(0xFFBE620E), Color(0xFFD27F12)),
    outgoingStops = listOf(Color(0xFF9A4A0B), Color(0xFFB4580C)),
    incoming = Color(0xFFFFFBF3),
    spam = LightHebColors.spam,
    divider = Color(0xFFEADCC4),
    onWhite = Color(0xFF9A4A0B),
)

/** Desert (dark). */
private val DesertDark = HebColors(
    isDark = true,
    background = Color(0xFF17110A),
    surface = Color(0xFF221A10),
    surfaceVariant = Color(0xFF2F2416),
    onSurface = Color(0xFFF6ECDD),
    onSurfaceMuted = Color(0xFFC2AE92),
    accent = Color(0xFFF2B45A),
    headerStops = listOf(Color(0xFF6B340A), Color(0xFF8E4A0D), Color(0xFFA8650F)),
    outgoingStops = listOf(Color(0xFF8A440C), Color(0xFFA85C10)),
    incoming = Color(0xFF2A2015),
    spam = DarkHebColors.spam,
    divider = Color(0xFF3A2C1C),
    onWhite = Color(0xFF6B340A),
)

/** Pastel (light): soft lilac into peach, on a pale blush page (deep enough under white text). */
private val PastelLight = HebColors(
    isDark = false,
    background = Color(0xFFFBF5F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3EAF5),
    onSurface = Color(0xFF2E2338),
    onSurfaceMuted = Color(0xFF6D6178),
    accent = Color(0xFF7A5FC0),
    headerStops = listOf(Color(0xFF8C78C6), Color(0xFFA97BB6), Color(0xFFC47E6A)),
    outgoingStops = listOf(Color(0xFF7C64BC), Color(0xFF9A6194)),
    incoming = Color(0xFFFFFFFF),
    spam = LightHebColors.spam,
    divider = Color(0xFFEEE3F0),
    onWhite = Color(0xFF7A5FC0),
)

/** Pastel (dark). */
private val PastelDark = HebColors(
    isDark = true,
    background = Color(0xFF17131C),
    surface = Color(0xFF211B28),
    surfaceVariant = Color(0xFF2C2434),
    onSurface = Color(0xFFF3ECF7),
    onSurfaceMuted = Color(0xFFB8ABC4),
    accent = Color(0xFFD2BFFF),
    headerStops = listOf(Color(0xFF5D4C8F), Color(0xFF7D5884), Color(0xFF9A6460)),
    outgoingStops = listOf(Color(0xFF6A5AA8), Color(0xFF8E5E8E)),
    incoming = Color(0xFF2A2233),
    spam = DarkHebColors.spam,
    divider = Color(0xFF352C3F),
    onWhite = Color(0xFF5D4C8F),
)

/** Graphite (light): graphite into near black, minimal, with a teal accent. */
private val GraphiteLight = HebColors(
    isDark = false,
    background = Color(0xFFF3F4F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EAEC),
    onSurface = Color(0xFF15181B),
    onSurfaceMuted = Color(0xFF5E656D),
    accent = Color(0xFF0F7C80),
    headerStops = listOf(Color(0xFF4A5058), Color(0xFF2E3238), Color(0xFF16181B)),
    outgoingStops = listOf(Color(0xFF3B4148), Color(0xFF24282D)),
    incoming = Color(0xFFFFFFFF),
    spam = LightHebColors.spam,
    divider = Color(0xFFE1E4E7),
    onWhite = Color(0xFF2E3238),
)

/** Graphite (dark). */
private val GraphiteDark = HebColors(
    isDark = true,
    background = Color(0xFF0E0F11),
    surface = Color(0xFF17191C),
    surfaceVariant = Color(0xFF212428),
    onSurface = Color(0xFFECEEF0),
    onSurfaceMuted = Color(0xFFA2A8AF),
    accent = Color(0xFF4FD1C5),
    headerStops = listOf(Color(0xFF3A3F46), Color(0xFF23262B), Color(0xFF0B0C0E)),
    outgoingStops = listOf(Color(0xFF3A4048), Color(0xFF2A2E34)),
    incoming = Color(0xFF1D2024),
    spam = DarkHebColors.spam,
    divider = Color(0xFF26292E),
    onWhite = Color(0xFF23262B),
)

/**
 * The app's colour skins, each with a light and a dark set. [id] is stable (for storing the choice); the order is
 * the order a picker shows them in. [Sunset] is the app's original look and the default.
 */
enum class Skin(val id: String, val light: HebColors, val dark: HebColors) {
    Sunset("sunset", LightHebColors, DarkHebColors),
    Ocean("ocean", OceanLight, OceanDark),
    Forest("forest", ForestLight, ForestDark),
    Midnight("midnight", MidnightLight, MidnightDark),
    Pomegranate("pomegranate", PomegranateLight, PomegranateDark),
    Desert("desert", DesertLight, DesertDark),
    Pastel("pastel", PastelLight, PastelDark),
    Graphite("graphite", GraphiteLight, GraphiteDark);

    fun colors(dark: Boolean): HebColors = if (dark) this.dark else light

    companion object {
        /** The skin stored under [id]; Sunset for an unknown or missing one. */
        fun fromId(id: String?): Skin = entries.firstOrNull { it.id == id } ?: Sunset
    }
}
