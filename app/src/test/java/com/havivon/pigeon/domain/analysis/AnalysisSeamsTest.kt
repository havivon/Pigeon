package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.Contact
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.seam.IncomingVerdict
import com.havivon.pigeon.domain.seam.StoredIncomingSms
import com.havivon.pigeon.fakes.FakeBlockedKeywords
import com.havivon.pigeon.fakes.FakeContacts
import com.havivon.pigeon.fakes.FakeSpam
import com.havivon.pigeon.fakes.conversation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisSeamsTest {

    private val scam = "זכית בפרס! לחץ כאן לקבלת המתנה: bit.ly/x7Kq9"

    private fun stored(address: String, body: String) = StoredIncomingSms(messageId = 1, threadId = 7, address = address, body = body, dateMillis = 0)

    @Test
    fun `a scam from a stranger is stored as spam and not notified`() = runTest {
        val spam = FakeSpam()
        val hook = SpamFilterHook(FakeContacts(), spam, FakeBlockedKeywords())
        assertEquals(IncomingVerdict.Silent, hook.onStored(stored("+972551234567", scam)))
        val verdict = spam.verdictState.value.single()
        assertEquals(7L, verdict.threadId)
        assertTrue(SpamReason.ShortLink in verdict.reasons)
    }

    @Test
    fun `someone the user wrote to is never spam, even without the contacts permission`() = runTest {
        val spam = FakeSpam()
        val hook = SpamFilterHook(FakeContacts(), spam, FakeBlockedKeywords()) { threadId -> threadId == 7L }
        assertEquals(IncomingVerdict.Notify, hook.onStored(stored("+972551234567", scam)))
        assertTrue(spam.verdictState.value.isEmpty())
    }

    @Test
    fun `a contact is never spam`() = runTest {
        val spam = FakeSpam()
        val hook = SpamFilterHook(FakeContacts(listOf(Contact(1, "Dana", listOf("0551234567")))), spam, FakeBlockedKeywords())
        assertEquals(IncomingVerdict.Notify, hook.onStored(stored("+972551234567", scam)))
        assertTrue(spam.verdictState.value.isEmpty())
    }

    @Test
    fun `a sender marked not spam is notified`() = runTest {
        val spam = FakeSpam()
        spam.markNotSpam("+972551234567")
        val hook = SpamFilterHook(FakeContacts(), spam, FakeBlockedKeywords())
        assertEquals(IncomingVerdict.Notify, hook.onStored(stored("0551234567", scam)))
    }

    @Test
    fun `a sender already in spam stays silent even with an innocent new message`() = runTest {
        val spam = FakeSpam()
        spam.markSpam("+972551234567", 7, listOf(SpamReason.UnknownSender))
        val hook = SpamFilterHook(FakeContacts(), spam, FakeBlockedKeywords())
        // The +972 / 05 forms are the same sender
        assertEquals(IncomingVerdict.Silent, hook.onStored(stored("0551234567", "נפגש מחר בעשר?")))
    }

    @Test
    fun `a new conversation of a sender already in spam joins its spam`() = runTest {
        val spam = FakeSpam()
        spam.markSpam("+972551234567", 3, listOf(SpamReason.UnknownSender))
        val hook = SpamFilterHook(FakeContacts(), spam, FakeBlockedKeywords())
        assertEquals(IncomingVerdict.Silent, hook.onStored(stored("+972551234567", "שלום")))
        assertTrue(spam.verdictState.value.any { it.threadId == 7L })
    }

    @Test
    fun `a thread already in spam stays silent`() = runTest {
        val spam = FakeSpam()
        spam.markSpam("PROMO", 7, listOf(SpamReason.UnknownSender))
        val hook = SpamFilterHook(FakeContacts(), spam, FakeBlockedKeywords())
        assertEquals(IncomingVerdict.Silent, hook.onStored(stored("PROMO", "עדכון")))
    }

    @Test
    fun `a blocked keyword sends a plain message to spam`() = runTest {
        val spam = FakeSpam()
        val hook = SpamFilterHook(FakeContacts(), spam, FakeBlockedKeywords(listOf("הלוואה")))
        assertEquals(IncomingVerdict.Silent, hook.onStored(stored("0551234567", "הלוואה מיידית בתנאים טובים")))
        assertEquals(SpamReason.BlockedKeyword("הלוואה"), spam.verdictState.value.single().reasons.first())
    }

    @Test
    fun `an ordinary message is notified`() = runTest {
        val hook = SpamFilterHook(FakeContacts(), FakeSpam(), FakeBlockedKeywords())
        assertEquals(IncomingVerdict.Notify, hook.onStored(stored("0551234567", "נפגש מחר בעשר?")))
    }

    @Test
    fun `the code finder is the code extractor`() {
        assertEquals("482913", AnalysisSeams.codeFinder.find("קוד האימות שלך הוא 482913"))
        assertNull(AnalysisSeams.codeFinder.find("נתראה בשמונה"))
    }

    @Test
    fun `folders come from the last message, never spam`() {
        assertEquals(Folder.Codes, AnalysisSeams.classifyFolder(conversation(1, "Verify", snippet = "Your verification code is 482913")))
        assertEquals(Folder.Personal, AnalysisSeams.classifyFolder(conversation(2, "0525550100", name = "Dana", snippet = "See you")))
        // A scam is not put in Spam by the folder: only a stored verdict does that
        assertNotEquals(Folder.Spam, AnalysisSeams.classifyFolder(conversation(3, "+972551234567", snippet = scam)))
    }
}
