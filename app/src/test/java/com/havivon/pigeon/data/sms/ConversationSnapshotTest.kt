package com.havivon.pigeon.data.sms

import com.havivon.pigeon.domain.model.SmsConversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The inbox's list kept for the next launch: whole, without the locked chats' text, and dropped when it cannot be read. */
class ConversationSnapshotTest {

    @get:Rule
    val dir = TemporaryFolder()

    private val list = listOf(
        SmsConversation(1, listOf("0525550100"), "Dana", "See you at 8", 1_000L, 2, hasFailed = false, incomingCount = 5, totalCount = 9, userWrote = true, unreadIds = listOf(11L, 12L)),
        SmsConversation(2, listOf("Leumi"), null, "Charge of 249.90", 900L, 0, hasFailed = true, incomingCount = 3, totalCount = 3),
        SmsConversation(3, listOf("0541112222", "0541113333"), null, "A secret", 800L, 1, hasFailed = false, unreadIds = listOf(31L)),
    )

    @Test
    fun `the list comes back exactly as it was kept`() {
        val snapshot = ConversationSnapshot(dir.root.resolve("inbox.json"))
        assertNull(snapshot.load())
        snapshot.save(list)
        assertEquals(list, snapshot.load())
        assertEquals(list, ConversationSnapshot.decode(ConversationSnapshot.encode(list)))
    }

    @Test
    fun `a locked chat is kept without its text`() {
        val kept = ConversationSnapshot.forSaving(list, locked = setOf(3L))
        assertEquals("", kept.first { it.threadId == 3L }.snippet)
        assertEquals("See you at 8", kept.first { it.threadId == 1L }.snippet)
        assertFalse(ConversationSnapshot.encode(kept).contains("A secret"))
    }

    @Test
    fun `a broken or old file is dropped, and clear forgets the list`() {
        val file = dir.root.resolve("inbox.json")
        val snapshot = ConversationSnapshot(file)
        file.writeText("{not json")
        assertNull(snapshot.load())
        assertFalse(file.exists())
        file.writeText("""{"v":99,"list":[]}""")
        assertNull(snapshot.load())
        snapshot.save(list)
        snapshot.clear()
        assertNull(snapshot.load())
    }
}
