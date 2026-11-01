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
 * What was judged is remembered (by conversation and its newest message's time), so a conversation
 * is judged again only when a new message comes in; contacts and groups are never judged.
 */
class SpamOnRead(
    private val hook: IncomingMessageHook,
    /** The conversation's newest incoming message, or null when it has none. */
    private val newestIncoming: suspend (threadId: Long) -> SmsMessage?,
) {
    private val judged = HashMap<Long, Long>()
    private val mutex = Mutex()

    /** Judges every conversation in [conversations] that needs it; the stored [verdicts] are left alone. */
    suspend fun judge(conversations: List<SmsConversation>, verdicts: List<StoredVerdict>) = mutex.withLock {
        for (c in conversations) {
            if (judged[c.threadId] == c.dateMillis) continue
            val address = c.addresses.singleOrNull()
            val needed = address != null && c.contactName == null && verdicts.none { PhoneNumbers.same(it.address, address) }
            if (needed) {
                val ok = judgeOne(c.threadId, address!!)
                // Unreadable right now (a provider that failed): tried again on the next read
                if (!ok) continue
            }
            judged[c.threadId] = c.dateMillis
        }
    }

    private suspend fun judgeOne(threadId: Long, address: String): Boolean = try {
        val m = newestIncoming(threadId)
        if (m != null) hook.onStored(StoredIncomingSms(m.id, threadId, m.address.ifBlank { address }, m.body, m.dateMillis))
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
