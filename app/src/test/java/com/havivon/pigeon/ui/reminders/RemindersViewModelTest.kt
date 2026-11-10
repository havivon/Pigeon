package com.havivon.pigeon.ui.reminders

import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.Contact
import com.havivon.pigeon.fakes.FakeBlockedKeywords
import com.havivon.pigeon.fakes.FakeContacts
import com.havivon.pigeon.fakes.FakeFolders
import com.havivon.pigeon.fakes.FakeLocks
import com.havivon.pigeon.fakes.FakeMessageRepository
import com.havivon.pigeon.fakes.FakeReminders
import com.havivon.pigeon.fakes.FakeRules
import com.havivon.pigeon.fakes.FakeScheduler
import com.havivon.pigeon.fakes.FakeSettings
import com.havivon.pigeon.fakes.MainDispatcherRule
import com.havivon.pigeon.fakes.Now
import com.havivon.pigeon.fakes.englishLabels
import com.havivon.pigeon.fakes.millis
import com.havivon.pigeon.fakes.sms
import com.havivon.pigeon.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Reminders screen: what it lists, in which order, what a locked chat hides, and what cancelling and moving do. */
@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour
    private val labels = englishLabels()
    private val reminders = FakeReminders()
    private val scheduler = FakeScheduler()
    private val locks = FakeLocks()
    private val messages = FakeMessageRepository()
    private val contacts = FakeContacts(listOf(Contact(1, "Mom", listOf("0525550100"))))

    private fun vm() = RemindersViewModel(
        reminders, scheduler, messages, contacts, locks,
        whenLabel = { millis, now -> labels.reminder(millis, now, withTime = true) },
        now = { Now },
        work = main.dispatcher,
    )

    private fun TestScope.watch(vm: RemindersViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    @Test
    fun `upcoming reminders come soonest first with the sender, the text and the time`() = runTest {
        messages.allMessages.value = listOf(
            sms(1, 10, "see you", Now - hour, address = "0525550100"),
            sms(2, 20, "your parcel", Now - hour, address = "0525550222"),
        )
        reminders.add(20, 2, "your parcel", Now + 2 * day)
        reminders.add(10, 1, "see you", millis(2026, 11, 1, 18, 0))
        val vm = vm().also { watch(it) }
        val rows = vm.state.first { it.loaded }.upcoming
        assertEquals(listOf("Mom", "0525550222"), rows.map { it.sender })
        assertEquals(listOf("see you", "your parcel"), rows.map { it.text })
        assertEquals(listOf("Today 18:00", "Tue 12:00"), rows.map { it.whenLabel })
        assertEquals(listOf(1L, 2L), rows.map { it.messageId })
    }

    @Test
    fun `the past section holds done and missed reminders of the last thirty days`() = runTest {
        reminders.add(10, null, "old", Now - 40 * day).also { reminders.setDone(it) }
        reminders.add(10, null, "done", Now - 2 * day).also { reminders.setDone(it) }
        reminders.add(10, null, "missed", Now - hour)
        reminders.add(10, null, "ahead", Now + hour)
        val state = vm().also { watch(it) }.state.first { it.loaded }
        assertEquals(listOf("ahead"), state.upcoming.map { it.text })
        assertEquals(listOf("missed", "done"), state.past.map { it.text })
    }

    @Test
    fun `a locked chat shows neither its sender nor its text`() = runTest {
        messages.allMessages.value = listOf(sms(1, 10, "secret", Now - hour, address = "0525550100"))
        reminders.add(10, 1, "secret", Now + hour)
        locks.lock(10)
        val row = vm().also { watch(it) }.state.first { it.loaded }.upcoming.single()
        assertTrue(row.locked)
        assertNull(row.sender)
        assertNull(row.text)
    }

    @Test
    fun `cancelling removes the reminder and its alarm, and undo brings it back with its alarm`() = runTest {
        val id = reminders.add(10, 1, "see you", Now + hour)
        val vm = vm().also { watch(it) }
        val removed = vm.cancel(id)
        assertNotNull(removed)
        assertEquals(listOf(id), scheduler.cancelled)
        assertTrue(vm.state.first { it.loaded }.upcoming.isEmpty())
        assertNull(vm.cancel(id))

        vm.restore(removed!!)
        val again = vm.state.first { it.upcoming.isNotEmpty() }.upcoming.single()
        assertEquals("see you", again.text)
        assertEquals(Now + hour, again.atMillis)
        assertEquals(Now + hour, scheduler.scheduled.last().triggerAtMillis)
    }

    @Test
    fun `changing the time moves the reminder and sets its alarm again`() = runTest {
        val id = reminders.add(10, null, "later", Now + hour)
        val vm = vm().also { watch(it) }
        assertTrue(vm.reschedule(id, Now + 5 * hour))
        assertEquals(Now + 5 * hour, reminders.get(id)!!.triggerAtMillis)
        assertEquals(id, scheduler.scheduled.last().id)
        assertEquals(Now + 5 * hour, scheduler.scheduled.last().triggerAtMillis)
        assertEquals(Now + 5 * hour, vm.state.first { it.loaded }.upcoming.single().atMillis)
    }

    @Test
    fun `a time in the past is refused and nothing changes`() = runTest {
        val id = reminders.add(10, null, "later", Now + hour)
        val vm = vm()
        assertFalse(vm.reschedule(id, Now - hour))
        assertEquals(Now + hour, reminders.get(id)!!.triggerAtMillis)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `moving a past reminder to the future makes it upcoming again`() = runTest {
        val id = reminders.add(10, null, "again", Now - hour)
        reminders.setDone(id)
        val vm = vm().also { watch(it) }
        assertTrue(vm.reschedule(id, Now + hour))
        val state = vm.state.first { it.upcoming.isNotEmpty() }
        assertEquals(listOf("again"), state.upcoming.map { it.text })
        assertTrue(state.past.isEmpty())
    }

    @Test
    fun `the settings row gets the number of reminders still ahead`() = runTest {
        val settings = SettingsViewModel(
            FakeSettings(), FakeRules(), FakeBlockedKeywords(), FakeFolders(), AppIcon.Envelope,
            upcomingReminders = flowOf(2),
        ) { }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { settings.state.collect { } }
        assertEquals(2, settings.state.first { it.reminderCount > 0 }.reminderCount)
    }
}
