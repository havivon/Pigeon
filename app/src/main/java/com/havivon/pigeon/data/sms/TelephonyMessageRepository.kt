package com.havivon.pigeon.data.sms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.PendingDeletion
import com.havivon.pigeon.domain.model.SearchResults
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.repository.ContactsRepository
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.MessageRepository.Companion.UNDO_WINDOW_MILLIS
import com.havivon.pigeon.domain.repository.MessageRepository.Companion.UNREAD_INBOX_LIMIT
import com.havivon.pigeon.domain.repository.PinsRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/** Snippet bodies are read in batches of this many ids, under SQLite's argument limit. */
private const val SNIPPET_CHUNK = 500

private const val SEARCH_LIMIT = 200

/** The most messages [TelephonyMessageRepository.browse] reads at once. */
private const val BROWSE_LIMIT = 1_000

/** Deleted in chunks below SQLite's limit of arguments per statement. */
private const val DELETE_CHUNK = 500

/** How long the conversations stay watched after the last screen stopped collecting them (a rotation, a quick switch). */
private const val KEEP_READING_MILLIS = 5_000L

private val MESSAGE_COLUMNS = arrayOf(
    Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE,
    Telephony.Sms.DATE_SENT, Telephony.Sms.TYPE, Telephony.Sms.READ,
)

/** With the SIM column, which some makers' databases leave out. */
private val MESSAGE_COLUMNS_WITH_SIM = MESSAGE_COLUMNS + Telephony.Sms.SUBSCRIPTION_ID

