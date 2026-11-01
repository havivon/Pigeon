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

    private fun row(id: Long, thread: Long, address: String, date: Long, type: SmsType = SmsType.Inbox, read: Boolean = true) =
        SmsSummaryRow(id, thread, address, date, read, type)

    // Unsorted on purpose: the provider's order must not matter
    private val rows = listOf(
        row(1, thread = 1, address = "+15555550100", date = 100, type = SmsType.Failed),
        row(2, thread = 2, address = "Leumi", date = 300, read = false),
        row(3, thread = 2, address = "Leumi", date = 250, read = false),
        row(4, thread = 2, address = "Leumi", date = 260, type = SmsType.Sent),
        row(5, thread = 3, address = "+15555550100", date = 200),
        row(6, thread = 3, address = "+15555550111", date = 150),
        row(7, thread = 1, address = "+15555550100", date = 90, read = false),
        row(8, thread = 4, address = "+15555550122", date = 500, type = SmsType.Draft),
        row(9, thread = 0, address = "+15555550133", date = 600),
    )

    @Test
    fun `conversations come from the SMS rows, newest first, without drafts or thread-less rows`() {
        val list = summarizeThreads(rows)
        assertEquals(listOf(2L, 3L, 1L), list.map { it.threadId })

        val leumi = list[0]
        assertEquals(2L, leumi.latestId)
        assertEquals(listOf("Leumi"), leumi.addresses)
        assertEquals(300L, leumi.dateMillis)
        // Only unread incoming messages count; the sent one does not
        assertEquals(2, leumi.unreadCount)
        assertFalse(leumi.hasFailed)

        val dana = list[2]
        assertEquals(1L, dana.latestId)
        assertTrue(dana.hasFailed)
        assertEquals(1, dana.unreadCount)
    }

    @Test
    fun `a thread with several numbers lists them newest first, once each`() {
        val group = summarizeThreads(rows).first { it.threadId == 3L }
        assertEquals(listOf("+15555550100", "+15555550111"), group.addresses)
    }

    @Test
    fun `a group shows names where known and numbers for the rest`() {
        val group = summarizeThreads(rows).first { it.threadId == 3L }.toConversation("hi", mapOf("+15555550100" to "Dana"))
        assertEquals("Dana, +15555550111", group.contactName)
        assertEquals("hi", group.snippet)
    }

    @Test
    fun `no known names leaves the title to the number`() {
        val leumi = summarizeThreads(rows).first().toConversation("charge", emptyMap())
        assertNull(leumi.contactName)
        assertEquals("Leumi", leumi.title)
    }

    @Test
    fun `each conversation counts its incoming messages and knows whether the user wrote in it`() {
        val list = summarizeThreads(rows).associateBy { it.threadId }
        assertEquals(2, list.getValue(2).incomingCount)
        assertTrue(list.getValue(2).userWrote)
        assertEquals(2, list.getValue(3).incomingCount)
        assertFalse(list.getValue(3).userWrote)
        // A failed send is the user writing too; the draft of thread 4 is no conversation at all
        assertTrue(list.getValue(1).userWrote)
        val conversation = list.getValue(2).toConversation("hi", emptyMap())
        assertEquals(2, conversation.incomingCount)
        assertTrue(conversation.userWrote)
    }

    @Test
    fun `hidden threads are left out`() {
        assertEquals(listOf(3L, 1L), summarizeThreads(rows, hidden = setOf(2L)).map { it.threadId })
    }

    @Test
    fun `the same date picks the higher id as the newest`() {
        val tie = summarizeThreads(listOf(row(10, 5, "A", 100), row(11, 5, "A", 100))).single()
        assertEquals(11L, tie.latestId)
    }

    @Test
    fun `no rows, no conversations`() {
        assertTrue(summarizeThreads(emptyList()).isEmpty())
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
