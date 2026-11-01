package com.havivon.pigeon.ui.spam

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.analysis.LinkAnalyzer
import com.havivon.pigeon.domain.analysis.LinkInfo
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.model.SpamReason

/**
 * One row of the Spam folder: a sender and every spam conversation with them. The system may keep one
 * sender in several conversations ("+972 52..." and "052..."), so they are grouped by [spamSenderKey].
 */
data class SpamGroup(
    /** The sender, normalised: the same for every way its number is written. */
    val key: String,
    /** The newest conversation's title: the number or the sender id (a contact is never spam). */
    val name: String,
    /** When the newest message came. */
    val time: String,
    /** The sender's incoming messages, in all its conversations. */
    val messageCount: Int,
    /** Every reason given for its conversations, each once, in the order first given. */
    val reasons: List<SpamReason>,
    /** Its spam conversations, newest first. */
    val conversations: List<Conversation>,
) {
    val threadIds: Set<Long> get() = conversations.mapTo(LinkedHashSet()) { it.id }

    /** Every address the conversations have, as the system wrote them: "Not spam" must reach all of them. */
    val addresses: List<String> get() = conversations.flatMap { it.addresses }.distinct()
}

/** One message of an opened [SpamGroup]: its text with the links found in it, or, for a locked chat, only that. */
data class SpamMessage(
    val id: Long,
    val threadId: Long,
    val text: String,
    val time: String,
    val links: List<LinkInfo> = emptyList(),
    /** A locked conversation: [text] is the "locked chat" line, nothing of its messages. */
    val locked: Boolean = false,
)

/**
 * The key a spam conversation's sender is grouped by: its addresses as lookup keys (a number's last digits,
 * with +972 and 0 the same; a sender id in lower case). A conversation without an address is a group of its own.
 */
fun spamSenderKey(c: Conversation): String =
    c.addresses.mapNotNull { PhoneNumbers.lookupKey(it) }.distinct().sorted().joinToString(",").ifEmpty { "thread:${c.id}" }

/** [spam] (newest first) as one group per sender, the sender with the newest message first. */
fun groupSpam(spam: List<Conversation>): List<SpamGroup> =
    spam.groupBy(::spamSenderKey).map { (key, conversations) ->
        val newest = conversations.first()
        SpamGroup(
            key = key,
            name = newest.name,
            time = newest.time,
            // A conversation that has not been counted (no incoming row read) still holds its last message
            messageCount = conversations.sumOf { it.messageCount.coerceAtLeast(1) },
            reasons = conversations.flatMap { it.spamReasons }.distinct(),
            conversations = conversations,
        )
    }

/**
 * An opened group's messages, newest first: the incoming messages of [messages] (its conversations' pages),
 * and one "locked chat" line for each locked conversation, whose messages are never read.
 */
fun spamMessages(group: SpamGroup, messages: List<SmsMessage>, time: (Long) -> String): List<SpamMessage> {
    val locked = group.conversations.filter { it.locked }.map { SpamMessage(-it.id, it.id, it.snippet, it.time, locked = true) }
    val lockedIds = group.conversations.filter { it.locked }.mapTo(HashSet()) { it.id }
    val read = messages
        .filter { !it.type.outgoing && it.threadId in group.threadIds && it.threadId !in lockedIds }
        .sortedWith(compareByDescending<SmsMessage> { it.dateMillis }.thenByDescending { it.id })
        .map { SpamMessage(it.id, it.threadId, it.body, time(it.dateMillis), LinkAnalyzer.findLinks(it.body)) }
    return read + locked
}
