package com.havivon.pigeon.ui.theme

import androidx.compose.ui.graphics.Color
import com.havivon.pigeon.domain.model.SkinIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2 relative luminance of an opaque colour. */
internal fun Color.wcagLuminance(): Double {
    fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

/** WCAG 2 contrast ratio between two opaque colours (1 to 21). */
internal fun contrast(a: Color, b: Color): Double {
    val la = a.wcagLuminance()
    val lb = b.wcagLuminance()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/**
 * Every skin, light and dark: body text 4.5:1 on its surfaces and bubbles, the muted text and the accent (links,
 * outlined buttons) 4.5:1 where they are drawn, white bubble text 4.5:1 on every outgoing stop, the white header
 * title 3:1 (large text) on every header stop, and the text on a white pill 4.5:1.
 */
class SkinContrastTest {

    /** Sunset is the app's original look and stays as it was: its coral end (2.57:1 under white) is the one known exception. */
    private val knownExceptions = setOf("Sunset light: white on header stop 2")

    private fun problems(name: String, c: HebColors): List<String> = buildList {
        fun need(what: String, ratio: Double, min: Double) {
            if (ratio < min && "$name: $what" !in knownExceptions) add("$name: $what is %.2f:1, needs %.1f:1".format(ratio, min))
        }
        listOf("background" to c.background, "surface" to c.surface, "surfaceVariant" to c.surfaceVariant, "incoming bubble" to c.incoming)
            .forEach { (label, bg) -> need("text on $label", contrast(c.onSurface, bg), 4.5) }
        listOf("background" to c.background, "surface" to c.surface, "incoming bubble" to c.incoming).forEach { (label, bg) ->
            need("muted text on $label", contrast(c.onSurfaceMuted, bg), 4.5)
            need("accent on $label", contrast(c.accent, bg), 4.5)
        }
        c.outgoingStops.forEachIndexed { i, stop -> need("white on outgoing stop $i", contrast(Color.White, stop), 4.5) }
        c.headerStops.forEachIndexed { i, stop -> need("white on header stop $i", contrast(Color.White, stop), 3.0) }
        need("text on a white pill", contrast(c.onWhite, Color.White), 4.5)
    }

    @Test
    fun everySkinIsReadable() {
        val all = Skin.entries.flatMap { problems("${it.name} light", it.light) + problems("${it.name} dark", it.dark) }
        assertTrue(all.joinToString("\n"), all.isEmpty())
    }

    @Test
    fun theKnownExceptionIsStillReal() {
        // If Sunset's coral is ever deepened, drop the exception
        assertTrue(contrast(Color.White, Skin.Sunset.light.headerStops[2]) < 3.0)
    }

    @Test
    fun sunsetIsTheOriginalLookAndTheDefault() {
        assertEquals(LightHebColors, Skin.Sunset.light)
        assertEquals(DarkHebColors, Skin.Sunset.dark)
        assertEquals(Skin.Sunset, Skin.fromId(null))
        assertEquals(Skin.Sunset, Skin.fromId("no-such-skin"))
    }

    @Test
    fun skinsAreDistinctAndKeepTheirIds() {
        assertEquals(Skin.entries.size, Skin.entries.map { it.id }.toSet().size)
        Skin.entries.forEach { assertEquals(it, Skin.fromId(it.id)) }
        assertEquals(Skin.entries.size, Skin.entries.map { it.light.headerStops }.toSet().size)
        Skin.entries.forEach {
            assertTrue(!it.light.isDark && it.dark.isDark)
            // Spam stays red in every skin
            assertEquals(LightHebColors.spam, it.light.spam)
            assertEquals(DarkHebColors.spam, it.dark.spam)
        }
    }

    @Test
    fun theStoredIdsAreTheSkins() {
        // The settings and the backup file know the skins by these ids only (SkinIds), in the picker's order
        assertEquals(SkinIds.ALL, Skin.entries.map { it.id })
        assertEquals(SkinIds.DEFAULT, Skin.Sunset.id)
        assertEquals(Skin.entries.size, Skin.entries.map { it.labelRes }.toSet().size)
        assertEquals("ocean", SkinIds.orDefault("ocean"))
        assertEquals(SkinIds.DEFAULT, SkinIds.orDefault("neon"))
        assertEquals(SkinIds.DEFAULT, SkinIds.orDefault(null))
    }

    /** Prints the measured ratios (run with --info to see them). */
    @Test
    fun report() {
        Skin.entries.forEach { skin ->
            listOf("light" to skin.light, "dark" to skin.dark).forEach { (mode, c) ->
                println(
                    "%-12s %-5s header>=%.2f outgoing>=%.2f text/bg=%.2f muted/bg=%.2f accent/surface=%.2f onWhite=%.2f".format(
                        skin.name, mode,
                        c.headerStops.minOf { contrast(Color.White, it) },
                        c.outgoingStops.minOf { contrast(Color.White, it) },
                        contrast(c.onSurface, c.background),
                        contrast(c.onSurfaceMuted, c.background),
                        contrast(c.accent, c.surface),
                        contrast(c.onWhite, Color.White),
                    ),
                )
            }
        }
    }
}
