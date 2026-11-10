package com.havivon.pigeon.fakes

import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.AppTheme
import com.havivon.pigeon.domain.model.BlockedKeyword
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.Contact
import com.havivon.pigeon.domain.model.Favorite
import com.havivon.pigeon.domain.model.PendingDeletion
import com.havivon.pigeon.domain.model.Reminder
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SearchResults
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.SpamVerdict
import com.havivon.pigeon.domain.repository.BlockedKeywordsRepository
import com.havivon.pigeon.domain.repository.ContactsRepository
import com.havivon.pigeon.domain.repository.FavoritesRepository
import com.havivon.pigeon.domain.repository.FoldersRepository
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.folders.FolderMoves
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.PinsRepository
import com.havivon.pigeon.domain.repository.RemindersRepository
import com.havivon.pigeon.domain.repository.RulesRepository
import com.havivon.pigeon.domain.repository.SettingsRepository
import com.havivon.pigeon.domain.repository.SmsSender
import com.havivon.pigeon.domain.repository.SpamRepository
import com.havivon.pigeon.domain.seam.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

// In-memory stand-ins for the repositories, for the ViewModel and seam tests. No real messages.

fun sms(
    id: Long,
    threadId: Long,
    body: String,
    dateMillis: Long,
    address: String = "0525550100",
    type: SmsType = SmsType.Inbox,
    read: Boolean = true,
) = SmsMessage(id, threadId, address, body, dateMillis, 0, type, read, subId = null)

fun conversation(
    threadId: Long,
    address: String,
    name: String? = null,
    snippet: String = "",
    dateMillis: Long = 0,
    unread: Int = 0,
) = SmsConversation(threadId, listOf(address), name, snippet, dateMillis, unread, hasFailed = false)

class FakeMessageRepository : MessageRepository {
    val threads = MutableStateFlow<List<SmsConversation>>(emptyList())
    val allMessages = MutableStateFlow<List<SmsMessage>>(emptyList())
    private val hiddenMessages = MutableStateFlow<Set<Long>>(emptySet())
    private val hiddenThreads = MutableStateFlow<Set<Long>>(emptySet())
    val markedRead = mutableListOf<Long>()
    var searchResults: SearchResults = SearchResults.Empty
    val searched = mutableListOf<String>()
    var nextThreadId = 900L

    /**
     * As the real repository: a conversation whose messages are in [allMessages] counts its unread ones from them
     * (their ids are what the Unread list reads); one without messages keeps the count it was given.
     */
    override val conversations: Flow<List<SmsConversation>> =
        combine(threads, hiddenThreads, allMessages, hiddenMessages) { list, hidden, all, hiddenIds ->
            val byThread = all.groupBy { it.threadId }
            list.filter { it.threadId !in hidden }.map { c ->
                val own = byThread[c.threadId] ?: return@map c
                val unread = own.filter { !it.type.outgoing && !it.read && it.id !in hiddenIds }
                    .sortedWith(compareByDescending<SmsMessage> { it.dateMillis }.thenByDescending { it.id })
                    .map { it.id }
                c.copy(unreadIds = unread, unreadCount = unread.size)
            }
        }

    override fun messages(threadId: Long, limit: Int): Flow<List<SmsMessage>> =
        combine(allMessages, hiddenMessages, hiddenThreads) { list, hidden, ht ->
            if (threadId in ht) emptyList() else list.filter { it.threadId == threadId && it.id !in hidden }.sortedBy { it.dateMillis }.takeLast(limit)
        }

    override suspend fun message(id: Long): SmsMessage? = allMessages.value.firstOrNull { it.id == id }
    override suspend fun recentMessages(threadId: Long, limit: Int) = allMessages.value.filter { it.threadId == threadId }.takeLast(limit)
    override suspend fun recentIncoming(limit: Int) =
        allMessages.value.filter { !it.type.outgoing }.sortedByDescending { it.dateMillis }.take(limit)
    override suspend fun unreadMessages(threadId: Long) = allMessages.value.filter { it.threadId == threadId && !it.read }
    val readById = mutableListOf<Collection<Long>>()

    override suspend fun messagesById(ids: Collection<Long>): List<SmsMessage> {
        readById += ids
        val wanted = ids.toSet()
        return allMessages.value.filter { it.id in wanted && it.id !in hiddenMessages.value && it.threadId !in hiddenThreads.value }
    }
    override suspend fun threadIdFor(addresses: List<String>): Long =
        threads.value.firstOrNull { c -> c.addresses.size == addresses.size && c.addresses.all { a -> addresses.any { PhoneNumbers.same(a, it) } } }?.threadId
            ?: nextThreadId

