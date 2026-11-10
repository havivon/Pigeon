package com.havivon.pigeon.ui.app

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.havivon.pigeon.R
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.DefaultSmsRole
import com.havivon.pigeon.data.LaunchRequests
import com.havivon.pigeon.data.viewerPermissions
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.SmsAccess
import com.havivon.pigeon.ui.compose.NewMessageRoute
import com.havivon.pigeon.ui.conversation.ConversationRoute
import com.havivon.pigeon.ui.inbox.InboxRoute
import com.havivon.pigeon.ui.bin.BinRoute
import com.havivon.pigeon.ui.reminders.RemindersRoute
import com.havivon.pigeon.ui.folders.FoldersRoute
import com.havivon.pigeon.ui.keywords.BlockedKeywordsRoute
import com.havivon.pigeon.ui.lock.BiometricAuthenticator
import com.havivon.pigeon.ui.lock.LockCover
import com.havivon.pigeon.ui.navigation.Navigator
import com.havivon.pigeon.ui.navigation.Screen
import com.havivon.pigeon.ui.navigation.ScreenHost
import com.havivon.pigeon.ui.onboarding.DefaultAppReminder
import com.havivon.pigeon.ui.onboarding.RestrictedSettingsHelp
import com.havivon.pigeon.ui.onboarding.DefaultAppWelcome
import com.havivon.pigeon.ui.onboarding.PermissionsExplainer
import com.havivon.pigeon.ui.rules.RulesRoute
import com.havivon.pigeon.ui.blocked.BlockedNumbersRoute
import com.havivon.pigeon.ui.blocked.PhoneUnblockOffer
import com.havivon.pigeon.ui.search.SearchRoute
import com.havivon.pigeon.ui.settings.SettingsRoute
import com.havivon.pigeon.ui.spam.SpamRoute
import com.havivon.pigeon.ui.theme.HebTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A result this fast means the system showed no dialog at all (it suppresses its request after a refusal). */
private const val SUPPRESSED_DIALOG_MS = 1000L

/** How long after the app comes back the copied code's clearing is tried (see [com.havivon.pigeon.domain.codes.CodeAutoCopy.clearIfDue]). */
private const val CLIPBOARD_FOCUS_DELAY_MS = 700L

/** Away for less than this (a share, the dialer, the browser), the app does not lock again. */
private const val LOCK_GRACE_MS = 10_000L

/** What the lock cover is waiting for. */
private enum class Cover { App, Thread }

/**
 * The whole app around the screens: the default-app welcome and reminder, the permission
 * explainer, app lock and locked chats, screenshot blocking, the undo snackbar, and the
 * navigation between the screens.
 */
@Composable
fun PigeonShell(navigator: Navigator, authenticator: BiometricAuthenticator) {
    val context = LocalContext.current
    val container = remember { AppContainer.from(context) }
    val settings by container.settings.settings.collectAsState(initial = null)
    val locked by container.locks.lockedThreadIds.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.common_undo)
    val notices = remember(undoLabel) { Notices(snackbar, scope, undoLabel) }

    val topThread = (navigator.top.screen as? Screen.Thread)?.threadId
    val topLocked = topThread != null && topThread in locked

    // Screenshots and the recent-apps preview: off when the setting says so, and always over a locked chat
    val secure = settings?.blockScreenshots == true || topLocked
    DisposableEffect(secure) {
        val window = (context as? Activity)?.window
        if (secure) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { }
    }

    // With app lock on, the recent-apps preview must not show what the lock covers. Android 13+
    // can hide just the preview, so screenshots inside the app still work.
    val appLock = settings?.appLock == true
    DisposableEffect(appLock) {
        if (Build.VERSION.SDK_INT >= 33) (context as? Activity)?.setRecentsScreenshotEnabled(!appLock)
        onDispose { }
    }

    // A copied code that is due to be cleared: Android lets Pigeon look at the clipboard only while it is on screen
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    // The window takes the focus a moment after the resume, and the clipboard needs it
                    delay(CLIPBOARD_FOCUS_DELAY_MS)
                    container.codeAutoCopy.clearIfDue()
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalNotices provides notices, LocalAuthenticator provides authenticator) {
        Box(Modifier.fillMaxSize()) {
            DefaultAppGate(settings) {
                Screens(navigator, container, authenticator)
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(start = 12.dp, end = 12.dp, bottom = 76.dp),
            ) { data ->
                val c = HebTheme.colors
                Snackbar(data, containerColor = c.onSurface, contentColor = c.surface, actionColor = if (c.isDark) c.background else c.surfaceVariant)
            }
            LockGate(settings, navigator, authenticator, topLocked, locked)
            PhoneUnblockOffer(container.phoneSpam, notices)
        }
    }
}

