package com.havivon.pigeon.data

import com.havivon.pigeon.data.db.BinDatabase
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The recycle bin holds the text of deleted messages: it must stay out of the cloud backup and the transfer to
 * a new phone, while everything else backs up as before (only exclusions, never an include list).
 */
class BackupRulesTest {

    private fun file(path: String): File = listOf(File("src/main/$path"), File("app/src/main/$path")).first { it.exists() }

    private fun parse(path: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file(path)).documentElement

    private fun Element.children(tag: String): List<Element> =
        (0 until getElementsByTagName(tag).length).map { getElementsByTagName(tag).item(it) as Element }

    /** The bin's database file and the files SQLite keeps beside it. */
    private val binFiles = listOf("", "-shm", "-wal", "-journal").map { BinDatabase.NAME + it }.toSet()

    private fun excluded(section: Element): Set<String> =
        section.children("exclude").filter { it.getAttribute("domain") == "database" }.map { it.getAttribute("path") }.toSet()

    @Test
    fun `the manifest uses both sets of rules and keeps backup on`() {
        val app = parse("AndroidManifest.xml").children("application").single()
        assertEquals("true", app.getAttribute("android:allowBackup"))
        assertEquals("@xml/backup_rules", app.getAttribute("android:fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttribute("android:dataExtractionRules"))
    }

    @Test
    fun `android 11 and below leave out only the bin`() {
        val rules = parse("res/xml/backup_rules.xml")
        assertEquals("full-backup-content", rules.tagName)
        assertEquals(binFiles, excluded(rules))
        assertTrue("an include list would stop the rest backing up", rules.children("include").isEmpty())
        assertEquals(binFiles.size, rules.children("exclude").size)
    }

    @Test
    fun `android 12 and above leave out only the bin, in the cloud backup and in the device transfer`() {
        val rules = parse("res/xml/data_extraction_rules.xml")
        assertEquals("data-extraction-rules", rules.tagName)
        for (tag in listOf("cloud-backup", "device-transfer")) {
            val section = rules.children(tag).single()
            assertEquals(tag, binFiles, excluded(section))
            assertTrue(tag, section.children("include").isEmpty())
            assertEquals(tag, binFiles.size, section.children("exclude").size)
        }
    }
}
