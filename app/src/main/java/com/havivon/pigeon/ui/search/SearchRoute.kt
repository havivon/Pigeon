package com.havivon.pigeon.ui.search

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
import com.havivon.pigeon.ui.app.rememberNeedsDefaultNotice
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
    val selectedConversations by vm.selectedConversations.collectAsStateWithLifecycle()
    val selectionMode by vm.selecting.collectAsStateWithLifecycle()
    val customFolders by vm.customFolders.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val scope = rememberCoroutineScope()
    // The long-press sheets: of a conversation result, of a message result
    var sheetFor by remember { mutableStateOf<Conversation?>(null) }
    var messageSheetFor by remember { mutableStateOf<SearchHit?>(null) }
    var remindSheet by remember { mutableStateOf(false) }
    var moveSheet by remember { mutableStateOf(false) }
    val reminderCreated = stringResource(R.string.thread_reminder_created)
    val needsDefault = rememberNeedsDefaultNotice()
    val markedRead = stringResource(R.string.search_marked_read)
    val movedToSpam = stringResource(R.string.thread_moved_to_spam)
    val selecting = selectionMode
    val count = selected.size + selectedConversations.size

    // Back leaves the selection first
    BackHandler(enabled = selecting) { vm.clearSelection() }

    val actions = selectionActions(
        hasMessages = selected.isNotEmpty(),
        count = count,
        allStarred = vm.allSelectedStarred(),
        onCopy = {
            SensitiveClipboard.copy(context, context.getString(R.string.app_name), vm.selectedText(), R.string.thread_copied)
            vm.clearSelection()
        },
        onShare = {
            shareText(context, vm.selectedText())
            vm.clearSelection()
        },
        onStar = vm::starSelected,
        onMarkRead = { if (vm.markSelectedRead() != null) notices.show(markedRead) else needsDefault() },
        onMove = { moveSheet = true },
        onSpam = {
            vm.markSelectedSpam()
            notices.show(movedToSpam)
        },
        onDelete = {
            vm.deleteSelected()?.let { d ->
                val plural = when {
                    d.conversations == 0 -> R.plurals.thread_messages_deleted
                    d.messages == 0 -> R.plurals.search_conversations_deleted
                    else -> R.plurals.search_results_deleted
                }
                val message = context.resources.getQuantityString(plural, d.count, d.count)
                notices.undo(message, d.deletion)
            } ?: needsDefault()
        },
        onRemind = if (count == 1) ({ remindSheet = true }) else null,
    )

    SearchScreen(
        state = state.copy(query = query, searching = state.searching || state.query != query),
        onQuery = vm::setQuery,
        onBack = onBack,
        onSender = vm::setSender,
        onHasLink = vm::toggleHasLink,
        onHasCode = vm::toggleHasCode,
        onStarred = vm::toggleStarred,
        onDates = vm::setDates,
        // A tap opens the result (while selecting: adds or removes it); a long press opens its sheet of actions (while selecting: adds or removes it too)
        onConversationClick = { conv -> if (selecting) vm.toggleSelectedConversation(conv.id) else openThread(conv.id) },
        onConversationLongClick = { conv -> if (selecting) vm.toggleSelectedConversation(conv.id) else sheetFor = conv },
        onMessageClick = { hit -> if (selecting) vm.toggleSelected(hit.messageId) else openMessage(hit.threadId, hit.messageId) },
        onMessageLongClick = { hit -> if (selecting) vm.toggleSelected(hit.messageId) else messageSheetFor = hit },
        selectionMode = selecting,
        onStartSelecting = vm::startSelecting,
        selectedIds = selected,
        selectedConversationIds = selectedConversations,
        onClearSelection = vm::clearSelection,
        onSelectAll = vm::selectAll,
        actions = if (count > 0) actions else emptyList(),
    )

    // The inbox's own sheet, with open, spam and select added (the long press)
    ConversationSheets(
        sheetFor, onDone = { sheetFor = null }, actions = vm.sheet, customFolders = customFolders,
        onOpen = { openThread(it.id) }, onSelect = { vm.selectConversation(it.id) }, withSpam = true,
    )
    MessageSheet(
        messageSheetFor, vm, customFolders, onDone = { messageSheetFor = null },
        onOpen = { openMessage(it.threadId, it.messageId) }, onSelect = { vm.select(it.messageId) },
    )

    if (moveSheet) MoveSelectionFlow(vm, customFolders, onDismiss = { moveSheet = false })

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
