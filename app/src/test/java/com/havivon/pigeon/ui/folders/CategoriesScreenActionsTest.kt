package com.havivon.pigeon.ui.folders

import com.havivon.pigeon.domain.folders.FolderKeys
import com.havivon.pigeon.domain.folders.FolderNameProblem
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.fakes.FakeFolders
import com.havivon.pigeon.fakes.FakeSpam
import com.havivon.pigeon.fakes.MainDispatcherRule
import com.havivon.pigeon.fakes.testMoves
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

/** Settings → Categories: New category, rename, delete and reorder, each as the screen's buttons call it. */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesScreenActionsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val folders = FakeFolders()
    private val moves = testMoves(folders, FakeSpam())
    private val blue = 0xFF2F80EDL

    private fun TestScope.vm(): FoldersViewModel = FoldersViewModel(folders, moves).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.folders.collect { } }
    }

    @Test
    fun `New category makes one at the end, with or without a colour`() = runTest {
        val vm = vm()
        val results = mutableListOf<Boolean>()
        vm.create("Work", null) { results += it }
        vm.create("  Family   time ", blue) { results += it }
        assertEquals(listOf(true, true), results)
        assertEquals(listOf("Work", "Family time"), vm.folders.value.map { it.name })
        assertNull(vm.folders.value[0].argb)
        assertEquals(blue, vm.folders.value[1].argb)
    }

    @Test
    fun `the new-category dialog refuses a blank, a taken or a built-in name`() = runTest {
        val vm = vm()
        vm.create("Work", null)
        assertEquals(FolderNameProblem.Blank, vm.newProblem("   "))
        assertEquals(FolderNameProblem.Taken, vm.newProblem("work"))
        assertEquals(FolderNameProblem.Taken, vm.newProblem("Banks"))
        assertEquals(FolderNameProblem.TooLong, vm.newProblem("x".repeat(25)))
        assertNull(vm.newProblem("Gym"))
        var made: Boolean? = null
        vm.create("WORK", null) { made = it }
        assertEquals("nothing is made for a taken name", false, made)
        assertEquals(1, vm.folders.value.size)
    }

    @Test
    fun `the pencil renames, and keeps the colour and its own name free`() = runTest {
        val vm = vm()
        vm.create("Work", blue)
        vm.create("Family", null)
        val work = vm.folders.value[0]
        assertNull("its own name is free", vm.renameProblem(work, "work"))
        assertEquals(FolderNameProblem.Taken, vm.renameProblem(work, "Family"))
        vm.rename(work, "Office")
        assertEquals(CustomFolder(work.id, "Office", blue), vm.folders.value[0])
        vm.rename(work, "Family")
        assertEquals("a taken name changes nothing", "Office", vm.folders.value[0].name)
    }

    @Test
    fun `the bin deletes the category and its conversations go back to the automatic sorting`() = runTest {
        val vm = vm()
        vm.create("Work", null)
        val work = vm.folders.value.single()
        val key = FolderKeys.of(5, listOf("0525550101"))
        folders.setOverride(key, FolderChoice.Custom(work.id))
        vm.delete(work)
        assertTrue(vm.folders.value.isEmpty())
        assertFalse(key in folders.overrideState.value)
    }

    @Test
    fun `a drag puts the categories in the new order`() = runTest {
        val vm = vm()
        vm.create("A", null)
        vm.create("B", null)
        vm.create("C", null)
        val ids = vm.folders.value.map { it.id }
        vm.reorder(listOf(ids[2], ids[0], ids[1]))
        assertEquals(listOf("C", "A", "B"), vm.folders.value.map { it.name })
    }
}
