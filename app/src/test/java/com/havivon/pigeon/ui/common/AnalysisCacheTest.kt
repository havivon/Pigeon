package com.havivon.pigeon.ui.common

import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.SmsConversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The folders kept from one launch to the next: by thread and fingerprint, without any text, and only for one version. */
class AnalysisCacheTest {

    @get:Rule
    val dir = TemporaryFolder()

    private val bank = SmsConversation(2, listOf("Leumi"), null, "Charge of 249.90 on your card", 900L, 0, hasFailed = false)
    private val mom = SmsConversation(1, listOf("0525550100"), "Mom", "Bring bread", 1_000L, 1, hasFailed = false, userWrote = true)

    @Test
    fun `a folder kept at one launch is there at the next, until the conversation changes`() {
        val file = dir.root.resolve("analysis.json")
        AnalysisCache(file, version = 41).apply {
            put(bank, Folder.Banks)
            put(mom, Folder.Personal)
            flush()
        }
        val next = AnalysisCache(file, version = 41)
        assertEquals(Folder.Banks, next.folderFor(bank))
        assertEquals(Folder.Personal, next.folderFor(mom))
        // A new last message, another contact name or the user writing: classified again
        assertNull(next.folderFor(bank.copy(snippet = "Your code is 4821")))
        assertNull(next.folderFor(mom.copy(contactName = "Mother")))
        assertNull(next.folderFor(mom.copy(userWrote = false)))
        assertNull(next.folderFor(bank.copy(threadId = 9)))
    }

    @Test
    fun `another version of the app starts over, and the file holds no text or sender`() {
        val file = dir.root.resolve("analysis.json")
        AnalysisCache(file, version = 41).apply {
            put(bank, Folder.Banks)
            flush()
        }
        val text = file.readText()
        assertFalse(text.contains("Leumi"))
        assertFalse(text.contains("249.90"))
        assertNull(AnalysisCache(file, version = 42).folderFor(bank))
        assertFalse(file.exists())
    }

    @Test
    fun `a broken file is dropped and nothing is kept from it`() {
        val file = dir.root.resolve("analysis.json")
        file.writeText("{broken")
        assertNull(AnalysisCache(file, version = 41).folderFor(bank))
        assertFalse(file.exists())
    }
}
