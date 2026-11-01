package com.havivon.pigeon.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.ui.common.conversationMapper
import com.havivon.pigeon.ui.common.dateLabels
import com.havivon.pigeon.ui.navigation.pigeonViewModel

@Composable
fun SearchRoute(onBack: () -> Unit, openThread: (Long) -> Unit) {
    val context = LocalContext.current
    val vm = pigeonViewModel { app ->
        val labels = dateLabels(context)
        SearchViewModel(app.messages, conversationMapper(context, app, labels), labels, app.codeFinder, lockedThreads = app.locks.lockedThreadIds, favorites = app.favorites.favorites)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.queryText.collectAsStateWithLifecycle()
    SearchScreen(
        state = state.copy(query = query, searching = state.searching || state.query != query),
        onQuery = vm::setQuery,
        onBack = onBack,
        onSender = vm::setSender,
        onHasLink = vm::toggleHasLink,
        onHasCode = vm::toggleHasCode,
        onStarred = vm::toggleStarred,
        onRange = vm::setRange,
        onOpenConversation = openThread,
    )
}
