package com.havivon.pigeon.ui.common

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.analysis.DateTimeExtractor
import com.havivon.pigeon.domain.analysis.RuleMatcher
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.Message
import com.havivon.pigeon.domain.model.SendStatus
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.SpamVerdict
import com.havivon.pigeon.domain.model.ThreadItem
import com.havivon.pigeon.domain.seam.CodeFinder
import com.havivon.pigeon.domain.seam.FolderClassifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns the platform's records ([SmsConversation], [SmsMessage]) and the app's own data (rules,
 * pins, locks, spam verdicts) into the models the screens draw ([Conversation], [Message]).
 */
class ConversationMapper(
    private val labels: DateLabels,
    private val codeFinder: CodeFinder,
    private val folders: FolderClassifier,
    /** What a locked conversation shows instead of its last message. */
    private val lockedSnippet: String,
) {
    fun map(
        c: SmsConversation,
        nowMillis: Long,
        rules: List<ColorRule> = emptyList(),
        pinned: Set<Long> = emptySet(),
        locked: Set<Long> = emptySet(),
        verdict: SpamVerdict? = null,
    ): Conversation {
        val isLocked = c.threadId in locked
        val address = c.addresses.firstOrNull().orEmpty()
        // Nothing of a locked conversation's text: not its snippet, its code, nor a keyword rule's colour
        val body = if (isLocked) "" else c.snippet
        val spam = verdict != null
        return Conversation(
            id = c.threadId,
            name = title(c),
            snippet = if (isLocked) lockedSnippet else c.snippet,
            time = labels.listTime(c.dateMillis, nowMillis),
            avatarArgb = if (spam) SpamArgb else avatarColor(PhoneNumbers.normalize(address)),
            unread = c.unreadCount,
            pinned = c.threadId in pinned,
            ruleId = if (spam) null else RuleMatcher.firstMatch(rules, c.contactName, address, body)?.id,
            folder = if (spam) Folder.Spam else folders.classify(c),
            code = if (isLocked || spam) null else codeFinder.find(body),
            // A verdict always shows at least one reason, so the screens can tell it is spam
            spamReasons = verdict?.reasons?.ifEmpty { listOf(SpamReason.MarkedByUser) }.orEmpty(),
            addresses = c.addresses,
            locked = isLocked,
        )
    }

    companion object {
        /** The contact's name, otherwise the number(s), kept left to right. */
        fun title(c: SmsConversation): String = c.contactName ?: c.addresses.joinToString(", ") { ltr(it) }
    }
}

/** The stored verdict on one of the conversation's senders, or null when it is not spam. */
fun verdictFor(c: SmsConversation, verdicts: List<SpamVerdict>): SpamVerdict? =
    verdicts.firstOrNull { v -> c.addresses.any { PhoneNumbers.same(it, v.address) } }

/** The conversations a folder chip shows: spam only under Spam, and only there. */
fun inFolder(all: List<Conversation>, folder: Folder): List<Conversation> = when (folder) {
    Folder.All -> all.filter { !it.isSpam }
    Folder.Spam -> all.filter { it.isSpam }
    else -> all.filter { !it.isSpam && it.folder == folder }
}

/** The numbers on the folder chips: unread messages per folder, and the number of spam conversations. */
fun folderCounts(all: List<Conversation>): Map<Folder, Int> = Folder.entries.associateWith { folder ->
    if (folder == Folder.Spam) all.count { it.isSpam } else inFolder(all, folder).sumOf { it.unread }
}

/** Builds a conversation's messages, oldest first, with a day separator before each new day. */
fun threadItems(messages: List<SmsMessage>, labels: DateLabels, nowMillis: Long, toMessage: (SmsMessage) -> Message): List<ThreadItem> {
    val items = ArrayList<ThreadItem>(messages.size + 8)
    var lastDay: LocalDate? = null
    for (m in messages) {
        val day = labels.date(m.dateMillis)
        if (day != lastDay) {
            items += ThreadItem.Day(labels.day(m.dateMillis, nowMillis))
            lastDay = day
        }
        items += ThreadItem.Msg(toMessage(m))
    }
    return items
}

fun sendStatus(type: SmsType): SendStatus = when (type) {
    SmsType.Outbox, SmsType.Queued -> SendStatus.Sending
    SmsType.Failed -> SendStatus.Failed
    else -> SendStatus.Done
}

/** A reminder found in a message: when it fires and whether a time was named. */
data class ReminderMatch(val atMillis: Long, val hasTime: Boolean)

/**
 * The first date or time in [body] that is still ahead, read relative to when the message was
 * written ("tomorrow" in yesterday's message is today). Null when there is none.
 */
fun findReminder(body: String, messageMillis: Long, nowMillis: Long, zone: ZoneId): ReminderMatch? {
    val written = Instant.ofEpochMilli(messageMillis).atZone(zone).toLocalDateTime()
    return DateTimeExtractor.extract(body, written)
        .map { ReminderMatch(it.dateTime.atZone(zone).toInstant().toEpochMilli(), it.hasTime) }
        .firstOrNull { it.atMillis > nowMillis }
}

/**
 * The selected messages as one text for the clipboard or a share, oldest first, a line per
 * message: "[time] name: text".
 */
fun formatForCopy(messages: List<SmsMessage>, labels: DateLabels, me: String, nameOf: (SmsMessage) -> String): String {
    val sorted = messages.sortedWith(compareBy({ it.dateMillis }, { it.id }))
    val spansDays = sorted.map { labels.date(it.dateMillis) }.distinct().size > 1
    return sorted.joinToString("\n") { m ->
        val who = if (m.type.outgoing) me else withoutIsolates(nameOf(m))
        "[${labels.stamp(m.dateMillis, spansDays)}] $who: ${m.body}"
    }
}
