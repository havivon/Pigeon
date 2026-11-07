package com.havivon.pigeon.data.sms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.havivon.pigeon.domain.seam.IncomingMessageHook
import com.havivon.pigeon.domain.seam.IncomingVerdict
import com.havivon.pigeon.domain.seam.StoredIncomingSms
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "IncomingSms"

/** Writes a new incoming SMS to the system database: the stored row, or null when the system refused it. */
fun interface IncomingSmsStore {
    fun store(sms: IncomingSms, subId: Int?, receivedAt: Long): StoredIncomingSms?
}

/** What the handler needs of the notifications (`MessageNotifier`). */
interface NewMessageNotifier {
    /** Shows the conversation's notification with its unread messages. */
    suspend fun notifyThread(threadId: Long)

    /** Shows a message that has no conversation to open: its notification shows the text itself. */
    suspend fun notifyUnsaved(address: String, body: String, dateMillis: Long)
}

/**
 * What happens to a new SMS, in an order that never loses it: store it in the system database
 * first, then let the analysis judge it ([IncomingMessageHook]), and only then notify. A failure in
 * a later step never undoes an earlier one, and a message judged spam is stored all the same.
 */
class IncomingSmsHandler(
    private val store: IncomingSmsStore,
    private val hook: IncomingMessageHook,
    private val notifier: NewMessageNotifier,
    private val now: () -> Long = System::currentTimeMillis,
    /** After a stored message passed the analysis and was notified (not for spam): copies its code (`CodeAutoCopy`). */
    private val afterNotify: suspend (StoredIncomingSms) -> Unit = {},
    /** Logs a failed step, by the exception type only: its message could quote the text. */
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) {

    suspend fun handle(parts: List<PduPart>, subId: Int?) {
        for (sms in joinParts(parts)) {
            if (sms.address.isBlank() && sms.body.isBlank()) continue
            val receivedAt = now()
            val stored = try {
                store.store(sms, subId, receivedAt)
            } catch (e: RuntimeException) {
                log("Store failed: ${e.javaClass.name}")
                null
            }
            if (stored == null) {
                // The system refused it: the notification is the only copy left, so it must show
                guarded("Notification") { notifier.notifyUnsaved(sms.address, sms.body, receivedAt) }
                continue
            }
            val verdict = guarded("Hook") { hook.onStored(stored) } ?: IncomingVerdict.Notify
            if (verdict != IncomingVerdict.Notify) continue
            if (stored.threadId > 0) {
                guarded("Notification") { notifier.notifyThread(stored.threadId) }
            } else {
                // Stored, but its conversation could not be read back: still tell the user, with the text
                guarded("Notification") { notifier.notifyUnsaved(sms.address, sms.body, receivedAt) }
            }
            guarded("After notification") { afterNotify(stored) }
        }
    }

    /** Runs one step after the message is stored; its failure is logged (by type only) and does not stop the rest. */
    private suspend fun <T> guarded(step: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("$step failed: ${e.javaClass.name}")
        null
    }
}

/** [IncomingSmsStore] over the system SMS database: the Inbox, where the system picks (or creates) the thread. */
class ProviderSmsStore(context: Context) : IncomingSmsStore {

    private val resolver = context.contentResolver

    override fun store(sms: IncomingSms, subId: Int?, receivedAt: Long): StoredIncomingSms? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sms.address)
            put(Telephony.Sms.BODY, sms.body)
            put(Telephony.Sms.DATE, receivedAt)
            put(Telephony.Sms.DATE_SENT, sms.dateSentMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            if (subId != null) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        val uri = resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(uri)
        // A refused insert comes back as a made-up row 0
        if (id <= 0) return null
        // The system picked (or created) the thread from the address; read it back
        val threadId = resolver.queryList(uri, arrayOf(Telephony.Sms.THREAD_ID)) { it.long(Telephony.Sms.THREAD_ID) }.firstOrNull() ?: 0L
        return StoredIncomingSms(messageId = id, threadId = threadId, address = sms.address, body = sms.body, dateMillis = receivedAt)
    }
}