    override suspend fun markThreadRead(threadId: Long) {
        markedRead += threadId
        allMessages.update { list -> list.map { if (it.threadId == threadId) it.copy(read = true) else it } }
        threads.update { list -> list.map { if (it.threadId == threadId) it.copy(unreadCount = 0) else it } }
    }

    /** Each batched "mark all as read" call, with the conversations it named. */
    val markedReadBatches = mutableListOf<List<Long>>()

    override suspend fun markThreadsRead(threadIds: Collection<Long>) {
        markedReadBatches += threadIds.toList()
        val ids = threadIds.toSet()
        allMessages.update { list -> list.map { if (it.threadId in ids && !it.type.outgoing) it.copy(read = true) else it } }
        threads.update { list -> list.map { if (it.threadId in ids) it.copy(unreadCount = 0) else it } }
    }

    override fun deleteMessages(ids: Set<Long>): PendingDeletion {
        hiddenMessages.update { it + ids }
        return Undo { hiddenMessages.update { it - ids } }
    }

    override fun deleteThreads(threadIds: Set<Long>): PendingDeletion {
        hiddenThreads.update { it + threadIds }
        return Undo { hiddenThreads.update { it - threadIds } }
    }

    override suspend fun search(query: String): SearchResults {
        searched += query
        return searchResults
    }

    val browsed = mutableListOf<Long?>()

    override suspend fun browse(threadId: Long?, limit: Int): List<SmsMessage> {
        browsed += threadId
        return allMessages.value
            .filter { (threadId == null || it.threadId == threadId) && it.id !in hiddenMessages.value && it.threadId !in hiddenThreads.value }
            .sortedByDescending { it.dateMillis }
            .take(limit)
    }

    private class Undo(private val restore: () -> Unit) : PendingDeletion {
        private var done = false
        override fun undo(): Boolean {
            if (done) return false
            done = true
            restore()
            return true
        }
    }
}

class FakeSmsSender(private val messages: FakeMessageRepository? = null) : SmsSender {
    val sent = mutableListOf<Pair<List<String>, String>>()
    val retried = mutableListOf<Long>()
    var nextId = 5000L
    var fail = false

    override suspend fun send(recipients: List<String>, body: String, subId: Int?): List<Long> {
        sent += recipients to body
        if (fail) return emptyList()
        return recipients.map { address ->
            val id = nextId++
            val threadId = messages?.threadIdFor(listOf(address)) ?: 1L
            messages?.allMessages?.update { it + sms(id, threadId, body, 4_000_000_000_000L + id, address, SmsType.Outbox) }
            id
        }
    }

    override suspend fun retry(messageId: Long): Boolean {
        retried += messageId
        return true
    }
}

class FakeContacts(private val all: List<Contact> = emptyList()) : ContactsRepository {
    override val contacts: Flow<List<Contact>> = MutableStateFlow(all)
    override suspend fun displayName(address: String): String? = all.firstOrNull { c -> c.numbers.any { PhoneNumbers.same(it, address) } }?.name
    override suspend fun search(query: String): List<Contact> =
        all.filter { c -> c.name.contains(query, ignoreCase = true) || c.numbers.any { PhoneNumbers.matchesQuery(it, query) } }
}

class FakeRules(initial: List<ColorRule> = emptyList()) : RulesRepository {
    val state = MutableStateFlow(initial)
    override val rules: Flow<List<ColorRule>> = state
    override suspend fun add(label: String, match: RuleMatch, argb: Long, enabled: Boolean): Int {
        val id = (state.value.maxOfOrNull { it.id } ?: 0) + 1
        state.update { it + ColorRule(id, label, match, argb, enabled) }
        return id
    }
    override suspend fun update(rule: ColorRule) = state.update { list -> list.map { if (it.id == rule.id) rule else it } }
    override suspend fun delete(id: Int) = state.update { list -> list.filter { it.id != id } }
    val reordered = mutableListOf<List<Int>>()
    override suspend fun reorder(idsInOrder: List<Int>) {
        reordered += idsInOrder
        state.update { list -> idsInOrder.mapNotNull { id -> list.firstOrNull { it.id == id } } + list.filter { it.id !in idsInOrder } }
    }
}

