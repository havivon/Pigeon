package com.havivon.pigeon.data.sms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
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
import com.havivon.pigeon.domain.repository.PinsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/** The threads table, without the per-message join the full conversations URI does. */
private val THREADS_URI: Uri = Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build()

/** Recipient id → address, for the threads table's recipient_ids. No public constant exists. */
private val CANONICAL_ADDRESSES_URI: Uri = Uri.parse("content://mms-sms/canonical-addresses")

private const val SEARCH_LIMIT = 200

/** The most messages [TelephonyMessageRepository.browse] reads at once. */
private const val BROWSE_LIMIT = 1_000

/** Deleted in chunks below SQLite's limit of arguments per statement. */
private const val DELETE_CHUNK = 500

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
) : MessageRepository {

    private val resolver = context.contentResolver

    private data class Hidden(val messages: Set<Long> = emptySet(), val threads: Set<Long> = emptySet())

    private val hidden = MutableStateFlow(Hidden())

    override val conversations: Flow<List<SmsConversation>> =
        resolver.changes(Telephony.MmsSms.CONTENT_CONVERSATIONS_URI, Telephony.Sms.CONTENT_URI, ContactsContract.Contacts.CONTENT_URI)
            .mapLatest { readConversations() }
            .combine(hidden) { list, h -> list.filter { it.threadId !in h.threads } }
            .flowOn(Dispatchers.IO)

    override fun messages(threadId: Long): Flow<List<SmsMessage>> =
        resolver.changes(Telephony.Sms.CONTENT_URI)
            .mapLatest { queryMessages("${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC") }
            .combine(hidden) { list, h -> if (threadId in h.threads) emptyList() else list.filter { it.id !in h.messages } }
            .flowOn(Dispatchers.IO)

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
        val rows = resolver.queryList(
            THREADS_URI,
            arrayOf(Telephony.Threads._ID, Telephony.Threads.DATE, Telephony.Threads.SNIPPET, Telephony.Threads.RECIPIENT_IDS, Telephony.Threads.MESSAGE_COUNT),
            sortOrder = "${Telephony.Threads.DATE} DESC",
        ) { c ->
            ThreadRow(
                id = c.long(Telephony.Threads._ID),
                dateMillis = c.long(Telephony.Threads.DATE),
                snippet = c.string(Telephony.Threads.SNIPPET),
                recipientIds = parseRecipientIds(c.string(Telephony.Threads.RECIPIENT_IDS)),
                messageCount = c.int(Telephony.Threads.MESSAGE_COUNT),
            )
        }
        if (rows.isEmpty()) return emptyList()
        val canonical = resolver.queryList(CANONICAL_ADDRESSES_URI, arrayOf("_id", "address")) { c -> c.long("_id") to c.string("address") }.toMap()
        val unread = resolver.queryList(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = ${SmsType.Inbox.providerValue}",
        ) { it.long(Telephony.Sms.THREAD_ID) }.groupingBy { it }.eachCount()
        val failed = resolver.queryList(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.TYPE} = ${SmsType.Failed.providerValue}",
        ) { it.long(Telephony.Sms.THREAD_ID) }.toSet()
        val names = rows.flatMap { r -> r.recipientIds.mapNotNull { canonical[it] } }.distinct().associateWith { contacts.displayName(it) }
        return assembleConversations(rows, canonical, unread, failed, names)
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
