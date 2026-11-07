package com.havivon.pigeon.ui.blocked

import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.fakes.FakeBlockedKeywords
import com.havivon.pigeon.fakes.FakeFolders
import com.havivon.pigeon.fakes.FakeRules
import com.havivon.pigeon.fakes.FakeSettings
import com.havivon.pigeon.fakes.FakeSystemBlockList
import com.havivon.pigeon.fakes.MainDispatcherRule
import com.havivon.pigeon.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Settings → Messages → Blocked numbers, and the settings rows around it. */
@OptIn(ExperimentalCoroutinesApi::class)
class BlockedNumbersViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val names = mapOf("+972525550101" to "Dana")

    private fun TestScope.vm(system: FakeSystemBlockList) = BlockedNumbersViewModel(system) { names[it] }.also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    @Test
    fun `the phone's list shows with the contacts' names`() = runTest {
        val vm = vm(FakeSystemBlockList(listOf("+972525550101", "0541234567")))
        val s = vm.state.value
        assertTrue(s.available)
        assertEquals(listOf(BlockedEntry("+972525550101", "Dana"), BlockedEntry("0541234567", null)), s.entries)
    }

    @Test
    fun `a typed number is blocked on the phone and the field empties`() = runTest {
        val system = FakeSystemBlockList()
        val vm = vm(system)
        var done: Boolean? = null
        vm.setDraft("054-123 4567abc")
        assertEquals("only what a number can hold", "054-123 4567", vm.state.value.draft)
        vm.add { done = it }
        assertEquals(true, done)
        assertEquals(listOf("054-123 4567"), system.state.value)
        assertEquals("", vm.state.value.draft)
        assertEquals(1, vm.state.value.entries.size)
    }

    @Test
    fun `a number already on the list, under another way of writing it, is a duplicate`() = runTest {
        val system = FakeSystemBlockList(listOf("+972541234567"))
        val vm = vm(system)
        vm.setDraft("0541234567")
        vm.add()
        assertEquals(BlockProblem.Duplicate, vm.state.value.problem)
        assertTrue(system.blocked.isEmpty())
        vm.setDraft("05412345678")
        assertNull("typing again clears it", vm.state.value.problem)
    }

    @Test
    fun `something that is not a number is refused, and a blank field does nothing`() = runTest {
        val system = FakeSystemBlockList()
        val vm = vm(system)
        vm.add()
        assertNull(vm.state.value.problem)
        vm.setDraft("--")
        vm.add()
        assertEquals(BlockProblem.Invalid, vm.state.value.problem)
        assertTrue(system.blocked.isEmpty())
    }

    @Test
    fun `unblocking takes every way the number is written off the list`() = runTest {
        val system = FakeSystemBlockList(listOf("+972541234567", "054-1234567", "0529999999"))
        val vm = vm(system)
        vm.unblock(BlockedEntry("+972541234567"))
        assertEquals(listOf("0529999999"), system.state.value)
        assertEquals(listOf(BlockedEntry("0529999999")), vm.state.value.entries)
    }

    @Test
    fun `as a viewer the screen says the list is out of reach, and adding fails`() = runTest {
        val system = FakeSystemBlockList(available = false)
        val vm = vm(system)
        assertFalse(vm.state.value.available)
        assertTrue(vm.state.value.entries.isEmpty())
        var done: Boolean? = null
        vm.setDraft("0541234567")
        vm.add { done = it }
        assertEquals(false, done)
        assertEquals(BlockProblem.Failed, vm.state.value.problem)
    }

    // The settings rows

    private val settings = FakeSettings()

    private fun TestScope.settingsVm(system: FakeSystemBlockList) =
        SettingsViewModel(settings, FakeRules(), FakeBlockedKeywords(), FakeFolders(), AppIcon.Envelope, system.numbers) { }.also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        }

    @Test
    fun `the Blocked numbers row counts the phone's list, or says it is out of reach`() = runTest {
        val system = FakeSystemBlockList(listOf("0541234567"))
        val vm = settingsVm(system)
        assertEquals(1, vm.state.value.blockedNumberCount)
        system.state.value = null
        assertNull(vm.state.value.blockedNumberCount)
    }

    @Test
    fun `the two new switches write their settings, both on by default`() = runTest {
        val vm = settingsVm(FakeSystemBlockList())
        assertTrue(vm.state.value.settings.blockSpamOnPhone)
        assertTrue(vm.state.value.settings.autoCopyCodes)
        vm.setBlockSpamOnPhone(false)
        vm.setAutoCopyCodes(false)
        assertFalse(settings.state.value.blockSpamOnPhone)
        assertFalse(settings.state.value.autoCopyCodes)
        vm.setAutoCopyCodes(true)
        assertTrue(settings.state.value.autoCopyCodes)
        assertFalse(settings.state.value.blockSpamOnPhone)
    }
}
