package com.havivon.pigeon

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.havivon.pigeon.data.AppIconSwitcher
import com.havivon.pigeon.data.DefaultSmsRole
import com.havivon.pigeon.data.sample.SampleData
import com.havivon.pigeon.data.sample.SampleSet
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.Message
import com.havivon.pigeon.domain.model.ThreadItem
import com.havivon.pigeon.ui.conversation.ConversationScreen
import com.havivon.pigeon.ui.inbox.InboxScreen
import com.havivon.pigeon.ui.onboarding.DefaultAppReminder
import com.havivon.pigeon.ui.onboarding.DefaultAppWelcome
import com.havivon.pigeon.ui.rules.RulesScreen
import com.havivon.pigeon.ui.spam.SpamScreen
import com.havivon.pigeon.ui.theme.HebSmsTheme

/** Design preview: the real screens on sample data. SMS access comes in the next stage. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HebSmsTheme { DefaultAppGate { PreviewApp() } } }
    }
}

private const val PREFS = "onboarding"
private const val WELCOME_DONE = "welcome_done"

/** A result this fast means the system showed no dialog at all (it suppresses its request after a refusal). */
private const val SUPPRESSED_DIALOG_MS = 400L

/**
 * Asks the user to make Pigeon the default SMS app: the welcome screen instead of the app on the
 * first launch, a reminder card over it on every later open. "Not now" hides the prompt until the
 * user comes back to the app. Never shows before [DefaultSmsRole.SMS_HANDLING_READY].
 */
@Composable
private fun DefaultAppGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var welcomeDone by remember { mutableStateOf(prefs.getBoolean(WELCOME_DONE, false)) }
    var notDefault by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    var requestedAt by remember { mutableLongStateOf(0L) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                hidden = false
                notDefault = DefaultSmsRole.SMS_HANDLING_READY && !DefaultSmsRole.isDefault(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun notNow() {
        prefs.edit().putBoolean(WELCOME_DONE, true).apply()
        welcomeDone = true
        hidden = true
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        welcomeDone = true
        when {
            DefaultSmsRole.isDefault(context) -> notDefault = false
            SystemClock.elapsedRealtime() - requestedAt < SUPPRESSED_DIALOG_MS -> openDefaultAppSettings(context)
            else -> hidden = true
        }
    }

    fun makeDefault() {
        // The flag is saved now, but the screen only changes once the system's answer is back
        prefs.edit().putBoolean(WELCOME_DONE, true).apply()
        requestedAt = SystemClock.elapsedRealtime()
        launcher.launch(DefaultSmsRole.requestIntent(context) ?: DefaultSmsRole.settingsIntent())
    }

    val showPrompt = notDefault && !hidden
    if (showPrompt && !welcomeDone) {
        DefaultAppWelcome(onMakeDefault = ::makeDefault, onNotNow = ::notNow)
    } else {
        content()
        if (showPrompt) DefaultAppReminder(onMakeDefault = ::makeDefault, onNotNow = ::notNow)
    }
}

private fun openDefaultAppSettings(context: Context) {
    try {
        context.startActivity(DefaultSmsRole.settingsIntent())
    } catch (_: ActivityNotFoundException) {
        // A phone without that screen: nothing more to offer
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
    val sample = SampleData.forLanguage(LocalConfiguration.current.locales[0].language)
    var screen by remember { mutableStateOf<Screen>(Screen.Inbox) }
    val context = LocalContext.current
    var appIcon by remember { mutableStateOf(AppIconSwitcher.current(context)) }
    BackHandler(enabled = screen != Screen.Inbox) { screen = Screen.Inbox }
    when (val s = screen) {
        Screen.Inbox -> InboxScreen(
            conversations = sample.inbox,
            rules = sample.rules,
            folderCounts = sample.folderCounts,
            dateLine = sample.dateLine,
            onFolder = { if (it == Folder.Spam) screen = Screen.Spam },
            onOpen = { screen = Screen.Thread(it.id) },
            onSettings = { screen = Screen.Rules },
        )
        is Screen.Thread -> ThreadPreview(sample, s.id, onBack = { screen = Screen.Inbox })
        Screen.Spam -> SpamScreen(sample.spam, onBack = { screen = Screen.Inbox })
        Screen.Rules -> RulesScreen(
            sample.rules,
            sample.newRuleWord,
            appIcon = appIcon,
            onAppIcon = {
                AppIconSwitcher.set(context, it)
                appIcon = it
            },
            onBack = { screen = Screen.Inbox },
        )
    }
}

@Composable
private fun ThreadPreview(sample: SampleSet, id: Int, onBack: () -> Unit) {
    val conversation = sample.inbox.first { it.id == id }
    val items = sample.dannyThread
    val messages = items.filterIsInstance<ThreadItem.Msg>().map { it.message }
    var textSelectionId by remember { mutableStateOf<Int?>(null) }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var draft by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val me = stringResource(R.string.thread_sender_me)

    fun joined(): String = messages.filter { it.id in selected }.joinToString("\n") { m ->
        "[${m.time}] ${if (m.outgoing) me else conversation.name}: ${m.text}"
    }

    BackHandler(enabled = selected.isNotEmpty() || textSelectionId != null) {
        selected = emptySet()
        textSelectionId = null
    }
    ConversationScreen(
        conversation = conversation,
        items = items,
        rule = sample.rules.firstOrNull { it.id == conversation.ruleId },
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
