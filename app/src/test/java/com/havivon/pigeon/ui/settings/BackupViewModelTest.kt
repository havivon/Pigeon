package com.havivon.pigeon.ui.settings

import com.havivon.pigeon.data.backup.BackupJson
import com.havivon.pigeon.domain.backup.BackupError
import com.havivon.pigeon.domain.backup.BackupLimits
import com.havivon.pigeon.domain.backup.SettingsBackupManager
import com.havivon.pigeon.domain.model.AppTheme
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
import com.havivon.pigeon.fakes.MainDispatcherRule
import com.havivon.pigeon.fakes.Now
import com.havivon.pigeon.fakes.conversation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BackupViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettings()
    private val messages = FakeMessageRepository().also { it.threads.value = listOf(conversation(1, "0525550101", "Dana")) }
    private val rules = FakeRules()
    private val folders = FakeFolders()
    private val pins = FakePins()
    private val locks = FakeLocks()
    private val spam = FakeSpam()
    private val keywords = FakeBlockedKeywords()
    private val favorites = FakeFavorites()
    private val reminders = FakeReminders()
    private val manager = SettingsBackupManager(
        settings, rules, folders, pins, locks, spam, keywords, favorites, reminders, messages, FakeScheduler(),
        FakeBackupStore(rules, folders, pins, locks, spam, keywords, favorites, reminders), applyIcon = {}, now = { Now },
    )
    private val vm = BackupViewModel(manager)

    private val goodFile = """{"format":"pigeon-settings","version":1,"settings":{"theme":"dark"},"blockedWords":["casino"]}""".toByteArray()

    @Test
    fun `export writes a pigeon settings file and says so`() = runTest {
        var written: String? = null
        vm.export("0.4.5") { json -> written = json; true }
        assertEquals(BackupEvent.Exported, vm.eventFlow.first())
        assertTrue(written!!.contains("\"pigeon-settings\""))
        assertTrue(written!!.contains("0.4.5"))
    }

    @Test
    fun `a file that cannot be written is reported`() = runTest {
        vm.export("0.4.5") { false }
        assertEquals(BackupEvent.ExportFailed, vm.eventFlow.first())
        vm.export("0.4.5") { error("disk full") }
        assertEquals(BackupEvent.ExportFailed, vm.eventFlow.first())
    }

    @Test
    fun `a good file waits for the confirmation and changes nothing yet`() = runTest {
        vm.load { goodFile }
        val pending = vm.pending.value
        assertNotNull(pending)
        assertEquals(1, pending!!.summary().blockedWords)
        assertEquals(AppTheme.System, settings.state.value.theme)
        assertTrue(keywords.state.value.isEmpty())
    }

    @Test
    fun `cancelling drops the file`() = runTest {
        vm.load { goodFile }
        vm.cancelImport()
        assertNull(vm.pending.value)
        assertEquals(AppTheme.System, settings.state.value.theme)
    }

    @Test
    fun `confirming applies the file and reports it`() = runTest {
        vm.load { goodFile }
        vm.confirmImport(canLock = true)
        val event = vm.eventFlow.first()
        assertTrue(event is BackupEvent.Imported)
        assertNull(vm.pending.value)
        assertEquals(AppTheme.Dark, settings.state.value.theme)
        assertEquals(listOf("casino"), keywords.state.value.map { it.word })
    }

    @Test
    fun `confirming with no file does nothing`() = runTest {
        vm.confirmImport(canLock = true)
        assertNull(vm.pending.value)
        assertTrue(keywords.state.value.isEmpty())
    }

    @Test
    fun `files that are not settings are refused with the reason and nothing is kept`() = runTest {
        vm.load { "not json".toByteArray() }
        assertEquals(BackupEvent.Refused(BackupError.NotJson), vm.eventFlow.first())
        vm.load { """{"format":"pigeon-settings","version":7}""".toByteArray() }
        assertEquals(BackupEvent.Refused(BackupError.TooNew), vm.eventFlow.first())
        vm.load { ByteArray(BackupLimits.MAX_FILE_BYTES + 1) }
        assertEquals(BackupEvent.Refused(BackupError.TooLarge), vm.eventFlow.first())
        vm.load { null }
        assertEquals(BackupEvent.Refused(BackupError.Unreadable), vm.eventFlow.first())
        vm.load { error("no such file") }
        assertEquals(BackupEvent.Refused(BackupError.Unreadable), vm.eventFlow.first())
        assertNull(vm.pending.value)
    }

    @Test
    fun `a failure while importing is reported and the file is dropped`() = runTest {
        vm.load { goodFile }
        // The conversation list failing is enough to make the import throw
        val failing = BackupViewModel(
            SettingsBackupManager(
                settings, rules, folders, pins, locks, spam, keywords, favorites, reminders, messages, FakeScheduler(),
                object : com.havivon.pigeon.domain.backup.BackupStore {
                    override suspend fun replace(plan: com.havivon.pigeon.domain.backup.RestorePlan): List<com.havivon.pigeon.domain.model.Reminder> = error("database is locked")
                },
                applyIcon = {}, now = { Now },
            ),
        )
        failing.load { goodFile }
        failing.confirmImport(canLock = true)
        assertEquals(BackupEvent.ImportFailed, failing.eventFlow.first())
        assertNull(failing.pending.value)
        assertEquals(AppTheme.System, settings.state.value.theme)
    }

    @Test
    fun `what the view model writes can be read back by the parser`() = runTest {
        var written = ""
        vm.export("0.4.5") { json -> written = json; true }
        vm.eventFlow.first()
        assertTrue(BackupJson.parse(written) is com.havivon.pigeon.domain.backup.BackupParse.Ok)
    }
}
