package com.havivon.pigeon.domain.backup

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.folders.FolderKeys
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.repository.BlockedKeywordsRepository
import com.havivon.pigeon.domain.repository.FavoritesRepository
import com.havivon.pigeon.domain.repository.FoldersRepository
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.PinsRepository
import com.havivon.pigeon.domain.repository.RemindersRepository
import com.havivon.pigeon.domain.repository.RulesRepository
import com.havivon.pigeon.domain.repository.SettingsRepository
import com.havivon.pigeon.domain.repository.SpamRepository
import com.havivon.pigeon.domain.seam.ReminderScheduler
import kotlinx.coroutines.flow.first

/** What an import did, for the notice that follows it. "Total" is what the file held, the other number what this phone could use. */
data class RestoreReport(
    val pinsFound: Int,
    val pinsTotal: Int,
    val locksFound: Int,
    val locksTotal: Int,
    val remindersRestored: Int,
    val remindersTotal: Int,
    val favoritesFound: Int,
    val favoritesTotal: Int,
    /** The file turned app lock on, but the phone has no screen lock: it stays as it was. */
    val appLockKeptOff: Boolean,
    /** Pigeon could not read the phone's messages, so pins, locks, reminders and favorites were left as they were. */
    val messagesUnavailable: Boolean,
)

/**
 * Export and import of the user's settings (see [SettingsBackup] for what is in the file, and that no
 * message text ever is).
 *
 * Export reads only the app's own data and the conversation list (to name a pinned or locked chat by its
 * sender instead of by this phone's thread id), so it never asks for the fingerprint: a locked chat is
 * exported as "locked", its content is not touched.
 *
 * Import replaces what the file covers, in one database transaction ([BackupStore]) and then one DataStore
 * write. Things that point at messages are matched on this phone by chat ([ChatKeys]):
 * - Pins, locked chats and the moves of group chats apply to the conversations found; those not found are skipped.
 * - Reminders keep their time. One made on a message is attached to it when the message is found by its date; if
 *   not, it becomes a reminder on the conversation. A reminder whose conversation is not on the phone, or whose time
 *   has passed, is skipped. The reminder text is rebuilt from the phone's own message (empty for a locked chat).
 * - Favorites are references only, so a starred message is brought back only when the same message (date and text
 *   hash) is on the phone; the others are skipped. The report says how many were found ("12 of 14").
 * - When the phone's messages cannot be read at all (no permission), none of the four is touched.
 */
