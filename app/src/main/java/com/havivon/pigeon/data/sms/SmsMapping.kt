package com.havivon.pigeon.data.sms

import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SmsType

/*
 * The SMS platform's logic that does not need a phone: joining the parts of an incoming message,
 * assembling conversations from the provider's rows, the send-result state machine. The provider
 * code around it only reads columns and calls these, so all of it is unit-tested on the JVM.
 */

/** One PDU of an incoming SMS, as Android decoded it. */
data class PduPart(val address: String, val body: String, val timestampMillis: Long)

/** A whole incoming message, with its parts joined. */
data class IncomingSms(val address: String, val body: String, val dateSentMillis: Long)

/**
 * Joins the parts of the SMS_DELIVER broadcast into messages. Android delivers a long message as
 * several PDUs in one broadcast, in order; they are joined per sender, in the order the senders
 * first appear. The date sent is the first part's.
 */
fun joinParts(parts: List<PduPart>): List<IncomingSms> =
    parts.groupBy { it.address }.map { (address, group) ->
        IncomingSms(address = address, body = group.joinToString("") { it.body }, dateSentMillis = group.first().timestampMillis)
    }

/** A row of the system's threads table (`content://mms-sms/conversations?simple=true`). */
data class ThreadRow(
    val id: Long,
    val dateMillis: Long,
    val snippet: String,
    val recipientIds: List<Long>,
    val messageCount: Int,
)

/** The threads table keeps recipients as space-separated ids into the canonical addresses table. */
fun parseRecipientIds(value: String?): List<Long> =
    value.orEmpty().split(' ').mapNotNull { it.trim().toLongOrNull() }

/**
 * Builds the inbox list from the provider's rows: addresses through the canonical addresses table,
 * names from [names] (by address), unread counts and failures by thread. Empty threads (left
 * behind when every message was deleted) and [hidden] ones (deleted, waiting for undo) are left out.
 */
fun assembleConversations(
    rows: List<ThreadRow>,
    canonicalAddresses: Map<Long, String>,
    unreadByThread: Map<Long, Int>,
    failedThreads: Set<Long>,
    names: Map<String, String?>,
    hidden: Set<Long> = emptySet(),
): List<SmsConversation> =
    rows.asSequence()
        .filter { it.messageCount > 0 && it.id !in hidden }
        .map { row ->
            val addresses = row.recipientIds.mapNotNull { canonicalAddresses[it] }
            val known = addresses.map { names[it] }
            SmsConversation(
                threadId = row.id,
                addresses = addresses,
                // A group shows names where there are any, numbers for the rest
                contactName = if (known.all { it == null }) null else addresses.zip(known).joinToString(", ") { (a, n) -> n ?: a },
                snippet = row.snippet,
                dateMillis = row.dateMillis,
                unreadCount = unreadByThread[row.id] ?: 0,
                hasFailed = row.id in failedThreads,
            )
        }
        .sortedByDescending { it.dateMillis }
        .toList()

/**
 * The message's new type after the radio reports one part's result, or null to leave it. A long
 * message is one row sent as several parts: one failed part fails the message, and it is Sent only
 * when the last part succeeds without an earlier failure.
 */
fun typeAfterSendResult(current: SmsType, success: Boolean, isLastPart: Boolean): SmsType? = when {
    !success -> if (current == SmsType.Failed) null else SmsType.Failed
    current == SmsType.Failed -> null
    isLastPart -> SmsType.Sent
    else -> null
}

/** A LIKE pattern that finds [query] anywhere, with its own `%`, `_` and `\` taken literally (ESCAPE '\'). */
fun likeContains(query: String): String {
    val escaped = buildString {
        for (c in query) {
            if (c == '\\' || c == '%' || c == '_') append('\\')
            append(c)
        }
    }
    return "%$escaped%"
}
