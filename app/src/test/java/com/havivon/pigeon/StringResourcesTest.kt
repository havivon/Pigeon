package com.havivon.pigeon

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every text of the app exists in English and in Hebrew, with the same placeholders, so no screen
 * falls back to the other language and no `%1$d` is ever left without a value (or crashes).
 */
class StringResourcesTest {

    private class Strings(val plain: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun read(folder: String): Strings {
        // Unit tests run in the module folder; from the project folder the path starts with app/
        val file = listOf(File("src/main/res/$folder/strings.xml"), File("app/src/main/res/$folder/strings.xml")).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val plain = LinkedHashMap<String, String>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i) as Element
            if (e.getAttribute("translatable") == "false") continue
            plain[e.getAttribute("name")] = e.textContent
        }
        val plurals = LinkedHashMap<String, Map<String, String>>()
        val nodes = doc.getElementsByTagName("plurals")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            val items = e.getElementsByTagName("item")
            plurals[e.getAttribute("name")] = (0 until items.length).associate { j ->
                val item = items.item(j) as Element
                item.getAttribute("quantity") to item.textContent
            }
        }
        return Strings(plain, plurals)
    }

    private val english = read("values")
    private val hebrew = read("values-iw")

    /** The placeholders a text uses, as "1$s", "2$d"; a lone "%%" is a percent sign, not a placeholder. */
    private fun placeholders(text: String): Set<String> =
        Regex("%(\\d+\\$)?[sdf]").findAll(text.replace("%%", "")).map { it.value.removePrefix("%") }.toSet()

    @Test
    fun `every text is in both languages`() {
        assertEquals("only in English", emptySet<String>(), english.plain.keys - hebrew.plain.keys)
        assertEquals("only in Hebrew", emptySet<String>(), hebrew.plain.keys - english.plain.keys)
        assertEquals("plurals only in English", emptySet<String>(), english.plurals.keys - hebrew.plurals.keys)
        assertEquals("plurals only in Hebrew", emptySet<String>(), hebrew.plurals.keys - english.plurals.keys)
    }

    @Test
    fun `both languages take the same placeholders`() {
        val wrong = english.plain.filter { (name, text) -> placeholders(text) != placeholders(hebrew.plain[name].orEmpty()) }.keys
        assertEquals(emptySet<String>(), wrong)
    }

    @Test
    fun `numbered placeholders whenever there are several`() {
        // With two or more values, each must say which it is: word order differs between the languages
        val all = english.plain + hebrew.plain.mapKeys { "iw:" + it.key }
        val unnumbered = all.filter { (_, text) -> placeholders(text).size > 1 && placeholders(text).any { !it.contains('$') } }.keys
        assertEquals(emptySet<String>(), unnumbered)
    }

    @Test
    fun `plurals have the forms each language needs and no strange placeholder`() {
        for ((name, items) in english.plurals) {
            assertTrue("$name: English needs one and other", items.keys.containsAll(listOf("one", "other")))
            val englishArgs = items.values.flatMap { placeholders(it) }.toSet()
            val hebrewItems = hebrew.plurals.getValue(name)
            assertTrue("$name: Hebrew needs one, two and other", hebrewItems.keys.containsAll(listOf("one", "two", "other")))
            for ((quantity, text) in hebrewItems) {
                // "הודעה אחת" may leave the number out, but never use one English does not pass
                assertTrue("$name/$quantity uses ${placeholders(text)}", englishArgs.containsAll(placeholders(text)))
            }
            assertEquals("$name: the other form shows the number in both", placeholders(items.getValue("other")), placeholders(hebrewItems.getValue("other")))
        }
    }

    @Test
    fun `no text is left empty`() {
        val empty = (english.plain + hebrew.plain.mapKeys { "iw:" + it.key }).filterValues { it.isBlank() }.keys
        assertEquals(emptySet<String>(), empty)
    }
}
