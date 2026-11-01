package com.havivon.pigeon.ui.common

import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.Message
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.SpamVerdict
import com.havivon.pigeon.domain.model.ThreadItem
import com.havivon.pigeon.fakes.Now
import com.havivon.pigeon.fakes.Utc
import com.havivon.pigeon.fakes.conversation
import com.havivon.pigeon.fakes.englishLabels
import com.havivon.pigeon.fakes.millis
import com.havivon.pigeon.fakes.sms
import com.havivon.pigeon.fakes.testMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingTest {

    private val labels = englishLabels()
    private val mapper = testMapper(labels)

    @Test
    fun `day separators go before each new day, oldest first`() {
        val list = listOf(
            sms(1, 1, "a", millis(2026, 10, 30, 9, 0)),
            sms(2, 1, "b", millis(2026, 10, 31, 9, 0)),
            sms(3, 1, "c", millis(2026, 10, 31, 20, 0)),
            sms(4, 1, "d", millis(2026, 11, 1, 8, 0)),
        )
        val items = threadItems(list, labels, Now) { Message(it.id, it.body, labels.time(it.dateMillis), it.type.outgoing) }
        val shape = items.map { if (it is ThreadItem.Day) "[${it.label}]" else (it as ThreadItem.Msg).message.text }
        assertEquals(listOf("[Friday]", "a", "[Yesterday]", "b", "c", "[Today]", "d"), shape)
    }

    @Test
    fun `a conversation gets its name, time, code, folder and rule`() {
        val rules = listOf(ColorRule(7, "Codes", RuleMatch.Keyword("verification"), 0xFF14A3B8))
        val c = mapper.map(conversation(5, "Verify", snippet = "Your verification code is 482913", dateMillis = millis(2026, 11, 1, 8, 50), unread = 1), Now, rules)
        assertEquals("Verify", c.name)
        assertEquals("08:50", c.time)
        assertEquals("482913", c.code)
        assertEquals(Folder.Codes, c.folder)
        assertEquals(7, c.ruleId)
        assertEquals(1, c.unread)
    }

    @Test
    fun `a locked conversation shows nothing of its text`() {
        val c = mapper.map(conversation(5, "Verify", snippet = "Your verification code is 482913"), Now, locked = setOf(5L))
        assertEquals("Locked chat", c.snippet)
        assertNull(c.code)
        assertTrue(c.locked)
    }

    @Test
    fun `a stored verdict makes it spam, with its reasons`() {
        val verdicts = listOf(SpamVerdict("+972551234567", 9, listOf(SpamReason.ShortLink), 0))
        val sms = conversation(3, "055-123-4567", snippet = "bit.ly/x")
        val v = verdictFor(sms, verdicts)
        val c = mapper.map(sms, Now, verdict = v)
        assertEquals(Folder.Spam, c.folder)
        assertEquals(listOf(SpamReason.ShortLink), c.spamReasons)
        assertEquals(SpamArgb, c.avatarArgb)
        assertNull(verdictFor(conversation(4, "0525550100"), verdicts))
    }

    @Test
    fun `folder chips count unread messages, spam counts conversations`() {
        val all = listOf(
            Conversation(1, "Mom", "", "", 0, unread = 2, folder = Folder.Personal),
            Conversation(2, "Bank", "", "", 0, unread = 1, folder = Folder.Banks),
            Conversation(3, "Shop", "", "", 0, unread = 4, folder = Folder.All),
            Conversation(4, "Scam", "", "", 0, unread = 3, folder = Folder.Spam, spamReasons = listOf(SpamReason.UnknownSender)),
            Conversation(5, "Scam 2", "", "", 0, folder = Folder.Spam, spamReasons = listOf(SpamReason.ShortLink)),
        )
        val counts = folderCounts(all)
        assertEquals(7, counts[Folder.All])
        assertEquals(2, counts[Folder.Personal])
        assertEquals(1, counts[Folder.Banks])
        assertEquals(0, counts[Folder.Codes])
        assertEquals(2, counts[Folder.Spam])
        assertEquals(listOf(1L, 2L, 3L), inFolder(all, Folder.All).map { it.id })
        assertEquals(listOf(2L), inFolder(all, Folder.Banks).map { it.id })
        assertEquals(listOf(4L, 5L), inFolder(all, Folder.Spam).map { it.id })
    }

    @Test
    fun `the unread chip holds every folder's unread conversations but spam, and counts conversations`() {
        val all = listOf(
            Conversation(1, "Mom", "", "", 0, unread = 2, folder = Folder.Personal),
            Conversation(2, "Bank", "", "", 0, unread = 1, folder = Folder.Banks),
            Conversation(3, "Shop", "", "", 0, unread = 4, folder = Folder.All),
            Conversation(4, "Dana", "", "", 0, unread = 0, folder = Folder.Personal),
            Conversation(5, "Scam", "", "", 0, unread = 3, folder = Folder.Spam, spamReasons = listOf(SpamReason.UnknownSender)),
        )
        assertEquals(listOf(1L, 2L, 3L), inFolder(all, Folder.Unread).map { it.id })
        assertEquals(3, folderCounts(all)[Folder.Unread])
        assertEquals(0, folderCounts(all.filter { it.unread == 0 })[Folder.Unread])
        assertEquals(emptyList<Conversation>(), inFolder(all.filter { it.unread == 0 || it.isSpam }, Folder.Unread))
    }

    @Test
    fun `copied messages are oldest first, one line each`() {
        val list = listOf(
            sms(2, 1, "See you there", millis(2026, 11, 1, 11, 5), type = SmsType.Sent),
            sms(1, 1, "Address: 12 Maple Street", millis(2026, 11, 1, 11, 2)),
        )
        assertEquals("[11:02] Danny: Address: 12 Maple Street\n[11:05] Me: See you there", formatForCopy(list, labels, "Me") { "Danny" })
    }

    @Test
    fun `copied messages from several days carry the date`() {
        val list = listOf(sms(1, 1, "a", millis(2026, 10, 31, 9, 0)), sms(2, 1, "b", millis(2026, 11, 1, 9, 0)))
        // The name loses its direction marks on the clipboard
        assertEquals("[10/31/26 09:00] 052: a\n[11/1/26 09:00] 052: b", formatForCopy(list, labels, "Me") { ltr("052") })
    }

    @Test
    fun `a reminder is read from the day the message was written`() {
        // Written yesterday: "tomorrow at 10" is today at 10, still ahead at 09:00
        val written = millis(2026, 10, 31, 18, 0)
        val r = findReminder("Let's meet tomorrow at 10:00", written, millis(2026, 11, 1, 9, 0), Utc)
        assertEquals(millis(2026, 11, 1, 10, 0), r?.atMillis)
        // Once that time has passed there is nothing to remind
        assertNull(findReminder("Let's meet tomorrow at 10:00", written, Now, Utc))
    }

    @Test
    fun `send states come from the message type`() {
        assertEquals(com.havivon.pigeon.domain.model.SendStatus.Sending, sendStatus(SmsType.Outbox))
        assertEquals(com.havivon.pigeon.domain.model.SendStatus.Failed, sendStatus(SmsType.Failed))
        assertEquals(com.havivon.pigeon.domain.model.SendStatus.Done, sendStatus(SmsType.Sent))
    }
}
