package com.havivon.pigeon.ui.folders

import com.havivon.pigeon.domain.model.Categories
import com.havivon.pigeon.domain.model.CategoryTag
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.domain.model.SpamVerdict
import com.havivon.pigeon.domain.model.rowColor
import com.havivon.pigeon.fakes.FakeFolders
import com.havivon.pigeon.fakes.FakeLocks
import com.havivon.pigeon.fakes.FakeMessageRepository
import com.havivon.pigeon.fakes.FakePins
import com.havivon.pigeon.fakes.FakeReminders
import com.havivon.pigeon.fakes.FakeRules
import com.havivon.pigeon.fakes.FakeScheduler
import com.havivon.pigeon.fakes.FakeSpam
import com.havivon.pigeon.fakes.MainDispatcherRule
import com.havivon.pigeon.fakes.Now
import com.havivon.pigeon.fakes.conversation
import com.havivon.pigeon.fakes.englishLabels
import com.havivon.pigeon.fakes.millis
import com.havivon.pigeon.fakes.sms
import com.havivon.pigeon.fakes.testMapper
import com.havivon.pigeon.fakes.testMoves
import com.havivon.pigeon.ui.inbox.InboxViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The optional colour of a category: set, cleared, shown on the rows, and below a colour rule in priority. */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryColorsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val blue = 0xFF2F80EDL
    private val green = 0xFF1E9E6AL
    private val red = 0xFFE53935L
    private val mapper = testMapper(englishLabels())
    private val messages = FakeMessageRepository()
    private val rules = FakeRules()
    private val pins = FakePins()
    private val locks = FakeLocks()
    private val spam = FakeSpam()
    private val folders = FakeFolders()
    private val moves = testMoves(folders, spam)

    private fun TestScope.collect(flow: Flow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
    }

    private fun inbox() = InboxViewModel(
        messages, rules, pins, locks, spam, folders, moves, FakeReminders(), FakeScheduler(), mapper, MutableStateFlow(0), { "" }, {},
        now = { Now }, work = main.dispatcher,
    )

    private val leumi = conversation(2, "Leumi", snippet = "Charge of 249.90 on the card ending 4821", dateMillis = millis(2026, 11, 1, 10, 0), unread = 1)
    private val stranger = conversation(3, "0541112222", snippet = "Tree service", dateMillis = millis(2026, 11, 1, 9, 0))

    // Which colour a category has

    @Test
    fun `built-in folders and categories of the user's have a colour, All and Spam never do`() {
        val categories = Categories(listOf(CustomFolder(7, "Work", green), CustomFolder(8, "Club")), mapOf(Folder.Banks to blue, Folder.Spam to red, Folder.All to red))
        assertEquals(blue, categories.colorOf(FolderChoice.BuiltIn(Folder.Banks)))
        assertNull(categories.colorOf(FolderChoice.BuiltIn(Folder.Personal)))
        assertNull("Spam keeps its own red", categories.colorOf(FolderChoice.BuiltIn(Folder.Spam)))
        assertNull(categories.colorOf(FolderChoice.BuiltIn(Folder.All)))
        assertEquals(green, categories.colorOf(FolderChoice.Custom(7)))
        assertNull("no colour is the default", categories.colorOf(FolderChoice.Custom(8)))
        assertNull(categories.colorOf(FolderChoice.Custom(99)))
        assertEquals(listOf(Folder.Uncategorized, Folder.Personal, Folder.Banks, Folder.Insurance, Folder.Codes, Folder.Deliveries), Folder.entries.filter { it.colourable })
    }

    @Test
    fun `a colour rule comes first, then the category colour, then none`() {
        val rule = ColorRule(1, "Bank", RuleMatch.Sender("Leumi"), red)
        val banks = CategoryTag("Banks", green)
        assertEquals(red, rowColor(rule, banks))
        assertEquals(green, rowColor(null, banks))
        assertNull(rowColor(null, CategoryTag("Banks")))
        assertNull(rowColor(null, null))
    }

    @Test
    fun `the mapper puts the category's colour on the conversation, a move by hand included`() {
        val categories = Categories(listOf(CustomFolder(7, "Work", green)), mapOf(Folder.Banks to blue))
        assertEquals(CategoryTag("Banks", blue), mapper.map(leumi, Now, categories = categories).category)
        // Moved to a category of the user's: that category's name and colour
        assertEquals(CategoryTag("Work", green), mapper.map(leumi, Now, override = FolderChoice.Custom(7), categories = categories).category)
        // Moved to a built-in folder with no colour
        assertEquals(CategoryTag("Personal"), mapper.map(leumi, Now, override = FolderChoice.BuiltIn(Folder.Personal), categories = categories).category)
        // No category: no tag, nothing to colour
        assertNull(mapper.map(stranger, Now, categories = categories).category)
        // Spam is tagged and stays neutral, whatever its folder was
        val verdict = SpamVerdict("Leumi", 2, listOf(SpamReason.ShortLink), 0)
        assertEquals(CategoryTag("Spam", null), mapper.map(leumi, Now, verdict = verdict, categories = categories).category)
        // Without categories given (the widgets), the built-in tag is there and has no colour
        assertEquals(CategoryTag("Banks"), mapper.map(leumi, Now).category)
    }

    // The inbox

    @Test
    fun `the inbox rows, the unread rows and the chips carry the colours`() = runTest {
        messages.threads.value = listOf(leumi, stranger)
        messages.allMessages.value = listOf(sms(20, 2, leumi.snippet, millis(2026, 11, 1, 10, 0), "Leumi", read = false))
        val work = folders.add("Work", green)
        folders.setColor(FolderChoice.BuiltIn(Folder.Banks), blue)
        val vm = inbox()
        collect(vm.state)
        vm.selectUnreadOnly(true)
        val s = vm.state.value
        assertEquals(mapOf(Folder.Banks to blue), s.builtInColors)
        assertEquals(green, s.customFolders.single { it.id == work }.argb)
        assertEquals(CategoryTag("Banks", blue), s.all.first { it.id == 2L }.category)
        assertEquals(CategoryTag("Banks", blue), s.unread!!.single().category)
        // Changing the colour reaches the rows at once
        folders.setColor(FolderChoice.BuiltIn(Folder.Banks), null)
        assertNull(vm.state.value.all.first { it.id == 2L }.category!!.argb)
        assertTrue(vm.state.value.builtInColors.isEmpty())
    }

    // Setting the colour

    @Test
    fun `a new category can have a colour, and has none by default`() = runTest {
        val plain = moves.create("Club")!!
        val colored = moves.create("Work", green)!!
        assertNull(folders.folderState.value.single { it.id == plain }.argb)
        assertEquals(green, folders.folderState.value.single { it.id == colored }.argb)
        val id = moves.createAndMove(" Family ", 5, listOf("0525550100"), isSpam = false, argb = blue)!!
        assertEquals(CustomFolder(id, "Family", blue), folders.folderState.value.single { it.id == id })
    }

    @Test
    fun `the colour of a category or a built-in folder is set and cleared, All and Spam are ignored`() = runTest {
        val work = moves.create("Work")!!
        moves.setColor(FolderChoice.Custom(work), blue)
        moves.setColor(FolderChoice.BuiltIn(Folder.Codes), green)
        moves.setColor(FolderChoice.BuiltIn(Folder.Spam), red)
        moves.setColor(FolderChoice.BuiltIn(Folder.All), red)
        assertEquals(blue, folders.folderState.value.single().argb)
        assertEquals(mapOf(Folder.Codes to green), folders.builtInColorState.value)
        moves.setColor(FolderChoice.Custom(work), null)
        moves.setColor(FolderChoice.BuiltIn(Folder.Codes), null)
        assertNull(folders.folderState.value.single().argb)
        assertTrue(folders.builtInColorState.value.isEmpty())
    }

    @Test
    fun `deleting a category takes its colour with it, a rename keeps it`() = runTest {
        val work = moves.create("Work", green)!!
        moves.rename(work, "Office")
        assertEquals(CustomFolder(work, "Office", green), folders.folderState.value.single())
        moves.delete(work)
        assertTrue(folders.folderState.value.isEmpty())
    }

    // The picker of the Categories screen

    private fun TestScope.categories(): FoldersViewModel = FoldersViewModel(folders, moves).also { collect(it.folders); collect(it.builtInColors) }

    @Test
    fun `the picker opens for one category, applies the swatch and closes`() = runTest {
        val work = moves.create("Work")!!
        val vm = categories()
        assertNull(vm.picking.value)
        vm.pickColorFor(FolderChoice.Custom(work))
        assertEquals(FolderChoice.Custom(work), vm.picking.value)
        assertNull("none yet", vm.colorOf(FolderChoice.Custom(work)))
        vm.chooseColor(blue)
        assertNull("it closes", vm.picking.value)
        assertEquals(blue, vm.colorOf(FolderChoice.Custom(work)))
        assertEquals(blue, vm.folders.value.single().argb)
    }

    @Test
    fun `the picker colours a built-in category, and No colour takes the colour away`() = runTest {
        val vm = categories()
        vm.pickColorFor(FolderChoice.BuiltIn(Folder.Insurance))
        vm.chooseColor(green)
        assertEquals(mapOf(Folder.Insurance to green), vm.builtInColors.value)
        vm.pickColorFor(FolderChoice.BuiltIn(Folder.Insurance))
        vm.chooseColor(null)
        assertTrue(vm.builtInColors.value.isEmpty())
        assertNull(vm.colorOf(FolderChoice.BuiltIn(Folder.Insurance)))
    }

    @Test
    fun `closing the picker without a choice changes nothing, and a choice without a picker does nothing`() = runTest {
        val work = moves.create("Work", blue)!!
        val vm = categories()
        vm.chooseColor(green)
        assertEquals(blue, folders.folderState.value.single().argb)
        vm.pickColorFor(FolderChoice.Custom(work))
        vm.dismissPicker()
        assertNull(vm.picking.value)
        vm.chooseColor(green)
        assertEquals(blue, folders.folderState.value.single().argb)
    }
}
