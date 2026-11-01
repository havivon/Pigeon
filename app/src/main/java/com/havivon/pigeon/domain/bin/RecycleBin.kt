package com.havivon.pigeon.domain.bin

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.model.SmsType
import kotlinx.coroutines.flow.Flow

/** How long a deleted message waits in the recycle bin before it is deleted for good. */
const val BIN_DAYS = 30

const val DAY_MILLIS = 24 * 60 * 60 * 1000L

const val BIN_RETENTION_MILLIS = BIN_DAYS * DAY_MILLIS

/**
 * A message in the recycle bin: a copy of the system's row, taken just before the row was deleted, with
 * everything needed to put it back.
 */
data class BinMessage(
    /** The bin's own id. */
    val id: Long,
    /** The message's id in the system database before it was deleted. */
    val originalId: Long,
    /** Its conversation's id then. The system may give it to another conversation later: restoring asks again. */
    val threadId: Long,
    /** The conversation's other side then (several for a group): where it is restored to. */
    val threadAddresses: List<String>,
    val address: String,
    val body: String,
    val dateMillis: Long,
    val dateSentMillis: Long,
    val type: SmsType,
    val read: Boolean,
    val subId: Int?,
    val deletedAtMillis: Long,
    /** It was in a locked chat: the bin shows nothing of it until the user proves who they are. */
    val locked: Boolean,
)

/** A message on its way to the bin: the system's row and its conversation's addresses. */
data class BinCopy(val message: SmsMessage, val threadAddresses: List<String>)

/** The recycle bin's storage: a database of its own, kept out of backups. */
interface BinRepository {
    /** Everything in the bin, the newest deletion first. */
    val messages: Flow<List<BinMessage>>

    suspend fun add(copies: List<BinCopy>, lockedThreads: Set<Long>, deletedAtMillis: Long)

    suspend fun remove(ids: Collection<Long>)

    /** Deletes for good what was deleted before [cutoffMillis]; returns how many. */
    suspend fun removeDeletedBefore(cutoffMillis: Long): Int

    suspend fun clear()
}

/**
 * The recycle bin's rules. A deletion copies the messages into the bin first and deletes them from the
 * system database only when the copy is stored: if the copy fails, nothing is deleted. After [BIN_DAYS]
 * days a message is deleted for good ([purgeExpired], run daily). Restoring puts it back into the system
 * database, in the conversation of its addresses (asked of the system again: the old id may be gone).
 */
class RecycleBin(private val store: BinRepository, private val now: () -> Long = System::currentTimeMillis) {

    val messages: Flow<List<BinMessage>> get() = store.messages

    /**
     * Copies [copies] into the bin, then runs [delete]. A copy that fails throws, and [delete] does not run:
     * the messages stay where they were.
     */
    suspend fun moveToBin(copies: List<BinCopy>, lockedThreads: Set<Long>, delete: suspend () -> Unit) {
        if (copies.isNotEmpty()) store.add(copies, lockedThreads, now())
        delete()
    }

    /** Deletes for good everything that has been in the bin for [BIN_DAYS] days. */
    suspend fun purgeExpired(): Int = store.removeDeletedBefore(now() - BIN_RETENTION_MILLIS)

    /**
     * Puts [items] back into the system database: each conversation's messages into the thread the system
     * gives for its addresses ([threadFor], asked once per conversation), oldest first, by [insert]. What was
     * put back leaves the bin; what failed stays in it. Returns the number restored.
     */
    suspend fun restore(
        items: List<BinMessage>,
        threadFor: suspend (List<String>) -> Long,
        insert: suspend (BinMessage, threadId: Long) -> Boolean,
    ): Int {
        val restored = ArrayList<Long>()
        for ((_, group) in items.groupBy { conversationKey(it) }) {
            val addresses = group.first().threadAddresses.ifEmpty { listOf(group.first().address) }.filter { it.isNotBlank() }
            val threadId = if (addresses.isEmpty()) 0L else threadFor(addresses)
            if (threadId <= 0L) continue
            for (m in group.sortedWith(compareBy({ it.dateMillis }, { it.originalId }))) {
                if (insert(m, threadId)) restored += m.id
            }
        }
        if (restored.isNotEmpty()) store.remove(restored)
        return restored.size
    }

    suspend fun deleteForever(ids: Collection<Long>) {
        if (ids.isNotEmpty()) store.remove(ids)
    }

    suspend fun empty() = store.clear()

    companion object {
        /** The days a message deleted at [deletedAtMillis] has left in the bin at [nowMillis]: 30 on the day, never below 0. */
        fun daysLeft(deletedAtMillis: Long, nowMillis: Long): Int {
            val left = deletedAtMillis + BIN_RETENTION_MILLIS - nowMillis
            if (left <= 0) return 0
            return ((left + DAY_MILLIS - 1) / DAY_MILLIS).toInt()
        }

        /** The conversation a bin message belongs to: its addresses, however each number is written. */
        fun conversationKey(m: BinMessage): String =
            m.threadAddresses.ifEmpty { listOf(m.address) }
                .map { PhoneNumbers.lookupKey(it) ?: it.trim().lowercase() }
                .distinct().sorted().joinToString(",")
    }
}
