package com.havivon.pigeon.domain.analysis

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.seam.IncomingMessageHook
import com.havivon.pigeon.domain.seam.StoredIncomingSms
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.havivon.pigeon.domain.model.SpamVerdict as StoredVerdict

/**
 * Spam for messages Pigeon never received. While another app is the default, no message passes
 * through Pigeon's incoming hook, so nothing would ever reach the Spam folder. Instead, when the
 * conversations are read, each one from an unknown sender without a stored verdict is judged once
 * by the same [hook] the incoming messages go through (its newest incoming message, the "you wrote
 * here" rule, the user's "not spam" senders and blocked words). A spam verdict is stored in
 * `SpamRepository` like any other, so it stays, shows in the Spam folder, and "Not spam" undoes it.
 *
 * What was judged is remembered (by conversation and its newest message's time) in [memory], across
 * restarts too, so a conversation is judged again only when a new message comes in: an inbox of hundreds
 * of conversations is not read again, one query after another, on every start. Contacts and groups are
 * never judged.
 *
 * The memory carries the [version] of the rules it was judged with ([SpamScorer.VERSION]). Better rules
 * (a new version) forget it and judge every conversation again, once, off the main thread like any pass;
 * conversations with a stored verdict (the filter's, or the user's own "Mark as spam") are still left alone,
 * and the senders the user called "Not spam" are never put back (the hook keeps them out). One exception, [release]: a
 * stored verdict of the filter on a conversation whose newest message carries a one-time code is judged again with the
 * new rules, and dropped if they no longer call it spam (version 7: a code is never spam by score alone). The user's own
 * mark, a number blocked on the phone and a word the user blocked are never released.
 */
class SpamOnRead(
    private val hook: IncomingMessageHook,
    private val memory: JudgedMemory = JudgedMemory.None,
    /** The rules' version: what [memory] judged with another one is judged again. */
    private val version: Int = SpamScorer.VERSION,
    /** Drops the filter's verdict on a conversation's newest message when the rules no longer make it spam (see [CodeSpamRelease]). */
    private val release: suspend (StoredIncomingSms) -> Unit = {},
    /** The conversation's newest incoming message, or null when it has none. */
    private val newestIncoming: suspend (threadId: Long) -> SmsMessage?,
) {
    /** Read from [memory] at the first judging, which runs off the main thread. */
    private var judged: HashMap<Long, Long>? = null
    private val mutex = Mutex()

    /** Judges every conversation in [conversations] that needs it; the stored [verdicts] are left alone. */
    suspend fun judge(conversations: List<SmsConversation>, verdicts: List<StoredVerdict>) = mutex.withLock {
        val judged = judged ?: HashMap(memory.load().takeIf { it.version == version }?.threads.orEmpty()).also { judged = it }
        val present = conversations.mapTo(HashSet()) { it.threadId }
        fun remember() = memory.save(Judged(version, judged.filterKeys { it in present }))
        var changed = 0
        try {
            for (c in conversations) {
                if (judged[c.threadId] == c.dateMillis) continue
                // One number written two ways ("+972541234567" and "0541234567" in the same thread) is one sender, not a group
                val address = c.addresses.fold(emptyList<String>()) { acc, a -> if (acc.any { PhoneNumbers.same(it, a) }) acc else acc + a }.singleOrNull()
                val unknown = address != null && c.contactName == null && !c.userWrote
                val stored = unknown && verdicts.any { PhoneNumbers.same(it.address, address!!) }
                if (unknown) {
                    val ok = judgeOne(c.threadId, address!!, alreadyJudged = stored)
                    // Unreadable right now (a provider that failed): tried again on the next read
                    if (!ok) continue
                }
                judged[c.threadId] = c.dateMillis
                // A first pass over a long inbox takes a while: kept as it goes, in case the app is closed meanwhile
                if (++changed % SAVE_EVERY == 0) remember()
            }
        } finally {
            // Also when a newer list stopped this one: what was judged stays judged. Deleted conversations are forgotten.
            if (changed % SAVE_EVERY != 0) remember()
        }
    }

    /** Judges the newest incoming message; with a stored verdict ([alreadyJudged]) it only asks [release] whether to drop it. */
    private suspend fun judgeOne(threadId: Long, address: String, alreadyJudged: Boolean): Boolean = try {
        val m = newestIncoming(threadId)
        if (m != null) {
            val stored = StoredIncomingSms(m.id, threadId, m.address.ifBlank { address }, m.body, m.dateMillis)
            if (alreadyJudged) release(stored) else hook.onStored(stored)
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}

/** How many judged conversations [SpamOnRead] keeps in [JudgedMemory] at a time during a long pass. */
private const val SAVE_EVERY = 50

/** What [SpamOnRead] judged: conversation id to the time of its newest message, with the rules' [version]. */
data class Judged(val version: Int, val threads: Map<Long, Long>)

/** Where [SpamOnRead] keeps what it judged. */
interface JudgedMemory {
    fun load(): Judged

    fun save(judged: Judged)

    /** Remembers nothing: everything is judged again after a restart. */
    object None : JudgedMemory {
        override fun load(): Judged = Judged(SpamScorer.VERSION, emptyMap())

        override fun save(judged: Judged) = Unit
    }
}