class FakeFavorites : FavoritesRepository {
    val state = MutableStateFlow<List<Favorite>>(emptyList())
    override val favorites: Flow<List<Favorite>> = state
    override val favoriteMessageIds: Flow<Set<Long>> = state.map { list -> list.map { it.messageId }.toSet() }
    override suspend fun star(messageId: Long, threadId: Long, address: String, senderName: String?, body: String, dateMillis: Long) {
        state.update { list -> list.filter { it.messageId != messageId } + Favorite(messageId, messageId, threadId, address, senderName, body, dateMillis, 0) }
    }
    override suspend fun unstar(messageId: Long) = state.update { list -> list.filter { it.messageId != messageId } }
}

class FakeReminders : RemindersRepository {
    val state = MutableStateFlow<List<Reminder>>(emptyList())
    override val pending: Flow<List<Reminder>> = state.map { list -> list.filter { !it.done } }
    override suspend fun add(threadId: Long, messageId: Long?, text: String, triggerAtMillis: Long): Long {
        val id = (state.value.maxOfOrNull { it.id } ?: 0) + 1
        state.update { it + Reminder(id, threadId, messageId, text, triggerAtMillis, done = false) }
        return id
    }
    override suspend fun get(id: Long): Reminder? = state.value.firstOrNull { it.id == id }
    override suspend fun setDone(id: Long, done: Boolean) = state.update { list -> list.map { if (it.id == id) it.copy(done = done) else it } }
    override suspend fun delete(id: Long) = state.update { list -> list.filter { it.id != id } }
    override suspend fun allPending(): List<Reminder> = state.value.filter { !it.done }
}

class FakeScheduler : ReminderScheduler {
    val scheduled = mutableListOf<Reminder>()
    val cancelled = mutableListOf<Long>()
    override fun schedule(reminder: Reminder) {
        scheduled += reminder
    }
    override fun cancel(reminderId: Long) {
        cancelled += reminderId
    }
}

class FakeSpam : SpamRepository {
    val verdictState = MutableStateFlow<List<SpamVerdict>>(emptyList())
    val notSpamState = MutableStateFlow<Set<String>>(emptySet())
    override val verdicts: Flow<List<SpamVerdict>> = verdictState
    override val notSpam: Flow<Set<String>> = notSpamState
    override suspend fun verdictFor(address: String) = verdictState.value.firstOrNull { it.address == PhoneNumbers.normalize(address) }
    override suspend fun markSpam(address: String, threadId: Long, reasons: List<SpamReason>): Boolean {
        val key = PhoneNumbers.normalize(address)
        if (notSpamState.value.any { PhoneNumbers.same(it, key) }) return false
        verdictState.update { list -> list.filter { it.address != key } + SpamVerdict(key, threadId, reasons, 0) }
        return true
    }
    override suspend fun markNotSpam(address: String) {
        val key = PhoneNumbers.normalize(address)
        verdictState.update { list -> list.filter { !PhoneNumbers.same(it.address, key) } }
        notSpamState.update { it + key }
    }
    override suspend fun isNotSpam(address: String) = notSpamState.value.any { PhoneNumbers.same(it, address) }
    override suspend fun removeFromNotSpam(address: String) = notSpamState.update { set -> set.filterNot { PhoneNumbers.same(it, address) }.toSet() }
}

class FakeLocks : LocksRepository {
    val state = MutableStateFlow<Set<Long>>(emptySet())
    override val lockedThreadIds: Flow<Set<Long>> = state
    override suspend fun isLocked(threadId: Long) = threadId in state.value
    override suspend fun lockedNow() = state.value
    override suspend fun lock(threadId: Long) = state.update { it + threadId }
    override suspend fun unlock(threadId: Long) = state.update { it - threadId }
}

class FakePins : PinsRepository {
    val state = MutableStateFlow<Set<Long>>(emptySet())
    override val pinnedThreadIds: Flow<Set<Long>> = state
    override suspend fun pin(threadId: Long) = state.update { it + threadId }
    override suspend fun unpin(threadId: Long) = state.update { it - threadId }
}

class FakeBlockedKeywords(words: List<String> = emptyList()) : BlockedKeywordsRepository {
    val state = MutableStateFlow(words.mapIndexed { i, w -> BlockedKeyword(i.toLong(), w) })
    override val keywords: Flow<List<BlockedKeyword>> = state
    override suspend fun add(word: String): Boolean {
        val trimmed = word.trim()
        if (trimmed.isEmpty() || state.value.any { it.word.equals(trimmed, ignoreCase = true) }) return false
        state.update { it + BlockedKeyword(it.size.toLong(), trimmed) }
        return true
    }
    override suspend fun delete(id: Long) = state.update { list -> list.filter { it.id != id } }
}

