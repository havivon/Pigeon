package com.hebsms.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.hebsms.app.data.sample.SampleData
import com.hebsms.app.domain.model.Folder
import com.hebsms.app.domain.model.Message
import com.hebsms.app.domain.model.ThreadItem
import com.hebsms.app.ui.conversation.ConversationScreen
import com.hebsms.app.ui.inbox.InboxScreen
import com.hebsms.app.ui.rules.RulesScreen
import com.hebsms.app.ui.spam.SpamScreen
import com.hebsms.app.ui.theme.HebSmsTheme

/** Design preview: the real screens on sample data. SMS access comes in the next stage. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HebSmsTheme { PreviewApp() } }
    }
}

private sealed interface Screen {
    data object Inbox : Screen
    data class Thread(val id: Int) : Screen
    data object Spam : Screen
    data object Rules : Screen
}

@Composable
private fun PreviewApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Inbox) }
    BackHandler(enabled = screen != Screen.Inbox) { screen = Screen.Inbox }
    when (val s = screen) {
        Screen.Inbox -> InboxScreen(
            conversations = SampleData.inbox,
            rules = SampleData.rules,
            folderCounts = SampleData.folderCounts,
            onFolder = { if (it == Folder.Spam) screen = Screen.Spam },
            onOpen = { screen = Screen.Thread(it.id) },
            onSettings = { screen = Screen.Rules },
        )
        is Screen.Thread -> ThreadPreview(s.id, onBack = { screen = Screen.Inbox })
        Screen.Spam -> SpamScreen(SampleData.spam, onBack = { screen = Screen.Inbox })
        Screen.Rules -> RulesScreen(SampleData.rules, onBack = { screen = Screen.Inbox })
    }
}

@Composable
private fun ThreadPreview(id: Int, onBack: () -> Unit) {
    val conversation = SampleData.inbox.first { it.id == id }
    val items = SampleData.dannyThread
    val messages = items.filterIsInstance<ThreadItem.Msg>().map { it.message }
    var textSelectionId by remember { mutableStateOf<Int?>(null) }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var draft by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    fun joined(): String = messages.filter { it.id in selected }.joinToString("\n") { m ->
        "[${m.time}] ${if (m.outgoing) "אני" else conversation.name}: ${m.text}"
    }

    BackHandler(enabled = selected.isNotEmpty() || textSelectionId != null) {
        selected = emptySet()
        textSelectionId = null
    }
    ConversationScreen(
        conversation = conversation,
        items = items,
        rule = SampleData.rules.firstOrNull { it.id == conversation.ruleId },
        draft = draft,
        textSelectionId = textSelectionId,
        selectedIds = selected,
        onBack = onBack,
        onMessageTap = { m: Message ->
            if (selected.isNotEmpty()) {
                selected = if (m.id in selected) selected - m.id else selected + m.id
            } else {
                textSelectionId = if (textSelectionId == m.id) null else m.id
            }
        },
        onMessageLongPress = { m ->
            textSelectionId = null
            selected = selected + m.id
        },
        onClearSelection = { selected = emptySet() },
        onSelectAll = { selected = messages.map { it.id }.toSet() },
        onCopySelected = {
            clipboard.setText(AnnotatedString(joined()))
            selected = emptySet()
        },
        onShareSelected = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, joined())
            context.startActivity(Intent.createChooser(send, null))
        },
        onDraft = { draft = it },
    )
}
