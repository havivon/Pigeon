package com.havivon.pigeon.ui.common

import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.Message
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.SpamVerdict
import com.havivon.pigeon.domain.model.ThreadItem
import com.havivon.pigeon.domain.model.UnreadMessage
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

    /** One conversation in every folder, a locked one among the banks, a spam one with unread messages, and a chat that is all read. */
    private val folderConversations = listOf(
        Conversation(1, "Mom", "", "", 0, unread = 2, folder = Folder.Personal),
        Conversation(2, "Leumi", "", "", 0, unread = 2, folder = Folder.Banks),
        Conversation(3, "Hapoalim", "", "", 0, unread = 1, folder = Folder.Banks, locked = true),
        Conversation(4, "Harel", "", "", 0, unread = 1, folder = Folder.Insurance),
        Conversation(5, "Verify", "", "", 0, unread = 1, folder = Folder.Codes),
        Conversation(6, "Couriers", "", "", 0, unread = 1, folder = Folder.Deliveries),
        Conversation(7, "Shop", "", "", 0, unread = 4, folder = Folder.All),
        Conversation(8, "Dana", "", "", 0, unread = 0, folder = Folder.Personal),
        Conversation(9, "Scam", "", "", 0, unread = 3, folder = Folder.Spam, spamReasons = listOf(SpamReason.UnknownSender)),
    )

    /** The unread messages behind [folderConversations]' counts, and a message of a chat that is not in the list yet. */
    private val folderMessages = listOf(
        sms(1, 1, "Bring bread", millis(2026, 11, 1, 10, 0), "0525550100", read = false),
        sms(2, 1, "and milk", millis(2026, 11, 1, 10, 5), "0525550100", read = false),
        sms(3, 2, "Charge of 20.00", millis(2026, 11, 1, 9, 0), "Leumi", read = false),
        sms(4, 2, "Charge of 30.00", millis(2026, 11, 1, 9, 5), "Leumi", read = false),
        sms(5, 3, "Balance 1,000.00", millis(2026, 11, 1, 9, 10), "Hapoalim", read = false),
        sms(6, 4, "Your policy was renewed", millis(2026, 11, 1, 8, 0), "Harel", read = false),
        sms(7, 5, "Your verification code is 482913", millis(2026, 11, 1, 7, 0), "Verify", read = false),
        sms(8, 6, "Your package is on its way", millis(2026, 11, 1, 6, 0), "Couriers", read = false),
        sms(9, 7, "Sale today", millis(2026, 11, 1, 5, 0), "Shop", read = false),
        sms(10, 7, "Sale ends soon", millis(2026, 11, 1, 5, 5), "Shop", read = false),
        sms(11, 7, "Last chance", millis(2026, 11, 1, 5, 10), "Shop", read = false),
        sms(12, 7, "Gone tonight", millis(2026, 11, 1, 5, 15), "Shop", read = false),
        sms(13, 9, "You won!", millis(2026, 11, 1, 4, 0), "+972551234567", read = false),
        sms(14, 9, "Claim now", millis(2026, 11, 1, 4, 5), "+972551234567", read = false),
        sms(15, 9, "Hurry", millis(2026, 11, 1, 4, 10), "+972551234567", read = false),
        sms(16, 77, "Hello", millis(2026, 11, 1, 11, 30), "0541112222", read = false),
    )

    private fun folderRows(folder: Folder, locked: Set<Long> = emptySet()) =
        mapper.mapUnread(folderMessages, folderConversations.associateBy { it.id }, emptyMap(), Now, locked = locked, folder = folder)

    @Test
    fun `the chips count unread messages per folder, with no chip for unread`() {
        val counts = folderCounts(folderConversations)
        assertEquals(setOf(Folder.All, Folder.Personal, Folder.Banks, Folder.Insurance, Folder.Codes, Folder.Deliveries, Folder.Spam), counts.keys)
        assertEquals(12, counts[Folder.All])
        assertEquals(2, counts[Folder.Personal])
        assertEquals(3, counts[Folder.Banks])
        assertEquals(1, counts[Folder.Insurance])
        assertEquals(1, counts[Folder.Codes])
        assertEquals(1, counts[Folder.Deliveries])
        // Spam counts conversations, as before
        assertEquals(1, counts[Folder.Spam])
        assertEquals(0, folderCounts(folderConversations.filter { it.unread == 0 }).getValue(Folder.All))
    }

    @Test
    fun `the unread messages of each folder are those of its conversations only`() {
        assertEquals(listOf(2L, 1L), folderRows(Folder.Personal).map { it.messageId })
        assertEquals(listOf(5L, 4L, 3L), folderRows(Folder.Banks).map { it.messageId })
        assertEquals(listOf(6L), folderRows(Folder.Insurance).map { it.messageId })
        assertEquals(listOf(7L), folderRows(Folder.Codes).map { it.messageId })
        assertEquals(listOf(8L), folderRows(Folder.Deliveries).map { it.messageId })
        // Every folder at once; the unclassified ones belong to it too
        assertEquals(listOf(16L, 5L, 4L, 3L, 2L, 1L, 6L, 7L, 8L, 12L, 11L, 10L, 9L).sorted(), folderRows(Folder.All).map { it.messageId }.sorted())
    }

    @Test
    fun `spam never shows in the unread messages of any folder`() {
        for (folder in Folder.entries.filter { it != Folder.Spam }) {
            assertTrue("$folder", folderRows(folder).none { it.threadId == 9L })
        }
        // A chat that just arrived has no folder yet: only the unfiltered list shows it
        assertTrue(folderRows(Folder.All).any { it.threadId == 77L })
        assertTrue(Folder.entries.filter { it != Folder.All }.all { f -> folderRows(f).none { it.threadId == 77L } })
    }

    @Test
    fun `a locked chat's unread messages stay in its folder, as the locked line`() {
        val banks = folderRows(Folder.Banks, locked = setOf(3L))
        assertEquals(listOf(5L, 4L, 3L), banks.map { it.messageId })
        val locked = banks.first { it.messageId == 5L }
        assertTrue(locked.locked)
        assertEquals("Locked chat", locked.text)
        assertTrue(folderRows(Folder.Personal, locked = setOf(3L)).none { it.locked })
    }

    @Test
    fun `the number on a folder is the number of its unread rows when nothing is capped`() {
        val counts = folderCounts(folderConversations)
        for (folder in listOf(Folder.Personal, Folder.Banks, Folder.Insurance, Folder.Codes, Folder.Deliveries)) {
            assertEquals("$folder", counts[folder], folderRows(folder).size)
        }
    }

    private val sources = listOf(
        conversation(1, "0525550100", name = "Mom", snippet = "Bring bread", dateMillis = millis(2026, 11, 1, 11, 0), unread = 2),
        conversation(2, "Verify", snippet = "Your verification code is 482913", dateMillis = millis(2026, 11, 1, 9, 0), unread = 1),
        conversation(3, "+972551234567", snippet = "You won! bit.ly/x7Kq9", dateMillis = millis(2026, 11, 1, 8, 0), unread = 1),
        conversation(4, "Leumi", snippet = "Charge of 249.90", dateMillis = millis(2026, 11, 1, 7, 0), unread = 1),
    )

    private fun mapUnread(
        unread: List<SmsMessage>,
        rules: List<ColorRule> = emptyList(),
        locked: Set<Long> = emptySet(),
        spam: Map<Long, SpamVerdict> = emptyMap(),
    ): List<UnreadMessage> {
        val conversations = sources.map { mapper.map(it, Now, rules, emptySet(), locked, spam[it.threadId]) }
        return mapper.mapUnread(unread, conversations.associateBy { it.id }, sources.associateBy { it.threadId }, Now, rules, locked)
    }

    private val unread = listOf(
        sms(10, 1, "Bring bread", millis(2026, 11, 1, 10, 0), "0525550100", read = false),
        sms(11, 1, "and milk", millis(2026, 11, 1, 11, 0), "0525550100", read = false),
        sms(12, 2, "Your verification code is 482913", millis(2026, 11, 1, 9, 0), "Verify", read = false),
        sms(13, 3, "You won! bit.ly/x7Kq9", millis(2026, 11, 1, 8, 0), "+972551234567", read = false),
        sms(14, 4, "Charge of 249.90 on the card ending 4821", millis(2026, 11, 1, 7, 0), "Leumi", read = false),
    )

    @Test
    fun `unread rows are one per message, newest first, with the sender's name and the time`() {
        val rows = mapUnread(unread)
        assertEquals(listOf(11L, 10L, 12L, 13L, 14L), rows.map { it.messageId })
        val newest = rows.first()
        assertEquals(1L, newest.threadId)
        assertEquals("Mom", newest.name)
        assertEquals("and milk", newest.text)
        assertEquals("11:00", newest.time)
        // A sender without a contact shows its address
        assertEquals("Verify", rows.first { it.messageId == 12L }.name)
    }

    @Test
    fun `the messages of a spam conversation are not listed, the others stay`() {
        val rows = mapUnread(unread, spam = mapOf(3L to SpamVerdict("+972551234567", 3, listOf(SpamReason.ShortLink), 0)))
        assertEquals(listOf(11L, 10L, 12L, 14L), rows.map { it.messageId })
    }

    @Test
    fun `the number on All is the number of listed rows when nothing is capped`() {
        val spamVerdicts = mapOf(3L to SpamVerdict("+972551234567", 3, listOf(SpamReason.ShortLink), 0))
        val conversations = sources.map { mapper.map(it, Now, emptyList(), emptySet(), emptySet(), spamVerdicts[it.threadId]) }
        assertEquals(mapUnread(unread, spam = spamVerdicts).size, folderCounts(conversations)[Folder.All])
        assertEquals(4, folderCounts(conversations)[Folder.All])
    }

    @Test
    fun `mapped from real conversations, a folder keeps the unread messages the classifier put there`() {
        val conversations = sources.map { mapper.map(it, Now) }
        val rows = mapper.mapUnread(unread, conversations.associateBy { it.id }, sources.associateBy { it.threadId }, Now, folder = Folder.Codes)
        assertEquals(listOf(12L), rows.map { it.messageId })
        val banks = mapper.mapUnread(unread, conversations.associateBy { it.id }, sources.associateBy { it.threadId }, Now, folder = Folder.Banks)
        assertEquals(listOf(14L), banks.map { it.messageId })
    }

    @Test
    fun `a locked conversation's messages show only the locked line, no code, and no keyword colour`() {
        val rules = listOf(
            ColorRule(7, "Codes", RuleMatch.Keyword("verification"), 0xFF14A3B8),
            ColorRule(8, "Mom", RuleMatch.Sender("Mom"), 0xFFE0559B),
        )
        val open = mapUnread(unread, rules).first { it.messageId == 12L }
        assertEquals("482913", open.code)
        assertEquals(7, open.ruleId)

        val rows = mapUnread(unread, rules, locked = setOf(1L, 2L))
        val verify = rows.first { it.messageId == 12L }
        assertTrue(verify.locked)
        assertEquals("Locked chat", verify.text)
        assertNull(verify.code)
        assertNull(verify.ruleId)
        // The name stays, and a sender rule still frames it
        val mom = rows.first { it.messageId == 11L }
        assertEquals("Locked chat", mom.text)
        assertEquals("Mom", mom.name)
        assertEquals(8, mom.ruleId)
    }

    @Test
    fun `each message gets the colour rule and the code of its own text`() {
        val rules = listOf(ColorRule(7, "Codes", RuleMatch.Keyword("verification"), 0xFF14A3B8), ColorRule(9, "Bank", RuleMatch.Sender("Leumi"), 0xFF1E9E6A))
        val rows = mapUnread(unread, rules)
        assertEquals(mapOf(11L to null, 10L to null, 12L to 7, 13L to null, 14L to 9), rows.associate { it.messageId to it.ruleId })
        assertEquals(mapOf(12L to "482913"), rows.filter { it.code != null }.associate { it.messageId to it.code })
    }

    @Test
    fun `a message whose conversation is not read yet shows its number`() {
        val rows = mapper.mapUnread(listOf(sms(50, 77, "Hello", millis(2026, 11, 1, 11, 30), "0541112222", read = false)), emptyMap(), emptyMap(), Now)
        assertEquals(1, rows.size)
        assertTrue(rows.single().name.contains("0541112222"))
    }

    @Test
    fun `a message that was read leaves the rows`() {
        assertEquals(5, mapUnread(unread).size)
        assertEquals(listOf(10L, 12L, 13L, 14L), mapUnread(unread.filter { it.id != 11L }).map { it.messageId })
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