class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun setLanguage(value: AppLanguage) = state.update { it.copy(language = value) }
    override suspend fun setTheme(value: AppTheme) = state.update { it.copy(theme = value) }
    override suspend fun setAppIcon(value: AppIcon) = state.update { it.copy(appIcon = value) }
    override suspend fun setLockScreenText(value: Boolean) = state.update { it.copy(lockScreenText = value) }
    override suspend fun setWidgetsShowText(value: Boolean) = state.update { it.copy(widgetsShowText = value) }
    override suspend fun setBlockScreenshots(value: Boolean) = state.update { it.copy(blockScreenshots = value) }
    override suspend fun setAppLock(value: Boolean) = state.update { it.copy(appLock = value) }
    override suspend fun setAutoDeleteCodes(value: Boolean) = state.update { it.copy(autoDeleteCodes = value) }
    override suspend fun setAutoDeleteCodesSince(millis: Long?) = state.update { it.copy(autoDeleteCodesSinceMillis = millis) }
    override suspend fun setWelcomeDone(value: Boolean) = state.update { it.copy(welcomeDone = value) }
    override suspend fun setPermissionsAsked(value: Boolean) = state.update { it.copy(permissionsAsked = value) }
    override suspend fun setBlockSpamOnPhone(value: Boolean) = state.update { it.copy(blockSpamOnPhone = value) }
    override suspend fun setAutoCopyCodes(value: Boolean) = state.update { it.copy(autoCopyCodes = value) }
    override suspend fun restoreBackedUp(values: AppSettings) = state.update { values.copy(welcomeDone = it.welcomeDone, permissionsAsked = it.permissionsAsked) }
}

/** The user's categories and moves by hand, in memory, as the Room repository keeps them. */
class FakeFolders(initial: List<CustomFolder> = emptyList()) : FoldersRepository {
    val folderState = MutableStateFlow(initial)
    val overrideState = MutableStateFlow<Map<String, FolderChoice>>(emptyMap())
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1
    override val folders: Flow<List<CustomFolder>> = folderState
    val builtInColorState = MutableStateFlow<Map<Folder, Long>>(emptyMap())
    override val builtInColors: Flow<Map<Folder, Long>> = builtInColorState
    override val overrides: Flow<Map<String, FolderChoice>> = overrideState
    override suspend fun add(name: String, argb: Long?): Long {
        val id = nextId++
        folderState.update { it + CustomFolder(id, name, argb) }
        return id
    }
    override suspend fun setColor(choice: FolderChoice, argb: Long?) {
        when (choice) {
            is FolderChoice.Custom -> folderState.update { list -> list.map { if (it.id == choice.id) it.copy(argb = argb) else it } }
            is FolderChoice.BuiltIn -> if (choice.folder.colourable) {
                builtInColorState.update { if (argb == null) it - choice.folder else it + (choice.folder to argb) }
            }
        }
    }
    override suspend fun rename(id: Long, name: String) = folderState.update { list -> list.map { if (it.id == id) it.copy(name = name) else it } }
    override suspend fun delete(id: Long) {
        overrideState.update { map -> map.filterValues { it != FolderChoice.Custom(id) } }
        folderState.update { list -> list.filter { it.id != id } }
    }
    override suspend fun reorder(idsInOrder: List<Long>) = folderState.update { list ->
        val listed = idsInOrder.mapNotNull { id -> list.firstOrNull { it.id == id } }.distinct()
        listed + list.filter { it !in listed }
    }
    override suspend fun overrideFor(key: String): FolderChoice? = overrideState.value[key]
    override suspend fun setOverride(key: String, choice: FolderChoice) {
        if (choice == FolderChoice.BuiltIn(Folder.All) || choice == FolderChoice.BuiltIn(Folder.Spam)) return
        overrideState.update { it + (key to choice) }
    }
    override suspend fun clearOverride(key: String) = overrideState.update { it - key }
}

/** The built-in folders' names in English and Hebrew, and the "All" filter, as `reservedFolderNames` reads them from the resources. */
val ReservedFolderNames = setOf(
    "All categories", "Personal", "Banks", "Insurance", "Codes", "Deliveries", "Spam", "All",
    "כל הקטגוריות", "אישי", "בנקים", "ביטוח", "קודים", "משלוחים", "ספאם", "הכל",
)

fun testMoves(folders: FoldersRepository, spam: com.havivon.pigeon.domain.repository.SpamRepository) = FolderMoves(folders, spam, ReservedFolderNames)
