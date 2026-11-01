package com.havivon.pigeon.ui.bin

import com.havivon.pigeon.domain.bin.BinCopy
import com.havivon.pigeon.domain.bin.DAY_MILLIS
import com.havivon.pigeon.domain.bin.RecycleBin
import com.havivon.pigeon.domain.model.Contact
import com.havivon.pigeon.domain.model.SmsType
import com.havivon.pigeon.fakes.FakeBinStore
import com.havivon.pigeon.fakes.FakeContacts
import com.havivon.pigeon.fakes.MainDispatcherRule
import com.havivon.pigeon.fakes.Now
import com.havivon.pigeon.fakes.englishLabels
import com.havivon.pigeon.fakes.millis
import com.havivon.pigeon.fakes.sms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Made-up messages only.
@OptIn(ExperimentalCoroutinesApi::class)
class BinViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val store = FakeBinStore()
    private val bin = RecycleBin(store) { Now }
    private val contacts = FakeContacts(listOf(Contact(1, "Dana", listOf("0525550100"))))
    private val labels = englishLabels()
    private var canWrite = true
    private val inserted = mutableListOf<Pair<Long, Long>>()

    private fun TestScope.screen() = BinViewModel(
        bin, contacts, CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        lockedText = "Locked chat",
        time = labels::listTime,
        canWriteNow = { canWrite },
        threadFor = { 500L },
        insert = { m, threadId -> inserted += m.originalId to threadId; true },
        now = { Now },
        work = main.dispatcher,
    ).also { vm -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } } }

    private suspend fun seed() {
        val dana = listOf("0525550100")
        val shop = listOf("ShopNow")
        bin.moveToBin(
            listOf(
                BinCopy(sms(10, 1, "Hello there", millis(2026, 10, 31, 9, 0), "0525550100"), dana),
                BinCopy(sms(11, 1, "See you soon", millis(2026, 10, 31, 9, 5), "0525550100", type = SmsType.Sent), dana),
                BinCopy(sms(20, 2, "Big sale today", millis(2026, 10, 30, 9, 0), "ShopNow"), shop),
            ),
            emptySet(),
        ) {}
        bin.moveToBin(listOf(BinCopy(sms(30, 3, "The gate code is 4590", millis(2026, 10, 29, 9, 0), "0525550300"), listOf("0525550300"))), setOf(3L)) {}
    }

    @Test
    fun `the bin lists one row per conversation, with names, the deletion and the days left`() = runTest {
        seed()
        val vm = screen()
        val groups = vm.state.value.groups
        assertEquals(listOf("Dana", "ShopNow", "⁦0525550300⁩"), groups.map { it.name })
        val dana = groups.first()
        assertEquals(2, dana.messageCount)
        assertEquals(listOf("See you soon", "Hello there"), dana.messages.map { it.text })
        assertTrue(dana.messages.first().outgoing)
        assertEquals(30, dana.daysLeft)
        assertEquals("12:00", dana.deletedLabel)
    }

    @Test
    fun `a locked chat shows nothing of its messages until it is unlocked`() = runTest {
        seed()
        val vm = screen()
        val locked = vm.state.value.groups.last()
        assertTrue(locked.locked)
        assertEquals(listOf("Locked chat"), locked.messages.map { it.text })
        assertTrue(vm.state.value.groups.flatMap { it.messages }.none { it.text.contains("4590") })
        vm.unlock(locked)
        val shown = vm.state.value.groups.last()
        assertFalse(shown.locked)
        assertEquals(listOf("The gate code is 4590"), shown.messages.map { it.text })
        assertTrue(shown.key in vm.state.value.expanded)
    }

    @Test
    fun `long press selects, taps add and remove, and the actions apply to the chosen rows only`() = runTest {
        seed()
        val vm = screen()
        val (dana, shop, locked) = vm.state.value.groups
        vm.select(dana)
        assertTrue(vm.state.value.selecting)
        vm.toggleSelected(shop)
        vm.toggleSelected(locked)
        vm.toggleSelected(locked)
        assertEquals(setOf(dana.key, shop.key), vm.state.value.selected)

        assertTrue(vm.restore(vm.state.value.selected))
        assertEquals(listOf(10L to 500L, 11L to 500L, 20L to 500L), inserted)
        assertEquals(listOf(locked.key), vm.state.value.groups.map { it.key })
        assertFalse(vm.state.value.selecting)

        vm.select(vm.state.value.groups.single())
        vm.clearSelection()
        assertFalse(vm.state.value.selecting)
    }

    @Test
    fun `restoring needs the default SMS app, deleting for good does not`() = runTest {
        seed()
        canWrite = false
        val vm = screen()
        val (dana, shop) = vm.state.value.groups
        vm.select(dana)
        assertFalse(vm.restore(vm.state.value.selected))
        assertTrue(inserted.isEmpty())
        // Selection mode stays, so the user can try again once Pigeon is the default
        assertTrue(vm.state.value.selecting)
        assertEquals(3, vm.state.value.groups.size)

        vm.deleteForever(setOf(shop.key))
        assertEquals(listOf(dana.key, vm.state.value.groups.last().key), vm.state.value.groups.map { it.key })
        assertEquals(listOf(10L, 11L, 30L), store.rows.value.map { it.originalId }.sorted())
        vm.empty()
        assertTrue(vm.state.value.groups.isEmpty())
    }

    @Test
    fun `what is past its 30 days is not shown, even before the daily purge`() = runTest {
        val old = RecycleBin(store) { Now - 31 * DAY_MILLIS }
        old.moveToBin(listOf(BinCopy(sms(40, 4, "Old one", millis(2026, 9, 1, 9, 0), "0525550400"), listOf("0525550400"))), emptySet()) {}
        seed()
        val vm = screen()
        assertEquals(3, vm.state.value.groups.size)
        assertTrue(vm.state.value.groups.none { g -> g.messages.any { it.text == "Old one" } })
    }
}
