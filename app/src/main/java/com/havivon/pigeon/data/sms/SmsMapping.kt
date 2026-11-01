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

/** A row of the SMS table without its body: enough to build the inbox list. */
data class SmsSummaryRow(
    val id: Long,
    val threadId: Long,
    val address: String,
    val dateMillis: Long,
    val read: Boolean,
    val type: SmsType,
)

/** One conversation as the SMS table describes it; [latestId] is the message whose body is the snippet. */
data class ThreadSummary(
    val threadId: Long,
    val latestId: Long,
    val addresses: List<String>,
    val dateMillis: Long,
    val unreadCount: Int,
    val hasFailed: Boolean,
    val incomingCount: Int = 0,
    val userWrote: Boolean = false,
)

/**
 * The inbox list from the SMS table itself, newest conversation first. The threads table
 * (`mms-sms/conversations`) is not used: phone makers keep it differently (Samsung, for one, does
 * not maintain its message count), and an SMS-only app has everything it needs in the SMS rows.
 * Drafts and rows without a thread are skipped; [hidden] threads (deleted, waiting for undo) too.
 */
fun summarizeThreads(rows: List<SmsSummaryRow>, hidden: Set<Long> = emptySet()): List<ThreadSummary> =
    rows.asSequence()
        .filter { it.threadId > 0 && it.type != SmsType.Draft && it.threadId !in hidden }
        .groupBy { it.threadId }
        .map { (threadId, messages) ->
            val newestFirst = messages.sortedWith(compareByDescending<SmsSummaryRow> { it.dateMillis }.thenByDescending { it.id })
            ThreadSummary(
                threadId = threadId,
                latestId = newestFirst.first().id,
                addresses = newestFirst.map { it.address.trim() }.filter { it.isNotEmpty() }.distinct(),
                dateMillis = newestFirst.first().dateMillis,
                unreadCount = messages.count { it.type == SmsType.Inbox && !it.read },
                hasFailed = messages.any { it.type == SmsType.Failed },
                incomingCount = messages.count { it.type == SmsType.Inbox },
                userWrote = messages.any { it.type.outgoing },
            )
        }
        .sortedByDescending { it.dateMillis }

/** A summary with its snippet and contact names (by address); a group shows names where known, numbers for the rest. */
fun ThreadSummary.toConversation(snippet: String, names: Map<String, String?>): SmsConversation {
    val known = addresses.map { names[it] }
    return SmsConversation(
        threadId = threadId,
        addresses = addresses,
        contactName = if (known.all { it == null }) null else addresses.zip(known).joinToString(", ") { (a, n) -> n ?: a },
        snippet = snippet,
        dateMillis = dateMillis,
        unreadCount = unreadCount,
        hasFailed = hasFailed,
        incomingCount = incomingCount,
        userWrote = userWrote,
    )
}

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

/**
 * The condition on the row's update for a send result: a failure always lands, a success only on a
 * message that is not Failed by then. The read in [typeAfterSendResult] and the write are two steps,
 * and two parts of one message can report at the same moment; the condition makes the write atomic.
 */
fun sentResultGuard(next: SmsType): String? =
    if (next == SmsType.Failed) null else "type != ${SmsType.Failed.providerValue}"

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
