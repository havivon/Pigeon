package com.havivon.pigeon.data.sms

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import com.havivon.pigeon.domain.bin.BinMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Puts messages from the recycle bin back into Android's SMS database: only the default SMS app may.
 * The conversation is asked of the system by its addresses ([threadFor]), since the old thread id may be
 * gone or given to someone else by now.
 */
class ProviderBinRestorer(context: Context) {
    private val app = context.applicationContext

    suspend fun threadFor(addresses: List<String>): Long = withContext(Dispatchers.IO) {
        try {
            Telephony.Threads.getOrCreateThreadId(app, addresses.toSet())
        } catch (_: RuntimeException) {
            0L
        }
    }

    /** Inserts [m] into the conversation [threadId] as it was; false when the system refused. */
    suspend fun insert(m: BinMessage, threadId: Long): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.THREAD_ID, threadId)
            put(Telephony.Sms.ADDRESS, m.address)
            put(Telephony.Sms.BODY, m.body)
            put(Telephony.Sms.DATE, m.dateMillis)
            put(Telephony.Sms.DATE_SENT, m.dateSentMillis)
            put(Telephony.Sms.TYPE, m.type.providerValue)
            put(Telephony.Sms.READ, if (m.read) 1 else 0)
            // Restored messages raise no new notification
            put(Telephony.Sms.SEEN, 1)
            m.subId?.let { put(Telephony.Sms.SUBSCRIPTION_ID, it) }
        }
        try {
            app.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) != null
        } catch (_: RuntimeException) {
            // Not the default SMS app (any more), or a maker's provider that failed
            false
        }
    }
}
