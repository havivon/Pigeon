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
import com.havivon.pigeon.domain.model.UnreadMessage
import com.havivon.pigeon.domain.seam.CodeFinder
import com.havivon.pigeon.domain.seam.FolderClassifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
    /** The folder and code of a conversation's last message: the analysis, done again only when that message changes. */
    private data class Analysis(val sender: String?, val address: String, val snippet: String, val userWrote: Boolean, val folder: Folder, val code: String?)

    /** By thread. The inbox maps every conversation on every change (a pin, a verdict), and most did not change. */
    private val analyses = ConcurrentHashMap<Long, Analysis>()

    private fun analysis(c: SmsConversation, address: String): Analysis {
        analyses[c.threadId]?.takeIf { it.sender == c.contactName && it.address == address && it.snippet == c.snippet && it.userWrote == c.userWrote }
            ?.let { return it }
        return Analysis(c.contactName, address, c.snippet, c.userWrote, folders.classify(c), codeFinder.find(c.snippet)).also { analyses[c.threadId] = it }
    }

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
        val analysis = analysis(c, address)
        return Conversation(
            id = c.threadId,
            name = title(c),
            snippet = if (isLocked) lockedSnippet else c.snippet,
            time = labels.listTime(c.dateMillis, nowMillis),
            avatarArgb = if (spam) SpamArgb else avatarColor(PhoneNumbers.normalize(address)),
            unread = c.unreadCount,
            pinned = c.threadId in pinned,
            ruleId = if (spam) null else RuleMatcher.firstMatch(rules, c.contactName, address, body)?.id,
            folder = if (spam) Folder.Spam else analysis.folder,
            code = if (isLocked || spam) null else analysis.code,
            // A verdict always shows at least one reason, so the screens can tell it is spam
            spamReasons = verdict?.reasons?.ifEmpty { listOf(SpamReason.MarkedByUser) }.orEmpty(),
            addresses = c.addresses,
            locked = isLocked,
            messageCount = c.incomingCount,
        )
    }

    /** A message's time as the lists show it: the time today, otherwise the day or the date. */
    fun time(millis: Long, nowMillis: Long): String = labels.listTime(millis, nowMillis)

    /** The code in an unread message's text, by message id ("" for none): a text never changes, the list is mapped again on every change. */
    private val unreadCodes = ConcurrentHashMap<Long, String>()

    /**
     * The inbox's Unread rows: [unread] (incoming, unread) as one row per message, newest first. The
     * messages of a spam conversation are left out; those of a locked one show only [lockedSnippet],
     * no code and no keyword colour. A message whose conversation is not in [conversations] yet (it
     * just arrived) shows its number. [sources] are the conversations as the system gave them: the
     * contact's name is what a sender rule matches.
     */
    fun mapUnread(
        unread: List<SmsMessage>,
        conversations: Map<Long, Conversation>,
        sources: Map<Long, SmsConversation>,
        nowMillis: Long,
        rules: List<ColorRule> = emptyList(),
        locked: Set<Long> = emptySet(),
    ): List<UnreadMessage> {
        val rows = unread
            .sortedWith(compareByDescending<SmsMessage> { it.dateMillis }.thenByDescending { it.id })
            .mapNotNull { m ->
                val conversation = conversations[m.threadId]
                if (conversation?.isSpam == true) return@mapNotNull null
                val isLocked = m.threadId in locked
                val code = if (isLocked) null else unreadCodes.getOrPut(m.id) { codeFinder.find(m.body).orEmpty() }.ifEmpty { null }
                UnreadMessage(
                    messageId = m.id,
                    threadId = m.threadId,
                    name = conversation?.name ?: ltr(m.address),
                    text = if (isLocked) lockedSnippet else m.body,
                    time = labels.listTime(m.dateMillis, nowMillis),
                    avatarArgb = conversation?.avatarArgb ?: avatarColor(PhoneNumbers.normalize(m.address)),
                    ruleId = RuleMatcher.firstMatch(rules, sources[m.threadId]?.contactName, m.address, if (isLocked) "" else m.body)?.id,
                    code = code,
                    locked = isLocked,
                )
            }
        // Messages that left the list (read, deleted) take their cached code with them
        unreadCodes.keys.retainAll(unread.mapTo(HashSet()) { it.id })
        return rows
    }

    companion object {
        /** The contact's name, otherwise the number(s), kept left to right. */
        fun title(c: SmsConversation): String = c.contactName ?: c.addresses.joinToString(", ") { ltr(it) }
    }
}

/** Above this many conversations, [mapAll] spreads the work over the cores. */
private const val MAP_CHUNK = 100

/**
 * [items] mapped by [map], in order, on several threads of the caller's dispatcher when the list is long:
 * the inbox maps hundreds of conversations (folders, codes, rules) on its first read. [map] must be safe
 * to run in parallel.
 */
suspend fun <T, R> mapAll(items: List<T>, map: (T) -> R): List<R> =
    if (items.size <= MAP_CHUNK) {
        items.map(map)
    } else {
        coroutineScope { items.chunked(MAP_CHUNK).map { part -> async { part.map(map) } }.awaitAll().flatten() }
    }

/** The stored verdict on one of the conversation's senders, or null when it is not spam. */
fun verdictFor(c: SmsConversation, verdicts: List<SpamVerdict>): SpamVerdict? =
    verdicts.firstOrNull { v -> c.addresses.any { PhoneNumbers.same(it, v.address) } }

/**
 * The conversations a folder chip shows: spam only under Spam, and only there. Unread shows no conversations:
 * its rows are the unread messages themselves ([ConversationMapper.mapUnread]).
 */
fun inFolder(all: List<Conversation>, folder: Folder): List<Conversation> = when (folder) {
    Folder.All -> all.filter { !it.isSpam }
    Folder.Unread -> emptyList()
    Folder.Spam -> all.filter { it.isSpam }
    else -> all.filter { !it.isSpam && it.folder == folder }
}

/**
 * The numbers on the folder chips: unread messages per folder, all the unread messages (spam aside) on
 * Unread, and the number of spam conversations on Spam.
 */
fun folderCounts(all: List<Conversation>): Map<Folder, Int> = Folder.entries.associateWith { folder ->
    when (folder) {
        Folder.Spam -> all.count { it.isSpam }
        Folder.Unread -> all.filter { !it.isSpam }.sumOf { it.unread }
        else -> inFolder(all, folder).sumOf { it.unread }
    }
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

