package com.havivon.pigeon.domain.backup

import com.havivon.pigeon.data.backup.BackupJson
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.domain.folders.FolderKeys
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.AppTheme
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SmsConversation
import com.havivon.pigeon.fakes.FakeBackupStore
import com.havivon.pigeon.fakes.FakeBlockedKeywords
import com.havivon.pigeon.fakes.FakeFavorites
import com.havivon.pigeon.fakes.FakeFolders
import com.havivon.pigeon.fakes.FakeLocks
import com.havivon.pigeon.fakes.FakeMessageRepository
import com.havivon.pigeon.fakes.FakePins
import com.havivon.pigeon.fakes.FakeReminders
import com.havivon.pigeon.fakes.FakeRules
import com.havivon.pigeon.fakes.FakeScheduler
import com.havivon.pigeon.fakes.FakeSettings
import com.havivon.pigeon.fakes.FakeSpam
import com.havivon.pigeon.fakes.Now
import com.havivon.pigeon.fakes.conversation
import com.havivon.pigeon.fakes.sms
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HOUR = 3_600_000L

private const val DINNER = "Dinner at eight, the door code is 5521 SECRET-ONE"
private const val PARCEL = "Bring the parcel from the post office SECRET-TWO"
private const val BALANCE = "Your balance is 1234 NIS SECRET-THREE"
private const val GROUP_TEXT = "See you all on Friday SECRET-FOUR"

/**
 * A phone with made-up conversations and the app's own data in fakes. [shift] moves every thread and message id,
 * so a second phone shows that a file is matched by sender and date, not by this phone's ids.
 */
private class Phone(val shift: Long = 0) {
    val dana = 1 + shift
    val bank = 2 + shift
    val group = 3 + shift

    val messages = FakeMessageRepository()
    val settings = FakeSettings(AppSettings(welcomeDone = true, permissionsAsked = true))
    val rules = FakeRules()
    val folders = FakeFolders()
    val pins = FakePins()
    val locks = FakeLocks()
    val spam = FakeSpam()
    val keywords = FakeBlockedKeywords()
    val favorites = FakeFavorites()
    val reminders = FakeReminders()
    val scheduler = FakeScheduler()
    val store = FakeBackupStore(rules, folders, pins, locks, spam, keywords, favorites, reminders)
    var icon: AppIcon? = null

    val manager = SettingsBackupManager(
        settings, rules, folders, pins, locks, spam, keywords, favorites, reminders, messages, scheduler, store,
        applyIcon = { icon = it }, now = { Now },
    )

    init {
        messages.threads.value = listOf(
            conversation(dana, "0525550101", "Dana", snippet = PARCEL, dateMillis = Now - 5 * HOUR),
            conversation(bank, "Leumi", null, snippet = BALANCE, dateMillis = Now - 3 * HOUR),
            SmsConversation(group, listOf("0525550103", "0525550104"), null, GROUP_TEXT, Now - HOUR, 0, hasFailed = false),
        )
        messages.allMessages.value = listOf(
            sms(10 + shift, dana, DINNER, Now - 6 * HOUR, "0525550101"),
            sms(11 + shift, dana, PARCEL, Now - 5 * HOUR, "0525550101"),
            sms(20 + shift, bank, BALANCE, Now - 3 * HOUR, "Leumi"),
            sms(30 + shift, group, GROUP_TEXT, Now - HOUR, "0525550103"),
        )
    }

    /** The user's own set-up, as it is after some use. */
    suspend fun setUp() {
        settings.state.value = AppSettings(
            language = AppLanguage.Hebrew, theme = AppTheme.Dark, skin = "pomegranate", appIcon = AppIcon.Origami, lockScreenText = false,
            widgetsShowText = false, blockScreenshots = true, appLock = false, autoDeleteCodes = true, autoDeleteCodesSinceMillis = Now - 1000,
            welcomeDone = true, permissionsAsked = true, blockSpamOnPhone = false, autoCopyCodes = false,
        )
        rules.add("Bank", RuleMatch.Sender("Leumi"), 0xFFE53935L, true)
        rules.add("Parcels", RuleMatch.Keyword("parcel"), 0xFF1E88E5L, false)
        val work = folders.add("Work")
        val family = folders.add("Family")
        // Colours: one category and one built-in folder have one, the rest none
        folders.setColor(FolderChoice.Custom(work), 0xFF2F80EDL)
        folders.setColor(FolderChoice.BuiltIn(Folder.Banks), 0xFF1E9E6AL)
        folders.setOverride(FolderKeys.of(dana, listOf("0525550101")), FolderChoice.Custom(work))
        folders.setOverride(FolderKeys.of(bank, listOf("Leumi")), FolderChoice.BuiltIn(Folder.Banks))
        folders.setOverride(FolderKeys.of(group, listOf("0525550103", "0525550104")), FolderChoice.Custom(family))
        pins.pin(dana)
        locks.lock(bank)
        spam.markNotSpam("+972525550999")
        keywords.add("casino")
        keywords.add("loan")
        favorites.star(10 + shift, dana, "0525550101", "Dana", DINNER, Now - 6 * HOUR)
        favorites.star(20 + shift, bank, "Leumi", null, BALANCE, Now - 3 * HOUR)
        // A reminder on one message, one on a whole conversation, one in the locked chat (no text there)
        reminders.add(dana, 11 + shift, PARCEL, Now + 2 * HOUR)
        reminders.add(dana, null, PARCEL, Now + 4 * HOUR)
        reminders.add(bank, null, "", Now + 6 * HOUR)
    }

