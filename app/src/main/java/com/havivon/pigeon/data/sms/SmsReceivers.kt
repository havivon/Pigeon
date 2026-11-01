package com.havivon.pigeon.data.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.runAsync
import com.havivon.pigeon.domain.model.SmsType
import kotlinx.coroutines.withTimeoutOrNull

/** How the system tells which SIM a message came in on (the first key is Android 11+, the second older). */
private const val EXTRA_SUBSCRIPTION_INDEX = "android.telephony.extra.SUBSCRIPTION_INDEX"
private const val EXTRA_SUBSCRIPTION_LEGACY = "subscription"

/** A receiver's goAsync work must end within about 10 seconds; storing and notifying come first. */
private const val WIDGET_UPDATE_BUDGET_MILLIS = 4_000L

/**
 * New SMS, delivered only to the default SMS app. Exported because the system delivers it, and
 * guarded by BROADCAST_SMS, which only the system holds.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty().filterNotNull().map {
                PduPart(
                    address = it.originatingAddress ?: it.displayOriginatingAddress.orEmpty(),
                    body = it.messageBody.orEmpty(),
                    timestampMillis = it.timestampMillis,
                )
            }
        } catch (_: RuntimeException) {
            // PDUs the system could not decode: nothing to store
            return
        }
        if (parts.isEmpty()) return
        val subId = intent.getIntExtra(EXTRA_SUBSCRIPTION_INDEX, intent.getIntExtra(EXTRA_SUBSCRIPTION_LEGACY, -1)).takeIf { it >= 0 }
        val app = AppContainer.from(context)
        runAsync("SmsDeliver") {
            app.incomingSms.handle(parts, subId)
            // The process may not live long enough for the usual quiet-time refresh. The message is
            // stored and notified by now; the redraw only gets what is left of the broadcast's time.
            withTimeoutOrNull(WIDGET_UPDATE_BUDGET_MILLIS) { app.widgets.updateNow() }
        }
    }
}

/**
 * MMS, delivered only to the default SMS app. Android requires a default SMS app to have this
 * receiver (guarded by BROADCAST_WAP_PUSH), but MMS is out of Pigeon's scope, so it does nothing.
 * The app does not hold RECEIVE_MMS, so in practice the system does not even deliver it.
 */
class WapPushDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

/**
 * The radio's answer for each part of a sent message. Not exported: the PendingIntents that reach
 * it are the app's own, fired by the system on the app's behalf. A negative id is a viewer send
 * ([PendingSends]), which Android stores itself; a positive one is Pigeon's own Outbox row.
 */
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SENT) return
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, 0L)
        if (messageId == 0L) return
        val part = intent.getIntExtra(EXTRA_PART, 0)
        val count = intent.getIntExtra(EXTRA_PART_COUNT, 1)
        val success = resultCode == Activity.RESULT_OK
        if (PendingSends.isPending(messageId)) {
            // Sent as a viewer: Android has stored the message (Sent or Failed) by now, so the wait ends
            AppContainer.from(context).pendingSends.onResult(messageId, success, isLastPart = part >= count - 1)
            return
        }
        val resolver = context.contentResolver
        runAsync("SmsSent") {
            val uri = messageUri(messageId)
            val current = resolver.queryList(uri, arrayOf(Telephony.Sms.TYPE)) { SmsType.fromProvider(it.int(Telephony.Sms.TYPE)) }.firstOrNull()
                ?: return@runAsync
            val next = typeAfterSendResult(current, success, isLastPart = part >= count - 1) ?: return@runAsync
            // The parts' results are handled at the same time: Sent must never overwrite a Failed another part just wrote
            resolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, next.providerValue) }, sentResultGuard(next), null)
        }
    }

    companion object {
        private const val ACTION_SENT = "com.havivon.pigeon.action.SMS_SENT"
        private const val EXTRA_MESSAGE_ID = "message_id"
        private const val EXTRA_PART = "part"
        private const val EXTRA_PART_COUNT = "part_count"

        /** One PendingIntent per message and part; the data keeps them distinct so none replaces another. */
        fun pendingIntent(context: Context, messageId: Long, part: Int, count: Int): PendingIntent {
            val intent = Intent(context, SmsSentReceiver::class.java)
                .setAction(ACTION_SENT)
                .setData(Uri.parse("pigeon://sent/$messageId/$part"))
                .putExtra(EXTRA_MESSAGE_ID, messageId)
                .putExtra(EXTRA_PART, part)
                .putExtra(EXTRA_PART_COUNT, count)
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