@Composable
private fun Screens(navigator: Navigator, container: AppContainer, authenticator: BiometricAuthenticator) {
    val scope = rememberCoroutineScope()

    /** Opens a conversation, after the fingerprint or screen lock when it is locked. */
    fun openThread(threadId: Long, replaceTop: Boolean = false, messageId: Long? = null) {
        scope.launch {
            val go = {
                val screen = Screen.Thread(threadId, messageId)
                if (replaceTop) navigator.replaceTop(screen) else navigator.push(screen)
            }
            if (container.locks.isLocked(threadId)) authenticator.authenticate { ok -> if (ok) go() } else go()
        }
    }

    // What the activity was opened for: a notification's conversation, or a draft from another app
    val openRequest by LaunchRequests.openThread.collectAsState()
    LaunchedEffect(openRequest) {
        val id = openRequest ?: return@LaunchedEffect
        LaunchRequests.consumeOpenThread()
        navigator.popToInbox()
        openThread(id)
    }
    val composeRequest by LaunchRequests.compose.collectAsState()
    LaunchedEffect(composeRequest) {
        val draft = composeRequest ?: return@LaunchedEffect
        LaunchRequests.consumeCompose()
        navigator.push(Screen.NewMessage(draft.recipients, draft.body))
    }

    ScreenHost(navigator) { screen ->
        when (screen) {
            Screen.Inbox -> InboxRoute(
                navigate = navigator::push,
                openThread = { openThread(it) },
                openMessage = { threadId, messageId -> openThread(threadId, messageId = messageId) },
            )
            is Screen.Thread -> ConversationRoute(screen.threadId, screen.messageId, onBack = { navigator.pop() }, onClosed = { navigator.pop() })
            Screen.Spam -> SpamRoute(onBack = { navigator.pop() }, openThread = { threadId, messageId -> openThread(threadId, messageId = messageId) })
            Screen.Search -> SearchRoute(
                onBack = { navigator.pop() },
                openThread = { openThread(it) },
                openMessage = { threadId, messageId -> openThread(threadId, messageId = messageId) },
            )
            is Screen.NewMessage -> NewMessageRoute(
                screen.recipients,
                screen.body,
                onBack = { navigator.pop() },
                openThreadInstead = { openThread(it, replaceTop = true) },
            )
            Screen.Settings -> SettingsRoute(onBack = { navigator.pop() }, navigate = navigator::push)
            Screen.Rules -> RulesRoute(onBack = { navigator.pop() })
            Screen.BlockedKeywords -> BlockedKeywordsRoute(onBack = { navigator.pop() })
            Screen.Folders -> FoldersRoute(onBack = { navigator.pop() })
            Screen.BlockedNumbers -> BlockedNumbersRoute(onBack = { navigator.pop() })
            Screen.Reminders -> RemindersRoute(onBack = { navigator.pop() }, openThread = { threadId, messageId -> openThread(threadId, messageId = messageId) })
            Screen.RecycleBin -> BinRoute(onBack = { navigator.pop() })
        }
    }
}

/**
 * App lock and locked chats. With app lock on, the app is covered on start and whenever it comes
 * back after [LOCK_GRACE_MS]; a locked chat that was open is covered the same way. Unlocking takes
 * the fingerprint or screen lock; giving up on a locked chat closes it.
 */
