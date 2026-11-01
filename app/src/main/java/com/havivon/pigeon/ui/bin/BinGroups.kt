package com.havivon.pigeon.ui.bin

import com.havivon.pigeon.domain.bin.BinMessage
import com.havivon.pigeon.domain.bin.RecycleBin
import com.havivon.pigeon.ui.common.ltr

/** One row of the recycle bin: a conversation's deleted messages (a sender, or a group's senders). */
data class BinGroup(
    /** The conversation's addresses, however each number is written ([RecycleBin.conversationKey]). */
    val key: String,
    /** The contact's name, otherwise the number(s). */
    val name: String,
    /** Its messages in the bin, newest first. A locked chat's show only the locked line until it is unlocked. */
    val messages: List<BinRow>,
    /** When the newest of them was deleted, as the lists write a time ("14:05", "Yesterday", "3 Nov"). */
    val deletedLabel: String,
    /** The days until the first of them is deleted for good. */
    val daysLeft: Int,
    /** From a locked chat, and not unlocked yet: nothing of its messages shows. */
    val locked: Boolean,
    /** The bin ids of its messages. */
    val ids: Set<Long>,
) {
    val messageCount: Int get() = ids.size
}

/** One deleted message, as the bin shows it. */
data class BinRow(val id: Long, val text: String, val time: String, val outgoing: Boolean, val locked: Boolean)

/**
 * [messages] (newest deletion first) as one group per conversation, the newest deletion first. [names] are the
 * contacts' names by address; a locked chat's messages show [lockedText] unless its key is in [unlocked].
 */
fun groupBin(
    messages: List<BinMessage>,
    nowMillis: Long,
    names: Map<String, String?> = emptyMap(),
    unlocked: Set<String> = emptySet(),
    lockedText: String = "",
    time: (Long) -> String = { it.toString() },
): List<BinGroup> =
    messages.groupBy { RecycleBin.conversationKey(it) }.map { (key, list) ->
        val addresses = list.first().threadAddresses.ifEmpty { listOf(list.first().address) }
        val hidden = list.any { it.locked } && key !in unlocked
        val newestFirst = list.sortedWith(compareByDescending<BinMessage> { it.dateMillis }.thenByDescending { it.originalId })
        BinGroup(
            key = key,
            name = addresses.joinToString(", ") { names[it] ?: ltr(it) },
            // A locked chat: one line in place of its messages, nothing of their text
            messages = if (hidden) {
                listOf(BinRow(-newestFirst.first().id, lockedText, time(newestFirst.first().dateMillis), outgoing = false, locked = true))
            } else {
                newestFirst.map { m -> BinRow(m.id, m.body, time(m.dateMillis), m.type.outgoing, locked = false) }
            },
            deletedLabel = time(list.maxOf { it.deletedAtMillis }),
            daysLeft = RecycleBin.daysLeft(list.minOf { it.deletedAtMillis }, nowMillis),
            locked = hidden,
            ids = list.mapTo(LinkedHashSet()) { it.id },
        )
    }
