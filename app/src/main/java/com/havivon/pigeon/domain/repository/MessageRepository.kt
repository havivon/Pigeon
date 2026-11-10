package com.havivon.pigeon.domain.repository

import com.havivon.pigeon.domain.model.PendingDeletion
import com.havivon.pigeon.domain.model.SearchResults
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SmsMessage
import kotlinx.coroutines.flow.Flow

/**
 * The user's conversations and messages, from Android's system SMS database. Every Flow re-reads
 * when the database changes (a new message, a send result, another app's change), so the screens
 * only collect. Without READ_SMS every Flow emits an empty list instead of failing.
 */
interface MessageRepository {

    /** All conversations, newest first. */
    val conversations: Flow<List<SmsConversation>>

    /**
     * A conversation's newest [limit] messages, oldest first: a screen reads the page it shows, not a
     * long conversation's every message. All of them by default.
     */
    fun messages(threadId: Long, limit: Int = Int.MAX_VALUE): Flow<List<SmsMessage>>

    suspend fun message(id: Long): SmsMessage?

    /** The conversation's newest messages (any direction), oldest first; for notifications. */
    suspend fun recentMessages(threadId: Long, limit: Int): List<SmsMessage>

    /** The newest incoming messages from every conversation, newest first (the code widget looks for the latest code in them). */
    suspend fun recentIncoming(limit: Int): List<SmsMessage>

    /** The conversation's unread incoming messages, oldest first. */
    suspend fun unreadMessages(threadId: Long): List<SmsMessage>

    /**
     * The messages with these [ids], in any order; those gone or waiting to be deleted are left out. The inbox's
     * Unread list reads the [SmsConversation.unreadIds] of the conversations it shows: the same messages its
     * count is made of, so the two always agree.
     */
    suspend fun messagesById(ids: Collection<Long>): List<SmsMessage>

    /** The system thread for these addresses, created if there is none yet (for a new message); 0 if the system has none to give. */
    suspend fun threadIdFor(addresses: List<String>): Long

    /** Marks every incoming message in the conversation read (and seen). */
    suspend fun markThreadRead(threadId: Long)

    /**
     * Marks every unread incoming message of these conversations read (and seen) in as few provider updates as
     * possible, not one call per conversation ("Mark all as read").
     */
    suspend fun markThreadsRead(threadIds: Collection<Long>)

    /**
     * Deletes messages, with undo: they disappear from every Flow at once, and are removed from the
     * system database only when [UNDO_WINDOW_MILLIS] ends without [PendingDeletion.undo].
     */
    fun deleteMessages(ids: Set<Long>): PendingDeletion

    /** Deletes whole conversations, with the same undo window as [deleteMessages]. */
    fun deleteThreads(threadIds: Set<Long>): PendingDeletion

    /**
     * Conversations whose contact name or number matches [query], and messages whose text contains
     * it, newest first. Locked conversations are never in the results.
     */
    suspend fun search(query: String): SearchResults

    /**
     * The newest messages, newest first, of one conversation ([threadId]) or of all of them, for the
     * search filters used without any text ("has a code" alone). As in [search], locked
     * conversations and messages waiting to be deleted are left out. [fromMillis] (included) and [untilMillis] (not
     * included) keep only the messages in that time, so the newest [limit] of them are found, not of all.
     */
    suspend fun browse(threadId: Long?, limit: Int, fromMillis: Long? = null, untilMillis: Long? = null): List<SmsMessage>

    companion object {
        const val UNDO_WINDOW_MILLIS = 5_000L
    }
}