/**
 * [MessageRepository] over Android's system SMS database.
 *
 * Undo of a deletion works by waiting, not by putting rows back: deleted ids are hidden from every
 * Flow at once and the provider delete runs when the undo window ends. Re-inserting would give the
 * messages new ids and could put them in a new thread; waiting keeps them exactly as they were.
 * If the app is killed inside the window the delete simply does not happen, which errs on the side
 * of keeping the user's messages.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TelephonyMessageRepository(
    private val context: Context,
    private val contacts: ContactsRepository,
    private val locks: LocksRepository,
    private val pins: PinsRepository,
    /** Outlives the screens, so a deletion completes after the user leaves the conversation. */
    private val appScope: CoroutineScope,
    /** Messages sent as a viewer that Android has not stored yet: shown in their conversation meanwhile. */
    private val pendingSends: PendingSends = PendingSends(),
    /**
     * Whether Pigeon may change the system database now (it is the default SMS app). While it may
     * not, marking read and deleting are not even tried: the screens say why instead.
     */
    private val canWrite: () -> Boolean = { true },
    /** Starts the conversations over when it emits (access changed: the default app, a permission). */
    refresh: Flow<Any?> = flowOf(Unit),
) : MessageRepository {

    private val resolver = context.contentResolver

    private data class Hidden(val messages: Set<Long> = emptySet(), val threads: Set<Long> = emptySet())

    private val hidden = MutableStateFlow(Hidden())

    /**
     * Read once for every screen and widget, and kept: a screen that opens gets the last list at once
     * (and the fresh one when the provider changes), instead of a read of its own. A change in [refresh]
     * starts over, watchers included: a permission just granted is not reported to content observers.
     */
    override val conversations: Flow<List<SmsConversation>> =
        refresh.flatMapLatest {
            resolver.changes(Telephony.MmsSms.CONTENT_CONVERSATIONS_URI, Telephony.Sms.CONTENT_URI, ContactsContract.Contacts.CONTENT_URI)
                .mapNotNull { readConversationsOrKeep() }
        }
            .combine(hidden) { list, h -> list.filter { it.threadId !in h.threads } }
            .flowOn(Dispatchers.IO)
            .shareIn(appScope, SharingStarted.WhileSubscribed(KEEP_READING_MILLIS), replay = 1)

    /**
     * Only the unread incoming rows, newest first: far lighter than the conversations, and kept for
     * every screen the same way (and started over by [refresh] too).
     */
    override val unreadInbox: Flow<List<SmsMessage>> =
        refresh.flatMapLatest { resolver.changes(Telephony.Sms.CONTENT_URI).mapNotNull { readUnreadInboxOrKeep() } }
            .combine(hidden) { list, h -> list.filter { it.id !in h.messages && it.threadId !in h.threads } }
            .flowOn(Dispatchers.IO)
            .shareIn(appScope, SharingStarted.WhileSubscribed(KEEP_READING_MILLIS), replay = 1)

    private fun readUnreadInbox(): List<SmsMessage> = queryMessages(
        "${Telephony.Sms.TYPE} = ${SmsType.Inbox.providerValue} AND ${Telephony.Sms.READ} = 0",
        emptyArray(),
        "${Telephony.Sms.DATE} DESC, ${Telephony.Sms._ID} DESC LIMIT $UNREAD_INBOX_LIMIT",
    )

    /** The unread messages, or null (the last list stays) when a maker's provider failed in a way it should not. */
    private fun readUnreadInboxOrKeep(): List<SmsMessage>? = try {
        readUnreadInbox()
    } catch (e: RuntimeException) {
        Log.w("Pigeon", "Reading the unread messages failed: ${e.javaClass.name}")
        null
    }

    override fun messages(threadId: Long, limit: Int): Flow<List<SmsMessage>> =
        resolver.changes(Telephony.Sms.CONTENT_URI)
            .map { readThread(threadId, limit) }
            .combine(hidden) { list, h -> if (threadId in h.threads) emptyList() else list.filter { it.id !in h.messages } }
            .combine(pendingSends.pending) { list, pending -> withPendingSends(list, pending.filter { it.threadId == threadId }) }
            .flowOn(Dispatchers.IO)

    /** The conversation's newest [limit] messages, oldest first. */
    private fun readThread(threadId: Long, limit: Int): List<SmsMessage> {
        val args = arrayOf(threadId.toString())
        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        if (limit == Int.MAX_VALUE) return queryMessages(selection, args, "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC")
        return queryMessages(selection, args, "${Telephony.Sms.DATE} DESC, ${Telephony.Sms._ID} DESC LIMIT ${limit.coerceAtLeast(1)}").asReversed()
    }

    /** The conversations, or null (the last list stays) when a maker's provider failed in a way it should not. */
    private suspend fun readConversationsOrKeep(): List<SmsConversation>? = try {
        readConversations()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        Log.w("Pigeon", "Reading the conversations failed: ${e.javaClass.name}")
        null
    }

    override suspend fun message(id: Long): SmsMessage? = withContext(Dispatchers.IO) {
        queryMessages("${Telephony.Sms._ID} = ?", arrayOf(id.toString()), null).firstOrNull()
    }

    override suspend fun recentMessages(threadId: Long, limit: Int): List<SmsMessage> = withContext(Dispatchers.IO) {
        queryMessages("${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} DESC LIMIT $limit").reversed()
    }

    override suspend fun recentIncoming(limit: Int): List<SmsMessage> = withContext(Dispatchers.IO) {
        val hiddenNow = hidden.value
        queryMessages("${Telephony.Sms.TYPE} = ${SmsType.Inbox.providerValue}", emptyArray(), "${Telephony.Sms.DATE} DESC LIMIT $limit")
            .filter { it.id !in hiddenNow.messages && it.threadId !in hiddenNow.threads }
    }

    override suspend fun unreadMessages(threadId: Long): List<SmsMessage> = withContext(Dispatchers.IO) {
        queryMessages(
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} = ${SmsType.Inbox.providerValue} AND ${Telephony.Sms.READ} = 0",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} ASC",
        )
    }

    override suspend fun threadIdFor(addresses: List<String>): Long = withContext(Dispatchers.IO) {
        try {
            Telephony.Threads.getOrCreateThreadId(context, addresses.toSet())
        } catch (_: RuntimeException) {
            // The system could not find or make one (no permission, a provider that failed)
            0L
        }
    }

    override suspend fun markThreadRead(threadId: Long) {
        // A viewer may not write: the default messaging app keeps its own read state
        if (!canWrite()) return
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            try {
                resolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
            } catch (_: RuntimeException) {
                // Not the default SMS app (the system database is read-only for us), or a maker's provider that failed
            }
        }
    }

    override fun deleteMessages(ids: Set<Long>): PendingDeletion {
        hidden.update { it.copy(messages = it.messages + ids) }
        return deletion(messages = ids, threads = emptySet())
    }

    override fun deleteThreads(threadIds: Set<Long>): PendingDeletion {
        hidden.update { it.copy(threads = it.threads + threadIds) }
        return deletion(messages = emptySet(), threads = threadIds)
    }

    override suspend fun search(query: String): SearchResults = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext SearchResults.Empty
        val excluded = locks.lockedNow() + hidden.value.threads
        val conversations = readConversations().filter { c ->
            c.threadId !in excluded &&
                ((c.contactName?.contains(q, ignoreCase = true) ?: false) || c.addresses.any { PhoneNumbers.matchesQuery(it, q) })
        }
        val messages = queryMessages(
            "${Telephony.Sms.BODY} LIKE ? ESCAPE '\\'",
            arrayOf(likeContains(q)),
            "${Telephony.Sms.DATE} DESC LIMIT $SEARCH_LIMIT",
        ).filter { it.threadId !in excluded && it.id !in hidden.value.messages }
        SearchResults(conversations, messages)
    }

    override suspend fun browse(threadId: Long?, limit: Int): List<SmsMessage> = withContext(Dispatchers.IO) {
        val excluded = locks.lockedNow() + hidden.value.threads
        if (threadId != null && threadId in excluded) return@withContext emptyList()
        val (selection, args) = if (threadId == null) null to emptyArray() else "${Telephony.Sms.THREAD_ID} = ?" to arrayOf(threadId.toString())
        queryMessages(selection, args, "${Telephony.Sms.DATE} DESC LIMIT ${limit.coerceIn(1, BROWSE_LIMIT)}")
            .filter { it.threadId !in excluded && it.id !in hidden.value.messages }
    }

    private suspend fun readConversations(): List<SmsConversation> {
        // Every SMS without its body (light), then only the newest body of each conversation
        val rows = resolver.queryList(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.TYPE),
        ) { c ->
            SmsSummaryRow(
                id = c.long(Telephony.Sms._ID),
                threadId = c.long(Telephony.Sms.THREAD_ID),
                address = c.string(Telephony.Sms.ADDRESS),
                dateMillis = c.long(Telephony.Sms.DATE),
                read = c.int(Telephony.Sms.READ) != 0,
                type = SmsType.fromProvider(c.int(Telephony.Sms.TYPE)),
            )
        }
        val summaries = summarizeThreads(rows)
        if (summaries.isEmpty()) return emptyList()
        val snippets = summaries.map { it.latestId }.chunked(SNIPPET_CHUNK).flatMap { ids ->
            resolver.queryList(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY),
                "${Telephony.Sms._ID} IN (${placeholders(ids.size)})",
                ids.map { it.toString() }.toTypedArray(),
            ) { it.long(Telephony.Sms._ID) to it.string(Telephony.Sms.BODY) }
        }.toMap()
        val names = contacts.displayNames(summaries.flatMap { it.addresses }.distinct())
        return summaries.map { it.toConversation(snippets[it.latestId].orEmpty(), names) }
    }

    private fun queryMessages(selection: String?, args: Array<String>, sortOrder: String?): List<SmsMessage> {
        val withSim = try {
            resolver.query(Telephony.Sms.CONTENT_URI, MESSAGE_COLUMNS_WITH_SIM, selection, args, sortOrder)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SQLiteException) {
            null
        } catch (_: SecurityException) {
            return emptyList()
        }
        if (withSim != null) return withSim.use { c -> buildList { while (c.moveToNext()) add(c.toMessage()) } }
        return resolver.queryList(Telephony.Sms.CONTENT_URI, MESSAGE_COLUMNS, selection, args, sortOrder) { it.toMessage() }
    }

    private fun Cursor.toMessage() = SmsMessage(
        id = long(Telephony.Sms._ID),
        threadId = long(Telephony.Sms.THREAD_ID),
        address = string(Telephony.Sms.ADDRESS),
        body = string(Telephony.Sms.BODY),
        dateMillis = long(Telephony.Sms.DATE),
        dateSentMillis = long(Telephony.Sms.DATE_SENT),
        type = SmsType.fromProvider(int(Telephony.Sms.TYPE)),
        read = int(Telephony.Sms.READ) != 0,
        // -1 is how the system records "no SIM"
        subId = intOrNull(Telephony.Sms.SUBSCRIPTION_ID)?.takeIf { it >= 0 },
    )

    private fun deletion(messages: Set<Long>, threads: Set<Long>): PendingDeletion = DelayedDeletion(
        appScope + Dispatchers.IO,
        UNDO_WINDOW_MILLIS,
        commit = { commit(messages, threads) },
        release = { hidden.update { Hidden(messages = it.messages - messages, threads = it.threads - threads) } },
    )

    private suspend fun commit(messages: Set<Long>, threads: Set<Long>) {
        // Not the default SMS app (any more): nothing can be deleted, and the rows come back into view
        if (!canWrite()) return
        try {
            messages.chunked(DELETE_CHUNK).forEach { chunk ->
                resolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms._ID} IN (${placeholders(chunk.size)})", chunk.map { it.toString() }.toTypedArray())
            }
            for (threadId in threads) {
                resolver.delete(ContentUris.withAppendedId(Telephony.Sms.Conversations.CONTENT_URI, threadId), null, null)
                // The system may give a deleted thread's id to a new conversation later; it must not start out locked or pinned
                locks.unlock(threadId)
                pins.unpin(threadId)
            }
        } catch (_: SecurityException) {
            // Not the default SMS app: nothing can be deleted, and the rows come back into view
        }
    }
}
