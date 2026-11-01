package com.havivon.pigeon.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.AppIconSwitcher
import com.havivon.pigeon.data.DefaultSmsRole
import com.havivon.pigeon.data.backup.BackupFiles
import com.havivon.pigeon.data.reminders.AlarmReminderScheduler
import com.havivon.pigeon.ui.app.LocalAuthenticator
import com.havivon.pigeon.ui.app.LocalDefaultApp
import com.havivon.pigeon.ui.app.LocalNotices
import com.havivon.pigeon.ui.app.rememberNeedsDefaultNotice
import com.havivon.pigeon.ui.navigation.Screen
import com.havivon.pigeon.ui.navigation.pigeonViewModel
import java.time.LocalDate

/** A permission request that returns this fast showed no dialog: the system suppresses it after a refusal, so the app's settings are the only way. */
private const val SUPPRESSED_DIALOG_MS = 400L

/** The settings screen on real data. [navigate] opens the rules and blocked-words screens. */
@Composable
fun SettingsRoute(onBack: () -> Unit, navigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val container = remember { AppContainer.from(context) }
    val vm = pigeonViewModel { app ->
        val appContext = context.applicationContext
        SettingsViewModel(app.settings, app.rules, app.blockedKeywords, app.folders, AppIconSwitcher.current(appContext)) { AppIconSwitcher.set(appContext, it) }
    }
    val state by vm.state.collectAsStateWithLifecycle()

    // Export and import of the settings file: the system's file picker, so Pigeon needs no storage permission
    val backup = pigeonViewModel { app -> BackupViewModel(app.backup) }
    val pendingImport by backup.pending.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val exportRequest = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) backup.export(versionName(context)) { json -> BackupFiles.write(context, uri, json) }
    }
    val importRequest = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backup.load { BackupFiles.read(context, uri) }
    }
    LaunchedEffect(backup) {
        backup.eventFlow.collect { e ->
            notices.show(backupNotice(context, e))
            // The icon is switched behind the picker's back: show the chosen one
            if (e is BackupEvent.Imported) vm.iconChanged(AppIconSwitcher.current(context.applicationContext))
        }
    }
    val auth = LocalAuthenticator.current
    val defaultApp = LocalDefaultApp.current
    val needsDefault = rememberNeedsDefaultNotice()

    // What the phone allows right now; read again whenever the user comes back from a system screen
    var phone by remember { mutableStateOf(readPhone(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) phone = readPhone(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var requestedAt by remember { mutableLongStateOf(0L) }
    fun afterRequest(granted: Boolean) {
        phone = readPhone(context)
        if (granted) {
            container.refreshAccess()
        } else if (SystemClock.elapsedRealtime() - requestedAt < SUPPRESSED_DIALOG_MS) {
            // Refused for good: only the app's settings can allow it now
            open(context, appDetails(context))
        }
    }
    val contactsRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), ::afterRequest)
    val notificationsRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), ::afterRequest)

    val env = SettingsEnvironment(
        canLock = auth.available(),
        contactsAllowed = phone.contacts,
        notificationsAllowed = phone.notifications,
        exactAlarmsAllowed = phone.exactAlarms,
        smsAccess = defaultApp.mode,
        versionName = versionName(context),
    )

    pendingImport?.let { file ->
        ImportConfirmDialog(
            summary = file.summary(),
            // Importing can change the app lock and the locked chats, so it asks for the fingerprint or screen lock first
            onConfirm = { auth.authenticate { ok -> if (ok) backup.confirmImport(canLock = auth.available()) } },
            onDismiss = backup::cancelImport,
        )
    }

    SettingsScreen(
        state = state,
        env = env,
        actions = SettingsActions(
            onBack = onBack,
            onLanguage = vm::setLanguage,
            onTheme = vm::setTheme,
            onAppIcon = vm::setAppIcon,
            onOpenRules = { navigate(Screen.Rules) },
            onOpenKeywords = { navigate(Screen.BlockedKeywords) },
            onOpenFolders = { navigate(Screen.Folders) },
            onOpenRecycleBin = { navigate(Screen.RecycleBin) },
            onExportSettings = { exportRequest.launch("pigeon-settings-${LocalDate.now()}.json") },
            onImportSettings = { importRequest.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            // Deleting needs the default SMS app; turning it off always works
            onAutoDeleteCodes = { on -> if (on && !defaultApp.isDefault) needsDefault() else vm.setAutoDeleteCodes(on) },
            onLockScreenText = vm::setLockScreenText,
            onSystemNotifications = {
                open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            },
            onAllowExactAlarms = {
                if (Build.VERSION.SDK_INT >= 31) {
                    open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
            },
            onBlockScreenshots = vm::setBlockScreenshots,
            // Turning the lock on or off proves first that the fingerprint or screen lock works
            onAppLock = { on -> auth.authenticate { ok -> if (ok) vm.setAppLock(on) } },
            onContactsPermission = {
                if (phone.contacts) {
                    open(context, appDetails(context))
                } else {
                    requestedAt = SystemClock.elapsedRealtime()
                    contactsRequest.launch(Manifest.permission.READ_CONTACTS)
                }
            },
            onNotificationsPermission = {
                when {
                    phone.notifications -> open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    Build.VERSION.SDK_INT >= 33 && !granted(context, Manifest.permission.POST_NOTIFICATIONS) -> {
                        requestedAt = SystemClock.elapsedRealtime()
                        notificationsRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    // Allowed by the permission but switched off in the system's notification settings
                    else -> open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            },
            onWidgetsShowText = vm::setWidgetsShowText,
            onDefaultApp = {
                if (defaultApp.isDefault) open(context, DefaultSmsRole.settingsIntent()) else defaultApp.makeDefault()
            },
            onViewOnly = defaultApp.viewOnly,
        ),
    )
}

private data class Phone(val contacts: Boolean, val notifications: Boolean, val exactAlarms: Boolean?)

private fun readPhone(context: Context) = Phone(
    contacts = granted(context, Manifest.permission.READ_CONTACTS),
    // Below Android 13 there is no permission, only the switch in the system's notification settings
    notifications = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)),
    exactAlarms = if (Build.VERSION.SDK_INT >= 31) AlarmReminderScheduler.canScheduleExact(context) else null,
)

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A phone without that settings page: nothing more to offer
    }
}

@Suppress("DEPRECATION")
private fun versionName(context: Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }
