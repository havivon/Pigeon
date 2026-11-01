package com.havivon.pigeon.data

import android.content.Context
import android.util.Log
import com.havivon.pigeon.PigeonApp
import com.havivon.pigeon.data.contacts.AndroidContactsRepository
import com.havivon.pigeon.data.db.BinDatabase
import com.havivon.pigeon.data.db.PigeonDatabase
import com.havivon.pigeon.data.db.RoomBinRepository
import com.havivon.pigeon.data.db.RoomBackupStore
import com.havivon.pigeon.data.db.RoomBlockedKeywordsRepository
import com.havivon.pigeon.data.db.RoomFavoritesRepository
import com.havivon.pigeon.data.db.RoomFoldersRepository
import com.havivon.pigeon.data.db.RoomLocksRepository
import com.havivon.pigeon.data.db.RoomPinsRepository
import com.havivon.pigeon.data.db.RoomRemindersRepository
import com.havivon.pigeon.data.db.RoomRulesRepository
import com.havivon.pigeon.data.db.RoomSpamRepository
import com.havivon.pigeon.data.notify.MessageNotifier
import com.havivon.pigeon.data.reminders.ReminderNotifier
import com.havivon.pigeon.data.settings.DataStoreSettingsRepository
import com.havivon.pigeon.data.sms.IncomingSmsHandler
import com.havivon.pigeon.data.sms.PendingSends
import com.havivon.pigeon.data.sms.ProviderBinRestorer
import com.havivon.pigeon.data.sms.ProviderSmsStore
import com.havivon.pigeon.data.sms.TelephonyMessageRepository
import com.havivon.pigeon.data.sms.TelephonySmsSender
import com.havivon.pigeon.widget.WidgetRefresher
import com.havivon.pigeon.widget.refreshSignals
import com.havivon.pigeon.domain.backup.SettingsBackupManager
import com.havivon.pigeon.domain.analysis.SpamOnRead
import com.havivon.pigeon.domain.bin.RecycleBin
import com.havivon.pigeon.domain.repository.BlockedKeywordsRepository
import com.havivon.pigeon.domain.repository.ContactsRepository
import com.havivon.pigeon.domain.repository.FavoritesRepository
import com.havivon.pigeon.domain.repository.FoldersRepository
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.PinsRepository
import com.havivon.pigeon.domain.repository.RemindersRepository
import com.havivon.pigeon.domain.repository.RulesRepository
import com.havivon.pigeon.domain.repository.SettingsRepository
import com.havivon.pigeon.domain.repository.SmsSender
import com.havivon.pigeon.domain.repository.SpamRepository
import com.havivon.pigeon.domain.seam.CodeFinder
import com.havivon.pigeon.domain.seam.FolderClassifier
import com.havivon.pigeon.domain.seam.IncomingMessageHook
import com.havivon.pigeon.domain.seam.ReminderScheduler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Every repository and service of the app, built once per process (by [PigeonApp]) and shared by
 * the screens, receivers and workers. Plain constructor wiring, no DI framework.
 *
 * The analysis plugs in through the seams ([CodeFinder], [IncomingMessageHook], [FolderClassifier],
 * [ReminderScheduler]): pass the real implementations here. The defaults do nothing (no code,
 * notify everything, every conversation personal, reminders never fire), so the SMS platform works
 * on its own. The hook is built from the container, since it needs the repositories.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppContainer(
    context: Context,
    val codeFinder: CodeFinder = CodeFinder.None,
    val folderClassifier: FolderClassifier = FolderClassifier.AllPersonal,
    incomingHook: (AppContainer) -> IncomingMessageHook = { IncomingMessageHook.NotifyAll },
    val reminderScheduler: ReminderScheduler = ReminderScheduler.None,
) {
    private val app = context.applicationContext

    /** For work that must finish after a screen closes (a pending delete, a send from respond-via-message). */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w("Pigeon", "Background work failed: ${e.javaClass.name}") },
    )

    private val database: PigeonDatabase by lazy { PigeonDatabase.build(app) }

    val settings: SettingsRepository by lazy { DataStoreSettingsRepository(app) }
    val contacts: ContactsRepository by lazy { AndroidContactsRepository(app) }
    val rules: RulesRepository by lazy { RoomRulesRepository(database) }
    val favorites: FavoritesRepository by lazy { RoomFavoritesRepository(database) }
    val reminders: RemindersRepository by lazy { RoomRemindersRepository(database) }
    val spam: SpamRepository by lazy { RoomSpamRepository(database) }
    val locks: LocksRepository by lazy { RoomLocksRepository(database) }
    val blockedKeywords: BlockedKeywordsRepository by lazy { RoomBlockedKeywordsRepository(database) }
    val pins: PinsRepository by lazy { RoomPinsRepository(database) }
    /** The user's categories and the conversations moved by hand ("Move to category"). */
    val folders: FoldersRepository by lazy { RoomFoldersRepository(database) }

    /** Export and import of the settings file (Settings, Backup). */
    val backup: SettingsBackupManager by lazy {
        SettingsBackupManager(
            settings, rules, folders, pins, locks, spam, blockedKeywords, favorites, reminders, messages,
            reminderScheduler, RoomBackupStore(database),
            applyIcon = { AppIconSwitcher.set(app, it) },
        )
    }

    /** The recycle bin: a database file of its own, left out of backups (it holds message content). */
    private val binDatabase: BinDatabase by lazy { BinDatabase.build(app) }
    val recycleBin: RecycleBin by lazy { RecycleBin(RoomBinRepository(binDatabase)) }
    val binRestorer: ProviderBinRestorer by lazy { ProviderBinRestorer(app) }

    /** Messages sent as a viewer, until Android has stored them. */
    val pendingSends = PendingSends()

    val messages: MessageRepository by lazy { TelephonyMessageRepository(app, contacts, locks, pins, appScope, pendingSends, ::canWrite, refresh = accessChanges, bin = recycleBin) }
    val smsSender: SmsSender by lazy { TelephonySmsSender(app, messages, pendingSends, ::canWrite) }
    val notifier: MessageNotifier by lazy {
        MessageNotifier(app, messages, contacts, locks, settings, codeFinder, isOnScreen = { it == visibleThreadId })
    }
    /** Keeps the home-screen widgets current: redraws them when anything they show changes. */
    val widgets: WidgetRefresher by lazy {
        WidgetRefresher(
            app, appScope,
            refreshSignals(accessChanges.flatMapLatest { messages.conversations }, favorites.favorites, pins.pinnedThreadIds, locks.lockedThreadIds, settings.settings, rules.rules, spam.verdicts),
        )
    }
    val reminderNotifier: ReminderNotifier by lazy {
        ReminderNotifier(app, reminders, messages, contacts, locks, settings, reminderScheduler)
    }
    private val hook: IncomingMessageHook by lazy { incomingHook(this) }
    val incomingSms: IncomingSmsHandler by lazy { IncomingSmsHandler(ProviderSmsStore(app), hook, notifier) }

    /**
     * Spam for the messages already on the phone: a viewer's (they never pass through [incomingSms]), and,
     * when the rules change ([com.havivon.pigeon.domain.analysis.SpamScorer.VERSION]), everyone's. The
     * screens call it with every new list; what was judged is remembered, so it is judged once.
     */
    val spamOnRead: SpamOnRead by lazy {
        SpamOnRead(hook, SpamOnReadMemory(app)) { threadId ->
            // Someone the user wrote to is never spam: no need to judge at all
            messages.recentMessages(threadId, SPAM_ON_READ_DEPTH).let { list -> if (list.any { it.type.outgoing }) null else list.lastOrNull() }
        }
    }

    /**
     * The conversation open on the screen right now (the app in the foreground), or null. Its new
     * messages are not notified: the open conversation shows them and marks them read.
     */
    @Volatile
    var visibleThreadId: Long? = null

    private val _access = MutableStateFlow(readAppAccess(app))

    /** What the app may do now: default app, viewer or neither, and the permissions around it. Kept current by [refreshAccess]. */
    val access: StateFlow<AppAccess> = _access.asStateFlow()

    private val _accessChanges = MutableStateFlow(0)

    /**
     * Counts changes in [access] (default SMS app, the SMS and contacts permissions). The system
     * does not report a newly granted permission to content observers, so the screens re-read
     * their data whenever this changes.
     */
    val accessChanges: StateFlow<Int> = _accessChanges.asStateFlow()

    /** Reads [access] again (the app came back, a request returned); a change makes every screen re-read. */
    fun refreshAccess(): AppAccess {
        val now = readAppAccess(app)
        if (now != _access.value) {
            _access.value = now
            _accessChanges.update { it + 1 }
        }
        return now
    }

    /**
     * Whether Pigeon may change Android's SMS database right now: only as the default SMS app.
     * Asked live, since another app can take the role at any moment.
     */
    fun canWrite(): Boolean = DefaultSmsRole.isDefault(app)

    companion object {
        /** How far back a conversation is read for a message the user wrote, when judging spam on read. */
        private const val SPAM_ON_READ_DEPTH = 50

        fun from(context: Context): AppContainer = (context.applicationContext as PigeonApp).container
    }
}