class SettingsBackupManager(
    private val settings: SettingsRepository,
    private val rules: RulesRepository,
    private val folders: FoldersRepository,
    private val pins: PinsRepository,
    private val locks: LocksRepository,
    private val spam: SpamRepository,
    private val keywords: BlockedKeywordsRepository,
    private val favorites: FavoritesRepository,
    private val reminders: RemindersRepository,
    private val messages: MessageRepository,
    private val scheduler: ReminderScheduler,
    private val store: BackupStore,
    /** Switches the launcher icon (`AppIconSwitcher`). */
    private val applyIcon: (AppIcon) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun export(appVersion: String): SettingsBackup {
        val s = settings.settings.first()
        val chatOf = messages.conversations.first()
            .mapNotNull { c -> ChatKeys.of(c.addresses)?.let { c.threadId to it } }
            .toMap()
        val categories = folders.folders.first()

        val moves = folders.overrides.first().mapNotNull { (key, choice) ->
            val chat = FolderKeys.threadOf(key)?.let { chatOf[it] } ?: key.takeUnless { it.startsWith("thread:") } ?: return@mapNotNull null
            when (choice) {
                is FolderChoice.BuiltIn -> BackupMove(chat, choice.folder, null)
                is FolderChoice.Custom -> categories.firstOrNull { it.id == choice.id }?.let { BackupMove(chat, null, it.name) }
            }
        }.sortedBy { it.key }

        val backupReminders = reminders.allPending().mapNotNull { r ->
            val chat = chatOf[r.threadId] ?: return@mapNotNull null
            BackupReminder(r.triggerAtMillis, chat, r.messageId?.let { messages.message(it)?.dateMillis })
        }

        val backupFavorites = favorites.favorites.first().mapNotNull { f ->
            val chat = chatOf[f.threadId] ?: ChatKeys.of(listOf(f.address)) ?: return@mapNotNull null
            BackupFavorite(chat, f.dateMillis, BackupHash.of(f.body), f.starredAtMillis.takeIf { it > 0 })
        }

        return SettingsBackup(
            exportedAtMillis = now(),
            appVersion = appVersion,
            settings = BackupSettings(
                language = s.language,
                theme = s.theme,
                appIcon = s.appIcon,
                lockScreenText = s.lockScreenText,
                widgetsShowText = s.widgetsShowText,
                blockScreenshots = s.blockScreenshots,
                appLock = s.appLock,
                autoDeleteCodes = s.autoDeleteCodes,
            ),
            rules = rules.rules.first().map { BackupRule(it.label, it.match, it.argb, it.enabled) },
            categories = categories.map { it.name },
            moves = moves,
            pinned = pins.pinnedThreadIds.first().mapNotNull { chatOf[it] }.distinct().sorted(),
            locked = locks.lockedThreadIds.first().mapNotNull { chatOf[it] }.distinct().sorted(),
            notSpam = spam.notSpam.first().sorted(),
            blockedWords = keywords.keywords.first().map { it.word },
            reminders = backupReminders,
            favorites = backupFavorites,
        )
    }

    /** Replaces the user's settings and data with the file's. [canLock]: the phone has a screen lock or biometrics. */
    suspend fun restore(backup: SettingsBackup, canLock: Boolean): RestoreReport {
        val current = settings.settings.first()
        val conversations = messages.conversations.first()
        val canResolve = conversations.isNotEmpty()
        val byChat: Map<String, List<SmsConversation>> = conversations
            .mapNotNull { c -> ChatKeys.of(c.addresses)?.let { it to c } }
            .groupBy({ it.first }, { it.second })

        fun threadsOf(chats: List<String>): Set<Long> = chats.flatMap { chat -> byChat[chat].orEmpty().map { it.threadId } }.toSet()
        val pinned = threadsOf(backup.pinned)
        val locked = threadsOf(backup.locked)

        // Messages are read once per conversation, and only when a reminder or a favorite needs them
        val read = HashMap<Long, List<SmsMessage>>()
        suspend fun messagesOf(threadId: Long): List<SmsMessage> = read.getOrPut(threadId) { messages.messages(threadId).first() }

        val plannedReminders = ArrayList<PlannedReminder>()
        for (r in backup.reminders) {
            if (r.triggerAtMillis <= now()) continue
            val convs = byChat[r.chat] ?: continue
            val hit = r.messageDateMillis?.let { date -> convs.firstNotNullOfOrNull { c -> messagesOf(c.threadId).firstOrNull { it.dateMillis == date } } }
            val conv = convs.firstOrNull { it.threadId == hit?.threadId } ?: convs.first()
            // Text is the phone's own copy of the message; a locked chat keeps none in a reminder
            val text = when {
                conv.threadId in locked -> ""
                hit != null -> hit.body.take(REMINDER_TEXT_LENGTH)
                else -> conv.snippet.take(REMINDER_TEXT_LENGTH)
            }
            plannedReminders += PlannedReminder(conv.threadId, hit?.id, text, r.triggerAtMillis)
        }

        val plannedFavorites = ArrayList<PlannedFavorite>()
        val starred = HashSet<Long>()
        for (f in backup.favorites) {
            for (c in byChat[f.chat].orEmpty()) {
                val m = messagesOf(c.threadId).firstOrNull { it.dateMillis == f.dateMillis && BackupHash.of(it.body) == f.bodyHash } ?: continue
                if (starred.add(m.id)) {
                    plannedFavorites += PlannedFavorite(m.id, c.threadId, m.address, c.contactName, m.body, m.dateMillis, f.starredAtMillis ?: now())
                }
                break
            }
        }

        val moves = backup.moves.flatMap { m ->
            val keys = if (m.key.startsWith(ChatKeys.GROUP)) byChat[m.key].orEmpty().map { FolderKeys.forThread(it.threadId) } else listOf(m.key)
            val index = m.category?.let { backup.categories.indexOf(it) }
            if (m.category != null && index == -1) emptyList() else keys.map { PlannedMove(it, m.folder, index) }
        }

        val plan = RestorePlan(
            rules = backup.rules.map { ColorRule(0, it.label, it.match, it.argb, it.enabled) },
            categories = backup.categories,
            moves = moves,
            pinnedThreadIds = pinned.takeIf { canResolve },
            lockedThreadIds = locked.takeIf { canResolve },
            notSpam = backup.notSpam.map { PhoneNumbers.normalize(it) }.filter { it.isNotEmpty() }.distinct(),
            blockedWords = backup.blockedWords,
            reminders = plannedReminders.takeIf { canResolve },
            favorites = plannedFavorites.takeIf { canResolve },
        )

        val oldPending = if (plan.reminders != null) reminders.allPending() else emptyList()
        val inserted = store.replace(plan)
        oldPending.forEach { scheduler.cancel(it.id) }
        inserted.forEach { scheduler.schedule(it) }

        val lockKeptOff = backup.settings.appLock == true && !current.appLock && !canLock
        settings.restoreBackedUp(merged(current, backup.settings, lockKeptOff))
        backup.settings.appIcon?.let(applyIcon)

        return RestoreReport(
            pinsFound = backup.pinned.count { it in byChat }, pinsTotal = backup.pinned.size,
            locksFound = backup.locked.count { it in byChat }, locksTotal = backup.locked.size,
            remindersRestored = if (canResolve) plannedReminders.size else 0, remindersTotal = backup.reminders.size,
            favoritesFound = if (canResolve) plannedFavorites.size else 0, favoritesTotal = backup.favorites.size,
            appLockKeptOff = lockKeptOff,
            messagesUnavailable = !canResolve && (backup.pinned.isNotEmpty() || backup.locked.isNotEmpty() || backup.reminders.isNotEmpty() || backup.favorites.isNotEmpty()),
        )
    }

    /** The current settings with the file's values over them; the welcome and permission flags are never in a file. */
    private fun merged(current: AppSettings, file: BackupSettings, lockKeptOff: Boolean): AppSettings {
        val autoDelete = file.autoDeleteCodes ?: current.autoDeleteCodes
        return current.copy(
            language = file.language ?: current.language,
            theme = file.theme ?: current.theme,
            appIcon = file.appIcon ?: current.appIcon,
            lockScreenText = file.lockScreenText ?: current.lockScreenText,
            widgetsShowText = file.widgetsShowText ?: current.widgetsShowText,
            blockScreenshots = file.blockScreenshots ?: current.blockScreenshots,
            appLock = if (lockKeptOff) current.appLock else file.appLock ?: current.appLock,
            autoDeleteCodes = autoDelete,
            // As the switch does it: counting starts when the feature is turned on, so existing history is never wiped
            autoDeleteCodesSinceMillis = when {
                !autoDelete -> null
                current.autoDeleteCodes -> current.autoDeleteCodesSinceMillis
                else -> now()
            },
        )
    }

    private companion object {
        /** As a reminder made in a conversation keeps its text. */
        const val REMINDER_TEXT_LENGTH = 200
    }
}
