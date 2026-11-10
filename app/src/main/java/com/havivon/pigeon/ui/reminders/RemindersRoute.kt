package com.havivon.pigeon.ui.reminders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.R
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.ui.app.LocalNotices
import com.havivon.pigeon.ui.common.dateLabels
import com.havivon.pigeon.ui.navigation.pigeonViewModel
import kotlinx.coroutines.launch

/** The reminders on real data, opened from the settings and from the inbox's bell. A tap opens the conversation at the reminder's message. */
@Composable
fun RemindersRoute(onBack: () -> Unit, openThread: (threadId: Long, messageId: Long?) -> Unit) {
    val context = LocalContext.current
    val appScope = remember { AppContainer.from(context).appScope }
    val vm = pigeonViewModel { app ->
        val labels = dateLabels(context)
        RemindersViewModel(
            app.reminders, app.reminderScheduler, app.messages, app.contacts, app.locks,
            whenLabel = { millis, now -> labels.reminder(millis, now, withTime = true) },
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val scope = rememberCoroutineScope()
    var changing by remember { mutableStateOf<ReminderRow?>(null) }

    RemindersScreen(
        upcoming = state.upcoming,
        past = state.past,
        loaded = state.loaded,
        onBack = onBack,
        onOpen = { openThread(it.threadId, it.messageId) },
        onChangeTime = { changing = it },
        onCancel = { row ->
            scope.launch {
                val removed = vm.cancel(row.id) ?: return@launch
                // The undo outlives the screen: it runs in the app's scope
                notices.undo(context.getString(R.string.reminders_cancelled)) { appScope.launch { vm.restore(removed) } }
            }
        },
    )

    changing?.let { row ->
        ReminderSheet(
            onChoose = { at ->
                changing = null
                scope.launch {
                    if (vm.reschedule(row.id, at)) {
                        notices.show(context.getString(R.string.reminders_moved, dateLabels(context).reminder(at, System.currentTimeMillis(), withTime = true)))
                    }
                }
            },
            onDismiss = { changing = null },
        )
    }
}
