package com.havivon.pigeon.data.sms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The longest body the app will send: about 150 SMS parts, far beyond any real message. */
const val MAX_SEND_LENGTH = 10_000

/**
 * [SmsSender] over SmsManager. As the default SMS app, every message is written to the Outbox
 * before it goes out, so it shows in the conversation at once and is never lost if the send fails;
 * [SmsSentReceiver] moves it to Sent or Failed when the radio reports back. While another app is
 * the default (viewer), Android stores the sent message itself, so nothing is written: the message
 * waits in [PendingSends] until Android's row is there. See [SendFlow].
 */
class TelephonySmsSender(
    private val context: Context,
    private val messages: MessageRepository,
    pending: PendingSends,
    /** Whether Pigeon may write the SMS database now (it is the default SMS app). */
    canWrite: () -> Boolean,
) : SmsSender {

    private val resolver = context.contentResolver

    private val flow = SendFlow(canWrite, ProviderOutbox(), ::transmit, pending) { address -> messages.threadIdFor(listOf(address)) }

    override suspend fun send(recipients: List<String>, body: String, subId: Int?): List<Long> = withContext(Dispatchers.IO) {
        if (body.isBlank() || body.length > MAX_SEND_LENGTH) return@withContext emptyList()
        val sub = subId ?: SmsManager.getDefaultSmsSubscriptionId().takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        flow.send(recipients, body, sub)
    }

    override suspend fun retry(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        flow.retry(messages.message(messageId))
    }

    /** The Outbox and send results in the system database; only the default SMS app gets anything but a refusal. */
    private inner class ProviderOutbox : OutboxStore {
        override fun insert(address: String, body: String, subId: Int?): Long? {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, SmsType.Outbox.providerValue)
                if (subId != null) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
            }
            return try {
                resolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)?.let(ContentUris::parseId)?.takeIf { it > 0 }
            } catch (_: RuntimeException) {
                // Refused (the default SMS app changed a moment ago), or a maker's provider that failed: nothing is sent
                null
            }
        }

        override fun markFailed(messageId: Long) {
            update(messageId, ContentValues().apply { put(Telephony.Sms.TYPE, SmsType.Failed.providerValue) })
        }

        override fun requeue(messageId: Long): Boolean = update(
            messageId,
            ContentValues().apply {
                put(Telephony.Sms.TYPE, SmsType.Outbox.providerValue)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
            },
        )

        private fun update(messageId: Long, values: ContentValues): Boolean = try {
            resolver.update(messageUri(messageId), values, null, null) > 0
        } catch (_: SecurityException) {
            false
        }
    }

    /** Hands the message to the radio; false when it refuses at once (an invalid number, no SIM, no SEND_SMS). */
    private fun transmit(address: String, body: String, subId: Int?, messageId: Long): Boolean = try {
        val manager = smsManager(subId)
        val parts = manager.divideMessage(body)
        val sent = ArrayList(parts.indices.map { SmsSentReceiver.pendingIntent(context, messageId, it, parts.size) })
        if (parts.size <= 1) {
            manager.sendTextMessage(address, null, body, sent.firstOrNull(), null)
        } else {
            manager.sendMultipartTextMessage(address, null, parts, sent, null)
        }
        true
    } catch (_: RuntimeException) {
        false
    }

    @Suppress("DEPRECATION")
    private fun smsManager(subId: Int?): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val default = context.getSystemService(SmsManager::class.java)
            if (subId == null) default else default.createForSubscriptionId(subId)
        } else {
            if (subId == null) SmsManager.getDefault() else SmsManager.getSmsManagerForSubscriptionId(subId)
        }
}

/** What sending needs of the system database. Only the default SMS app may write it. */
internal interface OutboxStore {
    /** The new Outbox row's id, or null when the database refused it. */
    fun insert(address: String, body: String, subId: Int?): Long?

    fun markFailed(messageId: Long)

    /** Puts a failed message back in the Outbox for a retry; false when refused. */
    fun requeue(messageId: Long): Boolean
}

/** Hands a message to the radio, with [messageId] in its sent PendingIntents; false when the radio refuses at once. */
internal fun interface Radio {
    fun transmit(address: String, body: String, subId: Int?, messageId: Long): Boolean
}

/**
 * The two ways a message goes out. As the default SMS app ([canWrite]) it is written to the
 * Outbox first and only a written message is sent. As a viewer nothing is written (Android stores
 * a non-default app's sent message itself, and an Outbox row would make it show twice): the
 * message waits in [pending] while the radio has it. Either way each recipient is sent exactly once.
 */
internal class SendFlow(
    private val canWrite: () -> Boolean,
    private val outbox: OutboxStore,
    private val radio: Radio,
    private val pending: PendingSends,
    private val threadIdFor: suspend (String) -> Long,
) {

    /** The sent messages' ids: the Outbox rows, or (as a viewer) the negative ids of the pending sends. */
    suspend fun send(recipients: List<String>, body: String, subId: Int?): List<Long> =
        if (canWrite()) {
            sendEach(recipients, write = { outbox.insert(it, body, subId) }) { address, id ->
                // The row is there: a refusal shows as Failed, and a tap on it retries
                if (!radio.transmit(address, body, subId, id)) outbox.markFailed(id)
                true
            }
        } else {
            sendEach(recipients, write = { address -> pending.add(threadIdFor(address), address, body, subId) }) { address, id ->
                // Refused at once: Android stores nothing, so there is nothing to show either
                radio.transmit(address, body, subId, id).also { ok -> if (!ok) pending.remove(id) }
            }
        }

    /**
     * Sends a failed message again from the SIM it was first sent from. It needs the system
     * database (the row goes back to the Outbox), so only the default SMS app retries.
     */
    fun retry(message: SmsMessage?): Boolean {
        if (message == null || message.type != SmsType.Failed || !canWrite()) return false
        if (!outbox.requeue(message.id)) return false
        if (!radio.transmit(message.address, message.body, message.subId, message.id)) outbox.markFailed(message.id)
        return true
    }
}

/**
 * Sends one SMS per recipient (trimmed, blank ones and repeats of the same number dropped): each is
 * first written ([write], the new row's id or null), and only a message that was written is handed
 * to the radio ([transmit], whether the message counts as sent). A message the system database
 * refuses is not sent at all: the screen then says it failed, and a second tap must not send it
 * twice. Returns the ids of the messages that went out.
 */
internal inline fun sendEach(recipients: List<String>, write: (String) -> Long?, transmit: (String, Long) -> Boolean): List<Long> =
    recipients.map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { PhoneNumbers.normalize(it) }
        .mapNotNull { address -> write(address)?.takeIf { id -> transmit(address, id) } }

/** A message's row in the system database. */
internal fun messageUri(id: Long) = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
