package com.havivon.pigeon.ui.common

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.analysis.DateTimeExtractor
import com.havivon.pigeon.domain.folders.FolderKeys
import com.havivon.pigeon.domain.analysis.RuleMatcher
import com.havivon.pigeon.domain.model.Categories
import com.havivon.pigeon.domain.model.CategoryTag
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
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
 * pins, locks, spam verdicts, moves by hand) into the models the screens draw ([Conversation], [Message]).
 */
class ConversationMapper(
    private val labels: DateLabels,
    private val codeFinder: CodeFinder,
    private val folders: FolderClassifier,
    /** What a locked conversation shows instead of its last message. */
    private val lockedSnippet: String,
    /** A built-in folder's name, as the user reads it (the text of a category tag). */
    private val folderLabel: (Folder) -> String = { it.name },
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
        /**
         * Where the user moved it by hand ([overrideFor]): ahead of the automatic folder, but not of a spam
         * [verdict] (marking spam drops the move, and a filed sender is not judged again).
         */
        override: FolderChoice? = null,
        /** The user's categories and their colours, for the conversation's category tag ([categoryTag]). */
        categories: Categories = Categories.None,
    ): Conversation {
        val isLocked = c.threadId in locked
        val address = c.addresses.firstOrNull().orEmpty()
        // Nothing of a locked conversation's text: not its snippet, its code, nor a keyword rule's colour
        val body = if (isLocked) "" else c.snippet
        val spam = verdict != null
        val analysis = analysis(c, address)
        val moved = override?.takeIf { !spam }
        return Conversation(
            id = c.threadId,
            name = title(c),
            snippet = if (isLocked) lockedSnippet else c.snippet,
            time = labels.listTime(c.dateMillis, nowMillis),
            avatarArgb = if (spam) SpamArgb else avatarColor(PhoneNumbers.normalize(address)),
            unread = c.unreadCount,
            pinned = c.threadId in pinned,
            ruleId = if (spam) null else RuleMatcher.firstMatch(rules, c.contactName, address, body)?.id,
            folder = when {
                spam -> Folder.Spam
                moved is FolderChoice.BuiltIn -> moved.folder
                // In one of the user's categories: no built-in folder (it shows under All and its category)
                moved is FolderChoice.Custom -> Folder.All
                else -> analysis.folder
            },
            code = if (isLocked || spam) null else analysis.code,
            // A verdict always shows at least one reason, so the screens can tell it is spam
            spamReasons = verdict?.reasons?.ifEmpty { listOf(SpamReason.MarkedByUser) }.orEmpty(),
            addresses = c.addresses,
            locked = isLocked,
            messageCount = c.incomingCount,
            customFolderId = (moved as? FolderChoice.Custom)?.id,
            folderOverridden = moved != null,
            category = categoryTag(spam, moved ?: FolderChoice.BuiltIn(analysis.folder), categories),
        )
    }

    /**
     * The tag of the category a conversation is in: "Spam" (neutral, with no colour of its own) for spam, otherwise
     * the user's category or the built-in folder it is in (the move by hand first, else the automatic one), with that
     * category's colour; null for no category (only "All").
     */
    private fun categoryTag(spam: Boolean, where: FolderChoice, categories: Categories): CategoryTag? = when {
        spam -> CategoryTag(folderLabel(Folder.Spam))
        where is FolderChoice.Custom -> categories.custom.firstOrNull { it.id == where.id }?.let { CategoryTag(it.name, it.argb) }
        where is FolderChoice.BuiltIn && where.folder != Folder.All -> CategoryTag(folderLabel(where.folder), categories.colorOf(where))
        else -> null
    }

    /** A message's time as the lists show it: the time today, otherwise the day or the date. */
    fun time(millis: Long, nowMillis: Long): String = labels.listTime(millis, nowMillis)

    /** The code in an unread message's text, by message id ("" for none): a text never changes, the list is mapped again on every change. */
    private val unreadCodes = ConcurrentHashMap<Long, String>()

    /**
     * The inbox's unread rows: [unread] (incoming, unread) as one row per message, newest first, of the
     * conversations in [folder] ([Folder.All] is every folder). The messages of a spam conversation are
     * left out; those of a locked one show only [lockedSnippet], no code and no keyword colour. A message
     * whose conversation is not in [conversations] yet (it just arrived) shows its number, under
     * [Folder.All] only: it has no folder yet. [sources] are the conversations as the system gave them:
     * the contact's name is what a sender rule matches. With [customFolder], the rows of that category of the
     * user's instead of [folder].
     */
    fun mapUnread(
        unread: List<SmsMessage>,
        conversations: Map<Long, Conversation>,
        sources: Map<Long, SmsConversation>,
        nowMillis: Long,
        rules: List<ColorRule> = emptyList(),
        locked: Set<Long> = emptySet(),
        folder: Folder = Folder.All,
        customFolder: Long? = null,
    ): List<UnreadMessage> {
        val rows = unread
            .sortedWith(compareByDescending<SmsMessage> { it.dateMillis }.thenByDescending { it.id })
            .mapNotNull { m ->
                val conversation = conversations[m.threadId]
                if (conversation?.isSpam == true) return@mapNotNull null
                if (customFolder != null) {
                    if (conversation?.customFolderId != customFolder) return@mapNotNull null
                } else if (folder != Folder.All && conversation?.folder != folder) {
                    return@mapNotNull null
                }
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
                    category = conversation?.category,
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

/** Where the user moved [c] by hand, by its [FolderKeys] key; null when it is sorted automatically. */
fun overrideFor(c: SmsConversation, overrides: Map<String, FolderChoice>): FolderChoice? =
    if (overrides.isEmpty()) null else overrides[FolderKeys.of(c.threadId, c.addresses)]

/** The conversations a folder chip shows: spam only under Spam, and only there. */
fun inFolder(all: List<Conversation>, folder: Folder): List<Conversation> = when (folder) {
    Folder.All -> all.filter { !it.isSpam }
    Folder.Spam -> all.filter { it.isSpam }
    else -> all.filter { !it.isSpam && it.folder == folder }
}

/** The conversations a chip shows: a built-in folder ([inFolder]) or one of the user's categories (never spam). */
fun inFolder(all: List<Conversation>, choice: FolderChoice): List<Conversation> = when (choice) {
    is FolderChoice.BuiltIn -> inFolder(all, choice.folder)
    is FolderChoice.Custom -> all.filter { !it.isSpam && it.customFolderId == choice.id }
}

/** The numbers on the chips of the user's categories: their unread messages, as on the built-in ones. */
fun customFolderCounts(all: List<Conversation>, folders: List<CustomFolder>): Map<Long, Int> =
    folders.associate { f -> f.id to inFolder(all, FolderChoice.Custom(f.id)).sumOf { it.unread } }

/**
 * Where a conversation is now, as "Move to category" checks it: Spam, the user's category, or its folder
 * (the automatic one, or the one it was moved to); null when it is in no folder but All.
 */
fun currentFolder(c: Conversation): FolderChoice? = when {
    c.isSpam -> FolderChoice.BuiltIn(Folder.Spam)
    c.customFolderId != null -> FolderChoice.Custom(c.customFolderId)
    c.folder == Folder.All -> null
    else -> FolderChoice.BuiltIn(c.folder)
}

/**
 * The numbers on the folder chips: unread messages per folder (the number the "Unread" toggle shows for
 * the chosen folder), and the number of spam conversations on Spam.
 */
fun folderCounts(all: List<Conversation>): Map<Folder, Int> = Folder.entries.associateWith { folder ->
    when (folder) {
        Folder.Spam -> all.count { it.isSpam }
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