    /** Everything the file covers, in terms that do not depend on this phone's ids. */
    suspend fun appData(): Map<String, Any?> {
        val chatOf = messages.threads.value.associate { it.threadId to ChatKeys.of(it.addresses) }
        val dateOf = messages.allMessages.value.associate { it.id to it.dateMillis }
        val s = settings.state.value
        val categories = folders.folderState.value
        return mapOf(
            "settings" to s.copy(welcomeDone = false, permissionsAsked = false, autoDeleteCodesSinceMillis = null),
            "rules" to rules.state.value.map { listOf(it.label, it.match, it.argb, it.enabled) },
            "categories" to categories.map { it.name },
            "categoryColors" to categories.map { it.name to it.argb },
            "builtInColors" to folders.builtInColorState.value,
            "overrides" to folders.overrideState.value.map { (key, choice) ->
                val chat = FolderKeys.threadOf(key)?.let { chatOf[it] } ?: key
                chat to when (choice) {
                    is FolderChoice.BuiltIn -> choice.folder.name
                    is FolderChoice.Custom -> categories.first { it.id == choice.id }.name
                }
            }.toSet(),
            "pins" to pins.state.value.map { chatOf[it] }.toSet(),
            "locks" to locks.state.value.map { chatOf[it] }.toSet(),
            "notSpam" to spam.notSpamState.value,
            "keywords" to keywords.state.value.map { it.word },
            "favorites" to favorites.state.value.map { listOf(chatOf[it.threadId], dateOf[it.messageId], it.address, it.senderName, it.body, it.dateMillis) }.toSet(),
            "reminders" to reminders.state.value.filter { !it.done }.map { listOf(chatOf[it.threadId], it.messageId?.let(dateOf::get), it.text, it.triggerAtMillis) }.toSet(),
        )
    }

    suspend fun exportJson(): String = BackupJson.encode(manager.export("0.4.5"))
}

private fun parsed(json: String): SettingsBackup = (BackupJson.parse(json) as BackupParse.Ok).backup

class SettingsBackupManagerTest {

    // Round trip

    @Test
    fun `export then import gives identical app data`() = runTest {
        val a = Phone()
        a.setUp()
        val json = a.exportJson()

        val b = Phone()
        b.pins.pin(b.group)
        b.keywords.add("old word")
        b.reminders.add(b.dana, null, "old", Now + 9 * HOUR)
        b.settings.state.value = AppSettings(welcomeDone = true, permissionsAsked = true, theme = AppTheme.Light)
        val report = b.manager.restore(parsed(json), canLock = true)

        assertEquals(a.appData(), b.appData())
        assertEquals(2, report.favoritesFound)
        assertEquals(3, report.remindersRestored)
        assertEquals(1, report.pinsFound)
        assertEquals(1, report.locksFound)
        assertFalse(report.appLockKeptOff)
        assertFalse(report.messagesUnavailable)
    }

    @Test
    fun `the colours of the categories travel with the file, and a version 1 file leaves the built-in ones alone`() = runTest {
        val a = Phone()
        a.setUp()
        val file = parsed(a.exportJson())
        assertEquals(mapOf("Work" to 0xFF2F80EDL), file.categoryColors)
        assertEquals(mapOf(Folder.Banks to 0xFF1E9E6AL), file.folderColors)

        val b = Phone()
        b.folders.setColor(FolderChoice.BuiltIn(Folder.Codes), 0xFFE0559BL)
        b.manager.restore(file, canLock = true)
        assertEquals("a version 2 file replaces the built-in colours", mapOf(Folder.Banks to 0xFF1E9E6AL), b.folders.builtInColorState.value)
        assertEquals(listOf("Work" to 0xFF2F80EDL, "Family" to null), b.folders.folderState.value.map { it.name to it.argb })

        val c = Phone()
        c.folders.setColor(FolderChoice.BuiltIn(Folder.Codes), 0xFFE0559BL)
        c.manager.restore(file.copy(categoryColors = emptyMap(), folderColors = null), canLock = true)
        assertEquals("a version 1 file has none: the phone keeps its own", mapOf(Folder.Codes to 0xFFE0559BL), c.folders.builtInColorState.value)
        assertTrue(c.folders.folderState.value.all { it.argb == null })
    }

