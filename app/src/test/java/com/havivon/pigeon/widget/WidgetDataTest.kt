package com.havivon.pigeon.widget

import com.havivon.pigeon.domain.analysis.AnalysisSeams
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Favorite
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.seam.CodeFinder
import com.havivon.pigeon.fakes.Now
import com.havivon.pigeon.fakes.englishLabels
import com.havivon.pigeon.fakes.millis
import com.havivon.pigeon.fakes.sms
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun conv(
    id: Long,
    name: String,
    unread: Int = 0,
    snippet: String = "hello from $name",
    pinned: Boolean = false,
    locked: Boolean = false,
    spam: Boolean = false,
    folder: Folder = Folder.Personal,
    ruleId: Int? = null,
) = Conversation(
    id, name, snippet, "10:00", 0xFF2F80ED, unread = unread, pinned = pinned, ruleId = ruleId, folder = folder,
    spamReasons = if (spam) listOf(SpamReason.UnknownSender) else emptyList(), locked = locked,
)

class WidgetDataTest {

    private val rules = listOf(
        ColorRule(1, "Bank", RuleMatch.Sender("Bank"), 0xFF1E9E6A),
        ColorRule(2, "Off", RuleMatch.Sender("Off"), 0xFFF08A24, enabled = false),
    )

    // The unread list

    @Test
    fun `the unread list has the unread conversations with their rule strips`() {
        val data = WidgetDataBuilder.unread(
            listOf(conv(1, "Mom", unread = 2), conv(2, "Bank", unread = 1, ruleId = 1), conv(3, "Read one")),
            rules, showText = true,
        )
        assertEquals(listOf(1L, 2L), data.rows.map { it.threadId })
        assertEquals(3, data.totalUnread)
        assertNull(data.rows[0].ruleArgb)
        assertEquals(0xFF1E9E6A, data.rows[1].ruleArgb)
        assertEquals("hello from Mom", data.rows[0].snippet)
    }

    @Test
    fun `a switched-off rule draws no strip`() {
        val data = WidgetDataBuilder.unread(listOf(conv(1, "Off", unread = 1, ruleId = 2)), rules, showText = true)
        assertNull(data.rows.single().ruleArgb)
    }

    @Test
    fun `locked conversations are hidden from the unread list and its total`() {
        val data = WidgetDataBuilder.unread(
            listOf(conv(1, "Secret", unread = 5, snippet = "Locked chat", locked = true), conv(2, "Mom", unread = 1)),
            rules, showText = true,
        )
        assertEquals(listOf(2L), data.rows.map { it.threadId })
        assertEquals(1, data.totalUnread)
        assertTrue(data.rows.none { it.name == "Secret" })
    }

    @Test
    fun `spam is not in the unread list`() {
        val data = WidgetDataBuilder.unread(listOf(conv(1, "+972 55", unread = 3, spam = true), conv(2, "Mom", unread = 1)), rules, showText = true)
        assertEquals(listOf(2L), data.rows.map { it.threadId })
    }

    @Test
    fun `counts-only mode carries no message text`() {
        val data = WidgetDataBuilder.unread(listOf(conv(1, "Mom", unread = 2, snippet = "the secret plan")), rules, showText = false)
        assertEquals("", data.rows.single().snippet)
        // The row still says who and how many
        assertEquals("Mom", data.rows.single().name)
        assertEquals(2, data.rows.single().unread)
    }

    @Test
    fun `the unread list is capped`() {
        val many = (1L..30L).map { conv(it, "C$it", unread = 1) }
        val data = WidgetDataBuilder.unread(many, rules, showText = true)
        assertEquals(WidgetDataBuilder.MAX_ROWS, data.rows.size)
        assertEquals(30, data.totalUnread)
    }

    // The latest code

    private val codeFinder = AnalysisSeams.codeFinder

    @Test
    fun `the latest code is the newest incoming message that has one`() {
        val incoming = listOf(
            sms(3, 1, "See you at 5", Now - 60_000),
            sms(2, 2, "Your verification code is 482913", Now - 5 * 60_000),
            sms(1, 3, "Your verification code is 111222", Now - 90 * 60_000),
        )
        val data = WidgetDataBuilder.code(incoming, emptySet(), codeFinder, { "Sender ${it.threadId}" }, Now)!!
        assertEquals("482913", data.code)
        assertEquals("Sender 2", data.sender)
        assertEquals(2L, data.threadId)
        assertEquals(5L, data.ageMinutes)
    }

    @Test
    fun `codes in locked or spam conversations are never shown`() {
        val incoming = listOf(
            sms(2, 7, "Your verification code is 482913", Now - 60_000),
            sms(1, 8, "Your verification code is 111222", Now - 120_000),
        )
        assertEquals("111222", WidgetDataBuilder.code(incoming, setOf(7L), codeFinder, { "x" }, Now)!!.code)
        assertNull(WidgetDataBuilder.code(incoming, setOf(7L, 8L), codeFinder, { "x" }, Now))
    }

    @Test
    fun `no code means no data, and sent messages are skipped`() {
        assertNull(WidgetDataBuilder.code(emptyList(), emptySet(), codeFinder, { "x" }, Now))
        val sent = listOf(sms(1, 1, "Your verification code is 482913", Now, type = SmsType.Sent))
        assertNull(WidgetDataBuilder.code(sent, emptySet(), codeFinder, { "x" }, Now))
    }

