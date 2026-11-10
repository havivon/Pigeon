package com.havivon.pigeon.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.havivon.pigeon.ui.theme.HebColors
import com.havivon.pigeon.ui.theme.Skin
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The widgets' per-skin resources (Glance cannot draw a Brush, so the gradients are shape drawables) are the skins'
 * own colours: day in `values/widget_colors.xml`, night in `values-night`, and every skin has its four drawables, each
 * pointing at its own skin's colours.
 */
class WidgetSkinResourcesTest {

    private fun res(path: String): File =
        listOf(File("src/main/res/$path"), File("app/src/main/res/$path")).first { it.exists() }

    private fun colors(folder: String): Map<String, Int> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(res("$folder/widget_colors.xml"))
        val nodes = doc.getElementsByTagName("color")
        return (0 until nodes.length).associate { i ->
            val e = nodes.item(i) as Element
            val hex = e.textContent.trim().removePrefix("#")
            e.getAttribute("name") to (if (hex.length == 6) "FF$hex" else hex).toLong(16).toInt()
        }
    }

    private fun check(colors: Map<String, Int>, skin: Skin, palette: HebColors, mode: String) {
        fun color(name: String) = colors["widget_${skin.id}_$name"] ?: error("No widget_${skin.id}_$name in $mode")
        assertEquals("${skin.id} $mode surface", palette.surface.toArgb(), color("surface"))
        assertEquals("${skin.id} $mode stops", 3, palette.headerStops.size)
        listOf("start", "center", "end").forEachIndexed { i, stop ->
            assertEquals("${skin.id} $mode gradient $stop", palette.headerStops[i].toArgb(), color("gradient_$stop"))
        }
    }

    @Test
    fun theWidgetColoursAreTheSkinsColours() {
        val day = colors("values")
        val night = colors("values-night")
        Skin.entries.forEach {
            check(day, it, it.light, "day")
            check(night, it, it.dark, "night")
        }
        assertEquals(Skin.Sunset.light.spam.toArgb(), day.getValue("widget_spam"))
        assertEquals(Skin.Sunset.dark.spam.toArgb(), night.getValue("widget_spam"))
    }

    @Test
    fun everySkinHasItsFourDrawablesOnItsOwnColours() {
        Skin.entries.forEach { skin ->
            listOf("widget_bg_surface", "widget_bg_gradient", "widget_bg_header", "widget_circle_gradient").forEach { kind ->
                val xml = res("drawable/${kind}_${skin.id}.xml").readText()
                val refs = Regex("@color/(widget_[a-z_]+)").findAll(xml).map { it.groupValues[1] }.toList()
                assertTrue("$kind for ${skin.id} uses colours", refs.isNotEmpty())
                refs.forEach { assertTrue("$kind for ${skin.id} uses $it", it.startsWith("widget_${skin.id}_")) }
            }
        }
    }

    @Test
    fun sunsetKeepsTheWidgetsOriginalColours() {
        val day = colors("values")
        val night = colors("values-night")
        assertEquals(Color(0xFF5B3FD9).toArgb(), day.getValue("widget_sunset_gradient_start"))
        assertEquals(Color(0xFFFFFFFF).toArgb(), day.getValue("widget_sunset_surface"))
        assertEquals(Color(0xFF3A2596).toArgb(), night.getValue("widget_sunset_gradient_start"))
        assertEquals(Color(0xFF1C1631).toArgb(), night.getValue("widget_sunset_surface"))
    }
}