    @Test
    fun `a file is matched by sender and date, not by this phone's ids`() = runTest {
        val a = Phone()
        a.setUp()
        val b = Phone(shift = 50)
        b.manager.restore(parsed(a.exportJson()), canLock = true)

        assertEquals(a.appData(), b.appData())
        assertEquals(setOf(b.dana), b.pins.state.value)
        assertEquals(setOf(b.bank), b.locks.state.value)
        assertEquals(FolderChoice.Custom(2), b.folders.overrideState.value[FolderKeys.forThread(b.group)])
        assertEquals(setOf(10L + 50, 20L + 50), b.favorites.state.value.map { it.messageId }.toSet())
    }

    @Test
    fun `welcome and permission flags stay as they were`() = runTest {
        val a = Phone()
        a.setUp()
        val b = Phone()
        b.settings.state.value = AppSettings(welcomeDone = false, permissionsAsked = false)
        b.manager.restore(parsed(a.exportJson()), canLock = true)
        assertFalse(b.settings.state.value.welcomeDone)
        assertFalse(b.settings.state.value.permissionsAsked)
        assertEquals(AppTheme.Dark, b.settings.state.value.theme)
    }

    @Test
    fun `the icon is applied and the chosen language and theme are stored`() = runTest {
        val a = Phone()
        a.setUp()
        val b = Phone()
        b.manager.restore(parsed(a.exportJson()), canLock = true)
        assertEquals(AppIcon.Origami, b.icon)
        assertEquals(AppLanguage.Hebrew, b.settings.state.value.language)
        assertEquals(AppTheme.Dark, b.settings.state.value.theme)
    }

    @Test
    fun `one transaction per import`() = runTest {
        val a = Phone()
        a.setUp()
        val b = Phone()
        b.manager.restore(parsed(a.exportJson()), canLock = true)
        assertEquals(1, b.store.replaced)
    }

    // Privacy

    @Test
    fun `the file holds no message text`() = runTest {
        val a = Phone()
        a.setUp()
        val json = a.exportJson()

        val everyText = a.messages.allMessages.value.map { it.body } + a.favorites.state.value.map { it.body } +
            a.reminders.state.value.map { it.text }.filter { it.isNotEmpty() } + a.messages.threads.value.map { it.snippet }
        for (text in everyText) assertFalse("the file must not contain: $text", json.contains(text))
        for (word in listOf("SECRET-ONE", "SECRET-TWO", "SECRET-THREE", "SECRET-FOUR", "door code", "balance", "parcel from")) {
            assertFalse(word, json.contains(word))
        }
        // It does hold the references it needs
        assertTrue(json.contains(BackupHash.of(DINNER)))
        assertTrue(json.contains("525550101"))
    }

    @Test
    fun `a locked chat is exported without unlocking anything`() = runTest {
        val a = Phone()
        a.setUp()
        val backup = a.manager.export("0.4.5")
        assertEquals(listOf("leumi"), backup.locked)
        assertEquals(setOf(a.bank), a.locks.state.value)
    }

    @Test
    fun `import writes the text from this phone's own messages, and none for a locked chat`() = runTest {
        val a = Phone()
        a.setUp()
        val b = Phone()
        b.manager.restore(parsed(a.exportJson()), canLock = true)
        val byThread = b.reminders.state.value.groupBy { it.threadId }
        assertEquals(setOf(PARCEL), byThread.getValue(b.dana).map { it.text }.toSet())
        assertEquals(listOf(""), byThread.getValue(b.bank).map { it.text })
    }

    // Reminders

    @Test
    fun `pending reminders are scheduled again, the old alarms cancelled`() = runTest {
        val a = Phone()
        a.setUp()
        val b = Phone()
        val old = b.reminders.add(b.dana, null, "old", Now + 9 * HOUR)
        b.manager.restore(parsed(a.exportJson()), canLock = true)

        assertEquals(listOf(old), b.scheduler.cancelled)
        val scheduled = b.scheduler.scheduled
        assertEquals(3, scheduled.size)
        assertEquals(b.reminders.state.value.filter { !it.done }.toSet(), scheduled.toSet())
        assertEquals(setOf(Now + 2 * HOUR, Now + 4 * HOUR, Now + 6 * HOUR), scheduled.map { it.triggerAtMillis }.toSet())
        assertFalse(scheduled.any { it.id == old })
    }

