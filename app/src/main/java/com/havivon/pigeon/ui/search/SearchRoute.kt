package com.havivon.pigeon.ui.search

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.R
import com.havivon.pigeon.data.SensitiveClipboard
import com.havivon.pigeon.domain.folders.FolderMoves
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.ui.app.LocalNotices
import com.havivon.pigeon.ui.common.conversationMapper
import com.havivon.pigeon.ui.common.dateLabels
import com.havivon.pigeon.ui.common.reservedFolderNames
import com.havivon.pigeon.ui.inbox.ConversationSheetActions
import com.havivon.pigeon.ui.inbox.ConversationSheets
import com.havivon.pigeon.ui.navigation.pigeonViewModel
import com.havivon.pigeon.ui.reminders.ReminderSheet
import kotlinx.coroutines.launch

@Composable
fun SearchRoute(onBack: () -> Unit, openThread: (Long) -> Unit, openMessage: (threadId: Long, messageId: Long) -> Unit) {
    val context = LocalContext.current
    val me = stringResource(R.string.thread_sender_me)
    val vm = pigeonViewModel { app ->
        val labels = dateLabels(context)
        val sheet = ConversationSheetActions(
            app.messages, app.pins, app.locks, FolderMoves(app.folders, app.spam, reservedFolderNames(context)),
            app.reminders, app.reminderScheduler, app.appScope, app.notifier::cancel, app::canWrite,
        )
        SearchViewModel(
            app.messages, conversationMapper(context, app, labels), labels, app.codeFinder,
            app.rules, app.pins, app.spam, app.folders, app.favorites, sheet, me,
            lockedThreads = app.locks.lockedThreadIds,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.queryText.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val customFolders by vm.customFolders.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val scope = rememberCoroutineScope()
    var sheetFor by remember { mutableStateOf<Conversation?>(null) }
    var remindSheet by remember { mutableStateOf(false) }
    val reminderCreated = stringResource(R.string.thread_reminder_created)

    // Back leaves the selection first
    BackHandler(enabled = selected.isNotEmpty()) { vm.clearSelection() }

    SearchScreen(
        state = state.copy(query = query, searching = state.searching || state.query != query),
        onQuery = vm::setQuery,
        onBack = onBack,
        onSender = vm::setSender,
        onHasLink = vm::toggleHasLink,
        onHasCode = vm::toggleHasCode,
        onStarred = vm::toggleStarred,
        onDates = vm::setDates,
        onOpenConversation = openThread,
        // The same sheet as in the inbox
        onLongPressConversation = { sheetFor = it },
        // A tap adds or removes a result while selecting, otherwise opens the conversation at that message
        onMessageClick = { hit -> if (selected.isNotEmpty()) vm.toggleSelected(hit.messageId) else openMessage(hit.threadId, hit.messageId) },
        onMessageLongClick = { hit -> if (selected.isEmpty()) vm.select(hit.messageId) else vm.toggleSelected(hit.messageId) },
        selectedIds = selected,
        selectedStarred = vm.allSelectedStarred(),
        onClearSelection = vm::clearSelection,
        onStarSelected = vm::starSelected,
        onRemindSelected = if (selected.size == 1) ({ remindSheet = true }) else null,
        onCopySelected = {
            SensitiveClipboard.copy(context, context.getString(R.string.app_name), vm.selectedText(), R.string.thread_copied)
            vm.clearSelection()
        },
        onShareSelected = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, vm.selectedText())
            try {
                context.startActivity(Intent.createChooser(send, null))
            } catch (_: ActivityNotFoundException) {
                // No app can take text: nothing to offer
            }
            vm.clearSelection()
        },
    )

    ConversationSheets(sheetFor, onDone = { sheetFor = null }, actions = vm.sheet, customFolders = customFolders)

    if (remindSheet) {
        ReminderSheet(
            onChoose = { at ->
                remindSheet = false
                scope.launch {
                    if (vm.remindSelected(at)) notices.show(String.format(reminderCreated, dateLabels(context).reminder(at, System.currentTimeMillis(), withTime = true)))
                }
            },
            onDismiss = { remindSheet = false },
        )
    }
}
