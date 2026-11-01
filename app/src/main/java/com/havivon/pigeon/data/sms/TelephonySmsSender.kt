package com.havivon.pigeon.data.sms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The longest body the app will send: about 150 SMS parts, far beyond any real message. */
const val MAX_SEND_LENGTH = 10_000

/**
 * [SmsSender] over SmsManager. Every message is written to the Outbox before it goes out, so it
 * shows in the conversation at once and is never lost if the send fails; [SmsSentReceiver] moves
 * it to Sent or Failed when the radio reports back.
 */
class TelephonySmsSender(
    private val context: Context,
    private val messages: MessageRepository,
) : SmsSender {

    private val resolver = context.contentResolver

    override suspend fun send(recipients: List<String>, body: String, subId: Int?): List<Long> = withContext(Dispatchers.IO) {
        if (body.isBlank() || body.length > MAX_SEND_LENGTH) return@withContext emptyList()
        val sub = subId ?: SmsManager.getDefaultSmsSubscriptionId().takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        sendEach(recipients, write = { writeOutbox(it, body, sub) }, transmit = { address, id -> transmit(address, body, sub, id) })
    }

    override suspend fun retry(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        val message = messages.message(messageId)
        if (message == null || message.type != SmsType.Failed) return@withContext false
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, SmsType.Outbox.providerValue)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
        }
        if (!update(messageId, values)) return@withContext false
        transmit(message.address, message.body, message.subId, messageId)
        true
    }

    /** The new row's id, or null when the system database refused it (Pigeon is not the default SMS app). */
    private fun writeOutbox(address: String, body: String, subId: Int?): Long? {
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
            // Refused (not the default SMS app), or a maker's provider that failed: nothing is sent
            null
        }
    }

    /** Hands the stored message to the radio. A message the radio refuses at once is marked Failed straight away. */
    private fun transmit(address: String, body: String, subId: Int?, messageId: Long) {
        try {
            val manager = smsManager(subId)
            val parts = manager.divideMessage(body)
            val sent = ArrayList(parts.indices.map { SmsSentReceiver.pendingIntent(context, messageId, it, parts.size) })
            if (parts.size <= 1) {
                manager.sendTextMessage(address, null, body, sent.firstOrNull(), null)
            } else {
                manager.sendMultipartTextMessage(address, null, parts, sent, null)
            }
        } catch (_: RuntimeException) {
            // An invalid number, no SIM, or a refused permission
            update(messageId, ContentValues().apply { put(Telephony.Sms.TYPE, SmsType.Failed.providerValue) })
        }
    }

    private fun update(messageId: Long, values: ContentValues): Boolean = try {
        resolver.update(messageUri(messageId), values, null, null) > 0
    } catch (_: SecurityException) {
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

/**
 * Sends one SMS per recipient (trimmed, blank ones and repeats of the same number dropped): each is
 * first written ([write], the new row's id or null), and only a message that was written is handed
 * to the radio ([transmit]). A message the system database refuses (Pigeon is not the default SMS
 * app) is not sent at all: the screen then says it failed, and a second tap must not send it twice.
 * Returns the written messages' ids.
 */
internal inline fun sendEach(recipients: List<String>, write: (String) -> Long?, transmit: (String, Long) -> Unit): List<Long> =
    recipients.map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { PhoneNumbers.normalize(it) }
        .mapNotNull { address -> write(address)?.also { id -> transmit(address, id) } }

/** A message's row in the system database. */
internal fun messageUri(id: Long) = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
