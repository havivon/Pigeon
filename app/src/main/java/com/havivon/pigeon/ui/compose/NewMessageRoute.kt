package com.havivon.pigeon.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.app.LocalNotices
import com.havivon.pigeon.ui.navigation.pigeonViewModel

/**
 * The new-message screen on real data. [recipients] and [body] come prefilled from an `sms:` link;
 * nothing is sent until the user taps Send. One recipient: their conversation opens in its place.
 */
@Composable
fun NewMessageRoute(recipients: List<String>, body: String, onBack: () -> Unit, openThreadInstead: (Long) -> Unit) {
    val context = LocalContext.current
    val vm = pigeonViewModel { app -> NewMessageViewModel(app.contacts, app.messages, app.smsSender, recipients, body, app.appScope) }
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.queryText.collectAsStateWithLifecycle()
    val text by vm.bodyText.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val failed = stringResource(R.string.thread_send_failed)
    LaunchedEffect(vm) {
        vm.eventFlow.collect { e ->
            when (e) {
                is NewMessageEvent.OpenThread -> openThreadInstead(e.threadId)
                is NewMessageEvent.SentToMany -> {
                    notices.show(context.resources.getQuantityString(R.plurals.compose_sent_to_many, e.count, e.count))
                    onBack()
                }
                NewMessageEvent.Failed -> notices.show(failed)
            }
        }
    }
    NewMessageScreen(
        state = state.copy(query = query, body = text),
        onBack = onBack,
        onQuery = vm::setQuery,
        onCommitTyped = { vm.commitTyped() },
        onAdd = vm::add,
        onRemove = vm::remove,
        onBody = vm::setBody,
        onSend = vm::send,
    )
}
