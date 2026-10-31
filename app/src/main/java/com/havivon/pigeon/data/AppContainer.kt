package com.havivon.pigeon.data

import android.content.Context
import android.util.Log
import com.havivon.pigeon.PigeonApp
import com.havivon.pigeon.data.contacts.AndroidContactsRepository
import com.havivon.pigeon.data.db.PigeonDatabase
import com.havivon.pigeon.data.db.RoomBlockedKeywordsRepository
import com.havivon.pigeon.data.db.RoomFavoritesRepository
import com.havivon.pigeon.data.db.RoomLocksRepository
import com.havivon.pigeon.data.db.RoomRemindersRepository
import com.havivon.pigeon.data.db.RoomRulesRepository
import com.havivon.pigeon.data.db.RoomSpamRepository
import com.havivon.pigeon.data.notify.MessageNotifier
import com.havivon.pigeon.data.settings.DataStoreSettingsRepository
import com.havivon.pigeon.data.sms.IncomingSmsHandler
import com.havivon.pigeon.data.sms.TelephonyMessageRepository
import com.havivon.pigeon.data.sms.TelephonySmsSender
import com.havivon.pigeon.domain.repository.BlockedKeywordsRepository
import com.havivon.pigeon.domain.repository.ContactsRepository
import com.havivon.pigeon.domain.repository.FavoritesRepository
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.RemindersRepository
import com.havivon.pigeon.domain.repository.RulesRepository
import com.havivon.pigeon.domain.repository.SettingsRepository
import com.havivon.pigeon.domain.repository.SmsSender
import com.havivon.pigeon.domain.repository.SpamRepository
import com.havivon.pigeon.domain.seam.CodeFinder
import com.havivon.pigeon.domain.seam.FolderClassifier
import com.havivon.pigeon.domain.seam.IncomingMessageHook
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Every repository and service of the app, built once per process (by [PigeonApp]) and shared by
 * the screens, receivers and workers. Plain constructor wiring, no DI framework.
 *
 * The analysis plugs in through the seams ([CodeFinder], [IncomingMessageHook], [FolderClassifier]):
 * pass the real implementations here. The defaults do nothing (no code, notify everything, every
 * conversation personal), so the SMS platform works on its own.
 */
class AppContainer(
    context: Context,
    val codeFinder: CodeFinder = CodeFinder.None,
    val folderClassifier: FolderClassifier = FolderClassifier.AllPersonal,
    incomingHook: IncomingMessageHook = IncomingMessageHook.NotifyAll,
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

    val messages: MessageRepository by lazy { TelephonyMessageRepository(app, contacts, locks, appScope) }
    val smsSender: SmsSender by lazy { TelephonySmsSender(app, messages) }
    val notifier: MessageNotifier by lazy { MessageNotifier(app, messages, contacts, locks, settings, codeFinder) }
    val incomingSms: IncomingSmsHandler by lazy { IncomingSmsHandler(app, incomingHook, notifier) }

    companion object {
        fun from(context: Context): AppContainer = (context.applicationContext as PigeonApp).container
    }
}
