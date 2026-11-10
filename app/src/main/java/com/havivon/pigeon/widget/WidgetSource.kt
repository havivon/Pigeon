package com.havivon.pigeon.widget

import android.content.Context
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.ui.common.conversationMapper
import com.havivon.pigeon.ui.common.dateLabels
import com.havivon.pigeon.ui.common.verdictFor
import com.havivon.pigeon.ui.theme.Skin
import kotlinx.coroutines.flow.first

/**
 * Reads the widgets' data from the app's repositories, each widget asking for just what it draws.
 * The conversations go through the same mapper as the inbox, so a locked conversation's text and
 * code are already blanked, and a spam conversation is marked.
 */
internal class WidgetSource(private val context: Context) {

    private val app = AppContainer.from(context)

    /** Whether to show message text, or only counts (the "widgets show text" setting). */
    suspend fun showText(): Boolean = WidgetSettings.showContent(context)

    /** The colour skin the app is set to. */
    suspend fun skin(): Skin = Skin.fromId(app.settings.settings.first().skin)

    private suspend fun conversations(): List<Conversation> {
        val mapper = conversationMapper(context, app)
        val now = System.currentTimeMillis()
        val rules = app.rules.rules.first()
        val pinned = app.pins.pinnedThreadIds.first()
        val locked = app.locks.lockedThreadIds.first()
        val verdicts = app.spam.verdicts.first()
        return app.messages.conversations.first().map { mapper.map(it, now, rules, pinned, locked, verdictFor(it, verdicts)) }
    }

    suspend fun unread(): UnreadWidgetData =
        WidgetDataBuilder.unread(conversations(), app.rules.rules.first(), showText())

    suspend fun contacts(): ContactsWidgetData = WidgetDataBuilder.contacts(conversations())

    suspend fun counter(): CounterWidgetData = WidgetDataBuilder.counter(conversations())

    suspend fun code(): CodeWidgetData? {
        val all = conversations()
        val hidden = all.filter { it.locked || it.isSpam }.map { it.id }.toSet()
        val names = all.associate { it.id to it.name }
        val incoming = app.messages.recentIncoming(WidgetDataBuilder.CODE_SEARCH_DEPTH)
        return WidgetDataBuilder.code(incoming, hidden, app.codeFinder, { names[it.threadId] ?: it.address }, System.currentTimeMillis())
    }

    suspend fun favorites(): FavoritesWidgetData = WidgetDataBuilder.favorites(
        favorites = app.favorites.favorites.first(),
        lockedThreads = app.locks.lockedNow(),
        labels = dateLabels(context),
        showText = showText(),
    )
}
