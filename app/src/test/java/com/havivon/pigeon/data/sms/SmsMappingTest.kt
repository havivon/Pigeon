package com.havivon.pigeon.data.sms

import com.havivon.pigeon.domain.model.SmsType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsMappingTest {

    @Test
    fun `parts of one message are joined in order`() {
        val joined = joinParts(
            listOf(
                PduPart("+15555550100", "The first part, ", 1_000),
                PduPart("+15555550100", "the second part, ", 1_001),
                PduPart("+15555550100", "and the end.", 1_002),
            ),
        )
        assertEquals(listOf(IncomingSms("+15555550100", "The first part, the second part, and the end.", 1_000)), joined)
    }

    @Test
    fun `parts from two senders become two messages, in the order they came`() {
        val joined = joinParts(
            listOf(
                PduPart("B", "b1", 5),
                PduPart("A", "a1", 7),
                PduPart("B", "b2", 6),
            ),
        )
        assertEquals(listOf(IncomingSms("B", "b1b2", 5), IncomingSms("A", "a1", 7)), joined)
    }

    @Test
    fun `a single part stays as it is, Hebrew and emoji included`() {
        val joined = joinParts(listOf(PduPart("5555", "שלום 👋", 9)))
        assertEquals("שלום 👋", joined.single().body)
    }

    @Test
    fun `no parts, no messages`() {
        assertTrue(joinParts(emptyList()).isEmpty())
    }

    @Test
    fun `recipient ids are space separated`() {
        assertEquals(listOf(3L, 14L), parseRecipientIds("3 14"))
        assertEquals(listOf(7L), parseRecipientIds(" 7  x "))
        assertTrue(parseRecipientIds(null).isEmpty())
    }

    private val rows = listOf(
        ThreadRow(id = 1, dateMillis = 100, snippet = "old", recipientIds = listOf(10), messageCount = 2),
        ThreadRow(id = 2, dateMillis = 300, snippet = "new", recipientIds = listOf(20), messageCount = 1),
        ThreadRow(id = 3, dateMillis = 200, snippet = "group", recipientIds = listOf(10, 30), messageCount = 4),
        ThreadRow(id = 4, dateMillis = 400, snippet = "", recipientIds = listOf(20), messageCount = 0),
    )
    private val canonical = mapOf(10L to "+15555550100", 20L to "Leumi", 30L to "+15555550111")

    @Test
    fun `conversations are assembled newest first, without empty threads`() {
        val list = assembleConversations(rows, canonical, mapOf(2L to 3), setOf(1L), mapOf("+15555550100" to "Dana"))
        assertEquals(listOf(2L, 3L, 1L), list.map { it.threadId })

        val leumi = list[0]
        assertEquals(listOf("Leumi"), leumi.addresses)
        assertNull(leumi.contactName)
        assertEquals("Leumi", leumi.title)
        assertEquals(3, leumi.unreadCount)
        assertFalse(leumi.hasFailed)

        val dana = list[2]
        assertEquals("Dana", dana.contactName)
        assertTrue(dana.hasFailed)
        assertEquals(0, dana.unreadCount)
    }

    @Test
    fun `a group shows names where known and numbers for the rest`() {
        val group = assembleConversations(rows, canonical, emptyMap(), emptySet(), mapOf("+15555550100" to "Dana")).first { it.threadId == 3L }
        assertEquals("Dana, +15555550111", group.contactName)
    }

    @Test
    fun `hidden threads are left out`() {
        val list = assembleConversations(rows, canonical, emptyMap(), emptySet(), emptyMap(), hidden = setOf(2L))
        assertEquals(listOf(3L, 1L), list.map { it.threadId })
    }

    @Test
    fun `a recipient missing from the canonical table is skipped`() {
        val list = assembleConversations(listOf(ThreadRow(9, 1, "x", listOf(99, 10), 1)), canonical, emptyMap(), emptySet(), emptyMap())
        assertEquals(listOf("+15555550100"), list.single().addresses)
    }

    @Test
    fun `a single-part message is sent on success`() {
        assertEquals(SmsType.Sent, typeAfterSendResult(SmsType.Outbox, success = true, isLastPart = true))
    }

    @Test
    fun `an early part's success changes nothing yet`() {
        assertNull(typeAfterSendResult(SmsType.Outbox, success = true, isLastPart = false))
    }

    @Test
    fun `any failed part fails the message`() {
        assertEquals(SmsType.Failed, typeAfterSendResult(SmsType.Outbox, success = false, isLastPart = false))
        assertEquals(SmsType.Failed, typeAfterSendResult(SmsType.Outbox, success = false, isLastPart = true))
    }

    @Test
    fun `a later success does not undo an earlier failure`() {
        assertNull(typeAfterSendResult(SmsType.Failed, success = true, isLastPart = true))
        assertNull(typeAfterSendResult(SmsType.Failed, success = false, isLastPart = true))
    }

    @Test
    fun `like pattern takes wildcards literally`() {
        assertEquals("%50\\% off%", likeContains("50% off"))
        assertEquals("%a\\_b\\\\c%", likeContains("a_b\\c"))
        assertEquals("%קוד%", likeContains("קוד"))
    }

    @Test
    fun `unknown provider types read as inbox`() {
        assertEquals(SmsType.Failed, SmsType.fromProvider(5))
        assertEquals(SmsType.Inbox, SmsType.fromProvider(0))
        assertEquals(SmsType.Inbox, SmsType.fromProvider(42))
        assertFalse(SmsType.Inbox.outgoing)
        assertTrue(SmsType.Queued.outgoing)
    }
}
