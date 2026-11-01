package com.havivon.pigeon.ui.spam

import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.SpamReason
import org.junit.Assert.assertEquals
import org.junit.Test

// Made-up senders and messages only.
class SpamFilterTest {

    private fun spamConversation(id: Long, address: String, snippet: String, locked: Boolean = false) = Conversation(
        id = id,
        name = address,
        snippet = if (locked) "Locked chat" else snippet,
        time = "10:00",
        avatarArgb = 0,
        unread = 0,
        folder = Folder.Spam,
        spamReasons = listOf(SpamReason.UnknownSender),
        addresses = listOf(address),
        locked = locked,
    )

    private val groups = groupSpam(
        listOf(
            spamConversation(1, "+972551234567", "You won a prize"),
            spamConversation(2, "Polls", "Quick poll about the election"),
            spamConversation(3, "0529990000", "The secret offer", locked = true),
        ),
    )

    private fun keys(query: String, threads: Set<Long> = emptySet()) = filterSpam(groups, query, threads).map { it.conversations.first().id }

    @Test
    fun `an empty query shows every sender`() {
        assertEquals(listOf(1L, 2L, 3L), keys(""))
        assertEquals(listOf(1L, 2L, 3L), keys("   "))
    }

    @Test
    fun `senders are found by name or by part of the number, written either way`() {
        assertEquals(listOf(2L), keys("POLLS"))
        assertEquals(listOf(1L), keys("055-123"))
        assertEquals(listOf(1L), keys("1234567"))
        assertEquals(listOf(3L), keys("0529990000"))
    }

    @Test
    fun `messages are found by their text, but never a locked chat's`() {
        assertEquals(listOf(1L), keys("prize"))
        assertEquals(listOf(2L), keys("election"))
        assertEquals(emptyList<Long>(), keys("secret"))
        // A thread the message search found
        assertEquals(listOf(2L), keys("older words", threads = setOf(2L)))
        assertEquals(emptyList<Long>(), keys("older words", threads = setOf(3L)))
    }
}