@Composable
private fun LockGate(settings: AppSettings?, navigator: Navigator, authenticator: BiometricAuthenticator, topLocked: Boolean, locked: Set<Long>) {
    var cover by rememberSaveable { mutableStateOf<Cover?>(null) }
    var startChecked by rememberSaveable { mutableStateOf(false) }
    var stoppedAt by rememberSaveable { mutableLongStateOf(0L) }
    val appLock = settings?.appLock == true

    LaunchedEffect(settings != null) {
        if (settings != null && !startChecked) {
            startChecked = true
            if (settings.appLock) cover = Cover.App
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentAppLock by rememberUpdatedState(appLock)
    val currentTopLocked by rememberUpdatedState(topLocked)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // The PIN screen of the prompt stops the activity too: that is not leaving the app
                Lifecycle.Event.ON_STOP -> if (!authenticator.prompting) stoppedAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_START -> {
                    val away = stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > LOCK_GRACE_MS
                    stoppedAt = 0L
                    if (away && cover == null) {
                        authenticator.expire()
                        cover = when {
                            currentAppLock -> Cover.App
                            currentTopLocked -> Cover.Thread
                            else -> null
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun unlock() {
        authenticator.authenticate { ok ->
            when {
                ok -> cover = null
                cover == Cover.Thread -> {
                    navigator.removeAll { it is Screen.Thread && it.threadId in locked }
                    cover = null
                }
            }
        }
    }

    val shown = cover ?: return
    LockCover(onUnlock = ::unlock)
    LaunchedEffect(shown) { unlock() }
}

/** Which request Android's restricted settings blocked, so it runs again once the user has allowed it. */
private enum class Blocked { Role, Viewer }

/**
 * How Pigeon gets to the messages. While it can see none, it offers the two ways: becoming the
 * default SMS app, or just viewing (READ_SMS and the permissions around it, while another app stays
 * the default). The welcome screen comes instead of the app on the first launch, a reminder card
 * over it on every later open; "Not now" hides it until the user comes back to the app. A viewer is
 * reminded by a slim banner at the top of the inbox instead, which can be closed for the session.
 * Once Pigeon is the default, it explains and asks (once) for notifications and contacts; the app
 * works without them. Android's restricted settings (an app installed from a file) block both
 * requests: the help explains how to allow them, and the request runs again by itself after.
 */
@Composable
private fun DefaultAppGate(settings: AppSettings?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val container = remember { AppContainer.from(context) }
    // Read once from the settings (null for the few milliseconds that takes), then kept here
    var welcomeDone by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(settings != null) { if (welcomeDone == null && settings != null) welcomeDone = settings.welcomeDone }
    val scope = rememberCoroutineScope()
    val access by container.access.collectAsState()
    var hidden by remember { mutableStateOf(false) }
    var requestedAt by remember { mutableLongStateOf(0L) }
    var explainerHidden by remember { mutableStateOf(false) }
    var bannerHidden by rememberSaveable { mutableStateOf(false) }
    var restricted by remember { mutableStateOf<Blocked?>(null) }
    var restrictionLifted by remember { mutableStateOf<Blocked?>(null) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                hidden = false
                container.refreshAccess()
                val blocked = restricted
                // Only when the system says it is allowed now; when it won't say, the help stays with its retry
                if (blocked != null && DefaultSmsRole.isKnownAllowed(context)) {
                    restricted = null
                    restrictionLifted = blocked
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun saveWelcomeDone() {
        scope.launch { container.settings.setWelcomeDone(true) }
    }

    fun notNow() {
        saveWelcomeDone()
        welcomeDone = true
        hidden = true
    }

    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        welcomeDone = true
        val now = container.refreshAccess()
        when {
            now.sms == SmsAccess.Default -> Unit
            DefaultSmsRole.mayBeRestrictedByAndroid(context) -> restricted = Blocked.Role
            // An answer faster than anyone can decide: Android did not ask at all. Either restricted
            // settings blocked it (whatever the install source says now: an adb update keeps the
            // block) or an earlier refusal stops the dialog; app info is the way out of both
            SystemClock.elapsedRealtime() - requestedAt < SUPPRESSED_DIALOG_MS ->
                if (Build.VERSION.SDK_INT >= 33) restricted = Blocked.Role else openDefaultAppSettings(context)
            else -> hidden = true
        }
    }

    fun makeDefault() {
        // The flag is saved now, but the screen only changes once the system's answer is back
        saveWelcomeDone()
        if (DefaultSmsRole.isRestrictedByAndroid(context)) {
            // The system would only answer "denied": explain how to allow it instead
            restricted = Blocked.Role
            return
        }
        requestedAt = SystemClock.elapsedRealtime()
        try {
            roleRequest.launch(DefaultSmsRole.requestIntent(context) ?: DefaultSmsRole.settingsIntent())
        } catch (_: ActivityNotFoundException) {
            openDefaultAppSettings(context)
        }
    }

    val viewerRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        welcomeDone = true
        // Notifications and contacts were part of it: the explainer does not ask again later
        scope.launch { container.settings.setPermissionsAsked(true) }
        val now = container.refreshAccess()
        when {
            now.sms != SmsAccess.None -> Unit
            DefaultSmsRole.mayBeRestrictedByAndroid(context) -> restricted = Blocked.Viewer
            // Not asked at all (restricted settings, or refused for good earlier): the help leads to app info
            SystemClock.elapsedRealtime() - requestedAt < SUPPRESSED_DIALOG_MS ->
                if (Build.VERSION.SDK_INT >= 33) restricted = Blocked.Viewer else openAppInfo(context)
            else -> hidden = true
        }
    }

    fun viewOnly() {
        saveWelcomeDone()
        if (DefaultSmsRole.isRestrictedByAndroid(context)) {
            restricted = Blocked.Viewer
            return
        }
        requestedAt = SystemClock.elapsedRealtime()
        viewerRequest.launch(viewerPermissions())
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { container.settings.setPermissionsAsked(true) }
        container.refreshAccess()
    }

    LaunchedEffect(restrictionLifted) {
        when (restrictionLifted) {
            Blocked.Role -> makeDefault()
            Blocked.Viewer -> viewOnly()
            null -> Unit
        }
        restrictionLifted = null
    }

    val mode = if (DefaultSmsRole.SMS_HANDLING_READY) access.sms else SmsAccess.Default
    val showPrompt = mode == SmsAccess.None && !hidden
    val gate = DefaultAppAccess(
        mode = mode,
        makeDefault = ::makeDefault,
        viewOnly = ::viewOnly,
        showViewerBanner = mode == SmsAccess.Viewer && !bannerHidden,
        dismissViewerBanner = { bannerHidden = true },
    )
    CompositionLocalProvider(LocalDefaultApp provides gate) {
        if (showPrompt && welcomeDone == null) {
            // Not known yet whether this is the first launch: show nothing rather than the wrong screen
        } else if (showPrompt && welcomeDone == false) {
            DefaultAppWelcome(onMakeDefault = ::makeDefault, onViewOnly = ::viewOnly, onNotNow = ::notNow)
        } else {
            content()
            if (showPrompt && restricted == null) DefaultAppReminder(onMakeDefault = ::makeDefault, onViewOnly = ::viewOnly, onNotNow = ::notNow)
        }
        // Still needed: the role while Pigeon is not the default, reading while it sees nothing
        val blocked = restricted
        if (blocked != null && (if (blocked == Blocked.Role) mode != SmsAccess.Default else mode == SmsAccess.None)) {
            RestrictedSettingsHelp(
                onOpenAppInfo = { openAppInfo(context) },
                onRetry = {
                    restricted = null
                    restrictionLifted = blocked
                },
                onClose = { restricted = null },
            )
        }
    }

    // After becoming the default: notifications (Android 13+) and contacts, explained first, asked once
    val askNotifications = !access.notifications
    val askContacts = !access.contacts
    if (mode == SmsAccess.Default && settings != null && !settings.permissionsAsked && !explainerHidden && (askNotifications || askContacts)) {
        PermissionsExplainer(
            askNotifications = askNotifications,
            askContacts = askContacts,
            onContinue = {
                explainerHidden = true
                permissions.launch(
                    listOfNotNull(
                        if (askNotifications && Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null,
                        if (askContacts) Manifest.permission.READ_CONTACTS else null,
                    ).toTypedArray(),
                )
            },
            onNotNow = {
                explainerHidden = true
                scope.launch { container.settings.setPermissionsAsked(true) }
            },
        )
    }
}

private fun openDefaultAppSettings(context: Context) {
    try {
        context.startActivity(DefaultSmsRole.settingsIntent())
    } catch (_: ActivityNotFoundException) {
        // A phone without that screen: nothing more to offer
    }
}

/** This app's info screen: "Allow restricted settings" and the permissions live there. */
private fun openAppInfo(context: Context) {
    try {
        context.startActivity(DefaultSmsRole.appInfoIntent(context))
    } catch (_: ActivityNotFoundException) {
        openDefaultAppSettings(context)
    }
}
