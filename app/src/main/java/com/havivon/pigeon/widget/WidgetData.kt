package com.havivon.pigeon.widget

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.Favorite
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.seam.CodeFinder
import com.havivon.pigeon.ui.common.DateLabels
import com.havivon.pigeon.ui.common.avatarColor

/*
 * What each widget draws, as plain data. The `*WidgetContent` composables take these, so the home
 * screen (real data, see WidgetSource) and the design snapshots (sample data) use the same drawing.
 */

/** One unread conversation in the list widget. */
data class WidgetUnreadRow(
    val threadId: Long,
    val name: String,
    /** The last message; blank in counts-only mode. */
    val snippet: String,
    val unread: Int,
    val time: String,
    val avatarArgb: Long,
    /** The colour of the rule that matches it, drawn as a strip. */
    val ruleArgb: Long?,
)

data class UnreadWidgetData(val rows: List<WidgetUnreadRow>, val totalUnread: Int)

/** The latest verification code. [ageMinutes] is how long ago it arrived. */
data class CodeWidgetData(val code: String, val sender: String, val threadId: Long, val ageMinutes: Long)

data class WidgetContact(val threadId: Long, val name: String, val avatarArgb: Long)

data class ContactsWidgetData(val contacts: List<WidgetContact>)

data class CounterWidgetData(val unread: Int, val spam: Int)

data class WidgetFavorite(val threadId: Long, val sender: String, val text: String, val date: String, val avatarArgb: Long)

data class FavoritesWidgetData(val rows: List<WidgetFavorite>)

/** The code as the widget draws it: bullets of the same length while the widgets show counts only. */
fun displayCode(code: String, showContent: Boolean): String = if (showContent) code else "•".repeat(code.length)

/**
 * Builds the widgets' data from the app's own models. Pure, so it is tested on the JVM.
 *
 * What never reaches a widget: anything of a locked conversation (its row, code or star), spam
 * (it is only counted), and, in counts-only mode ("widgets show text" off), message text. The
 * unread counter is a number only, like the inbox badge, so it counts locked conversations too.
 */
object WidgetDataBuilder {

    /** Rows kept per widget: more than any launcher cell grid shows, so the RemoteViews stay small. */
    const val MAX_ROWS = 10
    const val MAX_CONTACTS = 5

    /** How many of the newest incoming messages are searched for the latest code. */
    const val CODE_SEARCH_DEPTH = 30

    fun unread(conversations: List<Conversation>, rules: List<ColorRule>, showText: Boolean): UnreadWidgetData {
        val rows = conversations
            .filter { it.unread > 0 && !it.isSpam && !it.locked }
            .map { c ->
                WidgetUnreadRow(
                    threadId = c.id,
                    name = c.name,
                    snippet = if (showText) c.snippet else "",
                    unread = c.unread,
                    time = c.time,
                    avatarArgb = c.avatarArgb,
                    ruleArgb = rules.firstOrNull { it.enabled && it.id == c.ruleId }?.argb,
                )
            }
        return UnreadWidgetData(rows.take(MAX_ROWS), rows.sumOf { it.unread })
    }

    /**
     * The newest incoming message with a code, from [incoming] (newest first). Messages of
     * [excludedThreads] (locked or spam conversations) are skipped.
     */
    fun code(
        incoming: List<SmsMessage>,
        excludedThreads: Set<Long>,
        codeFinder: CodeFinder,
        senderOf: (SmsMessage) -> String,
        nowMillis: Long,
    ): CodeWidgetData? {
        for (m in incoming.take(CODE_SEARCH_DEPTH)) {
            if (m.type.outgoing || m.threadId in excludedThreads) continue
            val code = codeFinder.find(m.body, m.address, knownPerson = false) ?: continue
            return CodeWidgetData(code, senderOf(m), m.threadId, ((nowMillis - m.dateMillis) / 60_000L).coerceAtLeast(0))
        }
        return null
    }

    /** The pinned conversations first, then the most recent personal ones. */
    fun contacts(conversations: List<Conversation>): ContactsWidgetData {
        val usable = conversations.filter { !it.isSpam && !it.locked }
        val ordered = usable.filter { it.pinned } + usable.filter { !it.pinned && it.folder == Folder.Personal }
        return ContactsWidgetData(ordered.take(MAX_CONTACTS).map { WidgetContact(it.id, it.name, it.avatarArgb) })
    }

    fun counter(conversations: List<Conversation>): CounterWidgetData =
        CounterWidgetData(unread = conversations.filter { !it.isSpam }.sumOf { it.unread }, spam = conversations.count { it.isSpam })

    fun favorites(favorites: List<Favorite>, lockedThreads: Set<Long>, labels: DateLabels, showText: Boolean): FavoritesWidgetData =
        FavoritesWidgetData(
            favorites
                .filter { it.threadId !in lockedThreads }
                .take(MAX_ROWS)
                .map { f ->
                    WidgetFavorite(
                        threadId = f.threadId,
                        sender = f.senderName ?: f.address,
                        text = if (showText) f.body else "",
                        date = labels.shortDate(f.dateMillis),
                        avatarArgb = avatarColor(PhoneNumbers.normalize(f.address)),
                    )
                },
        )
}