    @Test
    fun `done reminders are left alone and not exported`() = runTest {
        val a = Phone()
        a.setUp()
        val done = a.reminders.add(a.dana, null, "done", Now + HOUR)
        a.reminders.setDone(done)
        assertEquals(3, a.manager.export("x").reminders.size)

        val b = Phone()
        val mine = b.reminders.add(b.dana, null, "finished", Now + HOUR)
        b.reminders.setDone(mine)
        b.manager.restore(parsed(a.exportJson()), canLock = true)
        assertTrue(b.reminders.state.value.any { it.id == mine && it.done })
    }

    @Test
    fun `a reminder whose message is not on the phone becomes a reminder on the conversation`() = runTest {
        val a = Phone()
        a.setUp()
        val b = Phone()
        b.messages.allMessages.value = b.messages.allMessages.value.filter { it.id != 11L }
        b.manager.restore(parsed(a.exportJson()), canLock = true)
        val onDana = b.reminders.state.value.filter { it.threadId == b.dana }
        assertEquals(2, onDana.size)
        assertTrue(onDana.all { it.messageId == null })
    }

    @Test
    fun `a reminder in the past or in a chat not on the phone is skipped and counted`() = runTest {
        val a = Phone()
        a.setUp()
        val backup = a.manager.export("x").let {
            it.copy(
                reminders = it.reminders + BackupReminder(Now - HOUR, "525550101", null) + BackupReminder(Now + HOUR, "525550777", null),
            )
        }
        val b = Phone()
        val report = b.manager.restore(backup, canLock = true)
        assertEquals(3, report.remindersRestored)
        assertEquals(5, report.remindersTotal)
    }

    // Favorites

    @Test
    fun `favorites are brought back only when the same message is on the phone`() = runTest {
        val a = Phone()
        a.setUp()
        val b = Phone()
        // The bank message is not on this phone, and the Dana message was edited
        b.messages.allMessages.value = b.messages.allMessages.value.filter { it.id != 20L }
            .map { if (it.id == 10L) it.copy(body = "something else entirely") else it }
        val report = b.manager.restore(parsed(a.exportJson()), canLock = true)
        assertEquals(0, report.favoritesFound)
        assertEquals(2, report.favoritesTotal)
        assertTrue(b.favorites.state.value.isEmpty())
    }

    @Test
    fun `a partly found list is counted`() = runTest {
        val a = Phone()
        a.setUp()
        val b = Phone()
        b.messages.allMessages.value = b.messages.allMessages.value.filter { it.id != 20L }
        val report = b.manager.restore(parsed(a.exportJson()), canLock = true)
        assertEquals(1, report.favoritesFound)
        assertEquals(2, report.favoritesTotal)
        assertEquals(listOf(DINNER), b.favorites.state.value.map { it.body })
        assertEquals("Dana", b.favorites.state.value.single().senderName)
    }

    // Replace, and what cannot be done

    @Test
    fun `import replaces rules, categories, words and the allow list`() = runTest {
        val a = Phone()
        a.setUp()
        val b = Phone()
        b.rules.add("Old", RuleMatch.Keyword("old"), 0xFF000000L, true)
        b.folders.add("Old category")
        b.spam.markNotSpam("0501111111")
        b.manager.restore(parsed(a.exportJson()), canLock = true)
        assertEquals(listOf("Bank", "Parcels"), b.rules.state.value.map { it.label })
        assertEquals(listOf("Work", "Family"), b.folders.folderState.value.map { it.name })
        assertEquals(setOf(PhoneNumbers.normalize("+972525550999")), b.spam.notSpamState.value)
    }

    @Test
    fun `app lock stays off when the phone has no screen lock`() = runTest {
        val a = Phone()
        a.setUp()
        a.settings.state.value = a.settings.state.value.copy(appLock = true)
        val json = a.exportJson()

        val without = Phone()
        val report = without.manager.restore(parsed(json), canLock = false)
        assertFalse(without.settings.state.value.appLock)
        assertTrue(report.appLockKeptOff)

        val with = Phone()
        val ok = with.manager.restore(parsed(json), canLock = true)
        assertTrue(with.settings.state.value.appLock)
        assertFalse(ok.appLockKeptOff)
    }

