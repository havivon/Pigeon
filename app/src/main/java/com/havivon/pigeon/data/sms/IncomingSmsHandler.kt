package com.havivon.pigeon.data.sms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.havivon.pigeon.data.notify.MessageNotifier
import com.havivon.pigeon.domain.seam.IncomingMessageHook
import com.havivon.pigeon.domain.seam.IncomingVerdict
import com.havivon.pigeon.domain.seam.StoredIncomingSms
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "IncomingSms"

/**
 * What happens to a new SMS, in an order that never loses it: store it in the system database
 * first, then let the analysis judge it ([IncomingMessageHook]), and only then notify. A failure in
 * a later step never undoes an earlier one, and a message judged spam is stored all the same.
 */
class IncomingSmsHandler(
    private val context: Context,
    private val hook: IncomingMessageHook,
    private val notifier: MessageNotifier,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val resolver = context.contentResolver

    suspend fun handle(parts: List<PduPart>, subId: Int?) {
        for (sms in joinParts(parts)) {
            if (sms.address.isBlank() && sms.body.isBlank()) continue
            val receivedAt = now()
            val stored = store(sms, subId, receivedAt)
            if (stored == null) {
                // The system refused it: the notification is the only copy left, so it must show
                guarded("Notification") { notifier.notifyUnsaved(sms.address, sms.body, receivedAt) }
                continue
            }
            val verdict = guarded("Hook") { hook.onStored(stored) } ?: IncomingVerdict.Notify
            if (verdict == IncomingVerdict.Notify) guarded("Notification") { notifier.notifyThread(stored.threadId) }
        }
    }

    /** Runs one step after the message is stored; its failure is logged (by type only) and does not stop the rest. */
    private suspend fun <T> guarded(step: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$step failed: ${e.javaClass.name}")
        null
    }

    private fun store(sms: IncomingSms, subId: Int?, receivedAt: Long): StoredIncomingSms? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sms.address)
            put(Telephony.Sms.BODY, sms.body)
            put(Telephony.Sms.DATE, receivedAt)
            put(Telephony.Sms.DATE_SENT, sms.dateSentMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            if (subId != null) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        return try {
            val uri = resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) ?: return null
            val id = ContentUris.parseId(uri)
            // The system picked (or created) the thread from the address; read it back
            val threadId = resolver.queryList(uri, arrayOf(Telephony.Sms.THREAD_ID)) { it.long(Telephony.Sms.THREAD_ID) }.firstOrNull() ?: 0L
            StoredIncomingSms(messageId = id, threadId = threadId, address = sms.address, body = sms.body, dateMillis = receivedAt)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Store failed: ${e.javaClass.name}")
            null
        }
    }
}
