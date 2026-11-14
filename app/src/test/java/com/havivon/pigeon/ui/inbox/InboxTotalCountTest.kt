package com.havivon.pigeon.ui.inbox

import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.SpamReason
import org.junit.Assert.assertEquals
import org.junit.Test

/** The number on the "All" side of the All / Unread toggle: every message of the chosen category, read and unread. */
class InboxTotalCountTest {

    private fun conv(id: Long, total: Int, unread: Int = 0, folder: Folder = Folder.Personal, custom: Long? = null, spam: Boolean = false) =
        Conversation(
            id, "c$id", "", "", 0xFF000000,
            unread = unread,
            folder = if (spam) Folder.Spam else folder,
            spamReasons = if (spam) listOf(SpamReason.ShortLink) else emptyList(),
            customFolderId = custom,
            totalCount = total,
        )

    private val all = listOf(
        conv(1, total = 40, unread = 2),
        conv(2, total = 7, unread = 1, folder = Folder.Banks),
        conv(3, total = 5, folder = Folder.Banks),
        conv(4, total = 9, folder = Folder.All, custom = 12),
        conv(5, total = 100, unread = 3, spam = true),
    )

    @Test
    fun `All counts every message of the category, the unread ones included`() {
        val banks = InboxUiState(all = all, folder = Folder.Banks)
        assertEquals(12, banks.totalCount)
        assertEquals(1, banks.unreadCount)
    }

    @Test
    fun `the whole inbox leaves spam out, as its list does`() {
        assertEquals(40 + 7 + 5 + 9, InboxUiState(all = all, folder = Folder.All).totalCount)
    }

    @Test
    fun `a category of the user's counts its own conversations`() {
        assertEquals(9, InboxUiState(all = all, customFolder = 12).totalCount)
    }

    @Test
    fun `an empty category shows no number`() {
        assertEquals(0, InboxUiState(all = all, folder = Folder.Travel).totalCount)
    }
}