    @Test
    fun `turning auto delete on starts the count now, and off forgets it`() = runTest {
        val a = Phone()
        a.setUp()
        val on = a.exportJson()
        val b = Phone()
        b.manager.restore(parsed(on), canLock = true)
        assertTrue(b.settings.state.value.autoDeleteCodes)
        assertEquals(Now, b.settings.state.value.autoDeleteCodesSinceMillis)

        val c = Phone()
        c.settings.state.value = AppSettings(autoDeleteCodes = true, autoDeleteCodesSinceMillis = 5L)
        c.manager.restore(parsed(on), canLock = true)
        assertEquals(5L, c.settings.state.value.autoDeleteCodesSinceMillis)

        val off = parsed(on).let { it.copy(settings = it.settings.copy(autoDeleteCodes = false)) }
        c.manager.restore(off, canLock = true)
        assertFalse(c.settings.state.value.autoDeleteCodes)
        assertEquals(null, c.settings.state.value.autoDeleteCodesSinceMillis)
    }

    @Test
    fun `the skin travels with the file, and a file without one keeps the phone's`() = runTest {
        val a = Phone()
        a.setUp()
        val b = Phone()
        b.manager.restore(parsed(a.exportJson()), canLock = true)
        assertEquals("pomegranate", b.settings.state.value.skin)
        val c = Phone()
        c.settings.state.value = AppSettings(skin = "desert")
        c.manager.restore(parsed("""{"format":"pigeon-settings","version":2,"settings":{"theme":"light"}}"""), canLock = true)
        assertEquals("desert", c.settings.state.value.skin)
    }

    @Test
    fun `settings missing from the file keep their current value`() = runTest {
        val b = Phone()
        b.settings.state.value = AppSettings(theme = AppTheme.Light, blockScreenshots = true)
        val file = parsed("""{"format":"pigeon-settings","version":1,"settings":{"language":"english"}}""")
        b.manager.restore(file, canLock = true)
        assertEquals(AppLanguage.English, b.settings.state.value.language)
        assertEquals(AppTheme.Light, b.settings.state.value.theme)
        assertTrue(b.settings.state.value.blockScreenshots)
        assertEquals(null, b.icon)
    }

    @Test
    fun `without access to the messages the chat data is left as it is`() = runTest {
        val a = Phone()
        a.setUp()
        val json = a.exportJson()

        val b = Phone()
        b.pins.pin(b.bank)
        b.locks.lock(b.dana)
        val old = b.reminders.add(b.dana, null, "mine", Now + HOUR)
        b.messages.threads.value = emptyList()
        val report = b.manager.restore(parsed(json), canLock = true)

        assertTrue(report.messagesUnavailable)
        assertEquals(setOf(b.bank), b.pins.state.value)
        assertEquals(setOf(b.dana), b.locks.state.value)
        assertEquals(listOf(old), b.reminders.state.value.map { it.id })
        assertTrue(b.scheduler.cancelled.isEmpty())
        // The rest still came across
        assertEquals(listOf("Bank", "Parcels"), b.rules.state.value.map { it.label })
        assertEquals(AppTheme.Dark, b.settings.state.value.theme)
    }

    @Test
    fun `an empty file clears nothing it does not cover`() = runTest {
        val b = Phone()
        b.settings.state.value = AppSettings(theme = AppTheme.Light, welcomeDone = true)
        b.manager.restore(parsed("""{"format":"pigeon-settings","version":1}"""), canLock = true)
        assertEquals(AppTheme.Light, b.settings.state.value.theme)
        assertTrue(b.rules.state.value.isEmpty())
    }

    // Summary

    @Test
    fun `the summary counts each kind`() = runTest {
        val a = Phone()
        a.setUp()
        val summary = parsed(a.exportJson()).summary()
        assertEquals("ten settings and the skin", 11, summary.settings)
        assertEquals(2, summary.rules)
        assertEquals(2, summary.categories)
        assertEquals(3, summary.moves)
        assertEquals(1, summary.pinned)
        assertEquals(1, summary.locked)
        assertEquals(1, summary.notSpam)
        assertEquals(2, summary.blockedWords)
        assertEquals(3, summary.reminders)
        assertEquals(2, summary.favorites)
        assertEquals("0.4.5", summary.appVersion)
        assertEquals(Now, summary.exportedAtMillis)
    }

    @Test
    fun `a group chat travels by its members`() = runTest {
        val a = Phone()
        a.setUp()
        val moves = a.manager.export("x").moves.map { it.key }
        assertTrue(moves.contains("group:525550103,525550104"))
        assertFalse(moves.any { it.startsWith("thread:") })
    }
}
