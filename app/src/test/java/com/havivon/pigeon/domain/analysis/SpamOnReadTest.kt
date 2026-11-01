package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.model.Contact
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.seam.IncomingMessageHook
import com.havivon.pigeon.fakes.FakeBlockedKeywords
import com.havivon.pigeon.fakes.FakeContacts
import com.havivon.pigeon.fakes.FakeMessageRepository
import com.havivon.pigeon.fakes.FakeSpam
import com.havivon.pigeon.fakes.conversation
import com.havivon.pigeon.fakes.sms
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A viewer's spam: judged when read, stored like an incoming verdict, and judged only once. */
class SpamOnReadTest {

    private val spam = FakeSpam()
    private val messages = FakeMessageRepository()
    private val contacts = FakeContacts(listOf(Contact(1, "Mom", listOf("0525550100"))))
    private var judgedCount = 0
    private val realHook = SpamFilterHook(contacts, spam, FakeBlockedKeywords()) { t -> messages.recentMessages(t, 50).any { it.type.outgoing } }
    private val hook = IncomingMessageHook { judgedCount++; realHook.onStored(it) }
    private val judge = SpamOnRead(hook) { t -> messages.recentMessages(t, 50).lastOrNull { !it.type.outgoing } }

    private val scam = conversation(4, "+972551234567", snippet = "You won! Claim now bit.ly/x7Kq9", dateMillis = 2_000)

    private fun seed() {
        messages.allMessages.value = listOf(
            sms(40, 4, "You won! Claim now bit.ly/x7Kq9", 2_000, address = "+972551234567"),
            sms(10, 1, "Bring bread", 1_000, address = "0525550100"),
            sms(20, 2, "You won! Claim now bit.ly/abc", 1_500, address = "0547770000"),
            sms(21, 2, "Haha, nice try", 1_600, address = "0547770000", type = SmsType.Sent),
        )
    }

    @Test
    fun `an unknown sender's spam is stored and so reaches the Spam folder`() = runTest {
        seed()
        judge.judge(listOf(scam), spam.verdicts.first())
        val v = spam.verdictState.value.single()
        assertEquals(4L, v.threadId)
        assertTrue(SpamReason.ShortLink in v.reasons)
    }

    @Test
    fun `contacts, people the user wrote to, groups and stored verdicts are left alone`() = runTest {
        seed()
        spam.markSpam("0539990000", 5, listOf(SpamReason.MarkedByUser))
        judge.judge(
            listOf(
                conversation(1, "0525550100", name = "Mom", dateMillis = 1_000),
                conversation(2, "0547770000", dateMillis = 1_600),
                conversation(5, "0539990000", dateMillis = 900),
                conversation(6, "0521111111", dateMillis = 800).copy(addresses = listOf("0521111111", "0522222222")),
            ),
            spam.verdicts.first(),
        )
        // Only the conversation the user wrote in reached the hook (which keeps it out); the rest were skipped
        assertEquals(1, judgedCount)
        assertEquals(listOf("0539990000"), spam.verdictState.value.map { it.address })
    }

    @Test
    fun `a conversation is judged once, and again only after a new message`() = runTest {
        seed()
        val clean = conversation(7, "0548880000", dateMillis = 3_000)
        messages.allMessages.value += sms(70, 7, "See you at the meeting", 3_000, address = "0548880000")
        judge.judge(listOf(clean), spam.verdicts.first())
        judge.judge(listOf(clean), spam.verdicts.first())
        assertEquals(1, judgedCount)
        assertTrue(spam.verdictState.value.isEmpty())
        messages.allMessages.value += sms(71, 7, "You won! Claim now bit.ly/zz", 4_000, address = "0548880000")
        judge.judge(listOf(clean.copy(dateMillis = 4_000)), spam.verdicts.first())
        assertEquals(2, judgedCount)
        assertEquals(1, spam.verdictState.value.size)
    }

    @Test
    fun `not spam sticks and the sender is never put back`() = runTest {
        seed()
        judge.judge(listOf(scam), spam.verdicts.first())
        spam.markNotSpam("+972551234567")
        val later = scam.copy(dateMillis = 5_000)
        messages.allMessages.value += sms(41, 4, "You won again! bit.ly/q", 5_000, address = "+972551234567")
        judge.judge(listOf(later), spam.verdicts.first())
        assertTrue(spam.verdictState.value.isEmpty())
    }

    private class Remembered : JudgedMemory {
        var stored: Map<Long, Long> = emptyMap()
        override fun load() = stored
        override fun save(judged: Map<Long, Long>) {
            stored = judged
        }
    }

    @Test
    fun `what was judged is remembered across a restart, and deleted conversations are forgotten`() = runTest {
        seed()
        val memory = Remembered()
        val clean = conversation(7, "0548880000", dateMillis = 3_000)
        messages.allMessages.value += sms(70, 7, "See you at the meeting", 3_000, address = "0548880000")
        SpamOnRead(hook, memory) { t -> messages.recentMessages(t, 50).lastOrNull { !it.type.outgoing } }.judge(listOf(clean, scam), spam.verdicts.first())
        assertEquals(2, judgedCount)
        assertEquals(mapOf(7L to 3_000L, 4L to 2_000L), memory.stored)
        // A new start: nothing is judged again, and the list without the scam forgets it
        val restarted = SpamOnRead(hook, memory) { t -> messages.recentMessages(t, 50).lastOrNull { !it.type.outgoing } }
        val newer = conversation(8, "0547776666", dateMillis = 6_000)
        messages.allMessages.value += sms(80, 8, "Hi", 6_000, address = "0547776666")
        restarted.judge(listOf(newer, clean), spam.verdicts.first())
        assertEquals(3, judgedCount)
        assertEquals(mapOf(7L to 3_000L, 8L to 6_000L), memory.stored)
    }
}
