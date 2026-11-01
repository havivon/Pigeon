package com.havivon.pigeon.data.sms

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.model.SmsType
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A row the system writes for a viewer send may carry a time a little before the one Pigeon
 * noted (both are the phone's clock, read a moment apart); older rows never match.
 */
internal const val PENDING_MATCH_SLACK_MILLIS = 2_000L

/** A message handed to the radio while another app is the default: Android stores it once the radio answers. */
data class PendingSend(
    /** Negative, so it can never be confused with a row of the system database. */
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val createdAtMillis: Long,
    val subId: Int?,
)

/**
 * The messages Pigeon sent as a viewer that Android has not stored yet. A non-default app may not
 * write the SMS database, so there is no Outbox row: Android writes the message itself (Sent or
 * Failed) when the radio reports back, just before Pigeon's sent PendingIntent fires. Until then
 * the conversation shows it from here as "sending"; it leaves when the result comes
 * ([SmsSentReceiver]) or as soon as Android's row is there ([withPendingSends]), so it never shows twice.
 */
class PendingSends(private val now: () -> Long = System::currentTimeMillis) {

    // Counts down from minus the start time, so ids stay distinct from a previous run's in-flight results
    private val nextId = AtomicLong(-now().coerceAtLeast(1L))

    private val state = MutableStateFlow<List<PendingSend>>(emptyList())
    val pending: StateFlow<List<PendingSend>> = state.asStateFlow()

    fun add(threadId: Long, address: String, body: String, subId: Int?): Long {
        val id = nextId.getAndDecrement()
        state.update { it + PendingSend(id, threadId, address, body, now(), subId) }
        return id
    }

    fun remove(id: Long) = state.update { list -> list.filterNot { it.id == id } }

    /** The radio's answer for one part: a failure, or the last part, ends the wait (Android has stored it by now). */
    fun onResult(id: Long, success: Boolean, isLastPart: Boolean) {
        if (!success || isLastPart) remove(id)
    }

    companion object {
        fun isPending(messageId: Long) = messageId < 0
    }
}

/**
 * A conversation's messages from the system database with Pigeon's [pending] viewer sends in it,
 * oldest first. A pending send that Android has stored already (an outgoing row to the same
 * number with the same text, written after it was sent) is left out, one row per send: the
 * message shows once, never twice.
 */
fun withPendingSends(rows: List<SmsMessage>, pending: List<PendingSend>): List<SmsMessage> {
    if (pending.isEmpty()) return rows
    val used = HashSet<Long>()
    val waiting = pending.filter { p ->
        val stored = rows.firstOrNull { r ->
            r.id !in used && r.type.outgoing && r.body == p.body && r.dateMillis >= p.createdAtMillis - PENDING_MATCH_SLACK_MILLIS &&
                PhoneNumbers.same(r.address, p.address)
        }
        if (stored != null) used += stored.id
        stored == null
    }
    if (waiting.isEmpty()) return rows
    val shown = waiting.map { p ->
        SmsMessage(p.id, p.threadId, p.address, p.body, p.createdAtMillis, 0L, SmsType.Outbox, read = true, subId = p.subId)
    }
    return (rows + shown).sortedWith(compareBy<SmsMessage> { it.dateMillis }.thenBy { if (it.id < 0) Long.MAX_VALUE else it.id })
}