    @Test
    fun `the age of a code never goes below zero`() {
        val data = WidgetDataBuilder.code(listOf(sms(1, 1, "code 123456", Now + 10_000)), emptySet(), CodeFinder { "123456" }, { "x" }, Now)!!
        assertEquals(0L, data.ageMinutes)
    }

    @Test
    fun `counts-only mode draws the code as bullets`() {
        assertEquals("482913", displayCode("482913", showContent = true))
        assertEquals("••••••", displayCode("482913", showContent = false))
    }

    // Quick contacts

    @Test
    fun `quick contacts are the pinned ones, then recent personal ones`() {
        val data = WidgetDataBuilder.contacts(
            listOf(
                conv(1, "Recent"),
                conv(2, "Pinned", pinned = true),
                conv(3, "Bank", folder = Folder.Banks),
                conv(4, "Older"),
                conv(5, "Pinned bank", pinned = true, folder = Folder.Banks),
            ),
        )
        assertEquals(listOf(2L, 5L, 1L, 4L), data.contacts.map { it.threadId })
    }

    @Test
    fun `quick contacts leave out locked chats, spam, and anything past five`() {
        val data = WidgetDataBuilder.contacts(
            listOf(conv(1, "Locked", pinned = true, locked = true), conv(2, "Junk", spam = true)) + (3L..12L).map { conv(it, "P$it") },
        )
        assertEquals((3L..7L).toList(), data.contacts.map { it.threadId })
    }

    // The counter

    @Test
    fun `the counter counts unread messages and spam conversations`() {
        val data = WidgetDataBuilder.counter(
            listOf(conv(1, "A", unread = 2), conv(2, "B", unread = 1, folder = Folder.Banks), conv(3, "Junk", unread = 4, spam = true), conv(4, "Junk 2", spam = true)),
        )
        assertEquals(CounterWidgetData(unread = 3, spam = 2), data)
    }

    @Test
    fun `the counter is only a number, so it counts locked chats like the inbox badge does`() {
        val data = WidgetDataBuilder.counter(listOf(conv(1, "Secret", unread = 2, locked = true), conv(2, "A", unread = 1)))
        assertEquals(3, data.unread)
    }

    // Favourites

    private fun favorite(id: Long, thread: Long, name: String?, body: String) =
        Favorite(id, id, thread, "0525550100", name, body, millis(2026, 10, 20, 10, 0), starredAtMillis = id)

    @Test
    fun `favourites show the sender, the date and the text`() {
        val data = WidgetDataBuilder.favorites(listOf(favorite(1, 5, "Danny", "12 Maple Street")), emptySet(), englishLabels(), showText = true)
        val row = data.rows.single()
        assertEquals(WidgetFavorite(5, "Danny", "12 Maple Street", "10/20/26", row.avatarArgb), row)
    }

    @Test
    fun `a number that is not a contact is shown as the number`() {
        val data = WidgetDataBuilder.favorites(listOf(favorite(1, 5, null, "text")), emptySet(), englishLabels(), showText = true)
        assertEquals("0525550100", data.rows.single().sender)
    }

    @Test
    fun `favourites from locked chats are hidden`() {
        val data = WidgetDataBuilder.favorites(
            listOf(favorite(1, 5, "Danny", "locked text"), favorite(2, 6, "Mom", "fine")), setOf(5L), englishLabels(), showText = true,
        )
        assertEquals(listOf(6L), data.rows.map { it.threadId })
    }

    @Test
    fun `counts-only mode hides the starred text`() {
        val data = WidgetDataBuilder.favorites(listOf(favorite(1, 5, "Danny", "12 Maple Street")), emptySet(), englishLabels(), showText = false)
        assertEquals("", data.rows.single().text)
        assertEquals("Danny", data.rows.single().sender)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetRefreshSignalsTest {

    @Test
    fun `a burst of changes becomes one update after the quiet time`() = runTest {
        val messages = MutableStateFlow(0)
        val settings = MutableStateFlow("a")
        val signals = mutableListOf<Unit>()
        backgroundScope.launch { refreshSignals(messages, settings, quietMillis = 1_000).collect { signals += it } }
        runCurrent()
        // The initial values count as one change
        advanceTimeBy(1_001)
        assertEquals(1, signals.size)

        messages.value = 1
        advanceTimeBy(300)
        messages.value = 2
        advanceTimeBy(300)
        settings.value = "b"
        advanceTimeBy(900)
        assertEquals(1, signals.size)
        advanceTimeBy(200)
        assertEquals(2, signals.size)
    }

    @Test
    fun `any one source is enough to update`() = runTest {
        val favorites = MutableStateFlow(0)
        val locks = MutableStateFlow(0)
        val signals = mutableListOf<Unit>()
        backgroundScope.launch { refreshSignals(favorites, locks, quietMillis = 500).collect { signals += it } }
        advanceTimeBy(600)
        val base = signals.size
        locks.value = 1
        advanceTimeBy(600)
        assertEquals(base + 1, signals.size)
        favorites.value = 1
        advanceTimeBy(600)
        assertEquals(base + 2, signals.size)
    }
}
