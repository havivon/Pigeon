package com.havivon.pigeon.ui.inbox

import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The category menu that replaced the row of folder chips: its lines, their numbers, the chosen one, and what a tap does. */
class CategoryMenuTest {

    private val work = CustomFolder(1, "Work", 0xFF1E9E6AL)
    private val committee = CustomFolder(2, "Building committee")
    private val counts = mapOf(Folder.All to 31, Folder.Personal to 4, Folder.Banks to 2, Folder.Codes to 1, Folder.Spam to 5)
    private val colours = mapOf(Folder.Personal to 0xFF2F80EDL)

    private fun menu(selected: Folder = Folder.All, custom: Long? = null) =
        categoryMenu(counts, listOf(work, committee), mapOf(1L to 3), selected, custom, colours)

    @Test
    fun `the lines come in order - built-in folders, the user categories, Spam, Manage`() {
        val m = menu()
        assertEquals(
            listOf(Folder.All, Folder.Personal, Folder.Banks, Folder.Insurance, Folder.Codes, Folder.Deliveries),
            m.filterIsInstance<CategoryEntry.BuiltIn>().map { it.folder },
        )
        assertEquals(listOf(work, committee), m.filterIsInstance<CategoryEntry.Custom>().map { it.folder })
        assertEquals(CategoryEntry.Spam(5), m[m.size - 2])
        assertEquals(CategoryEntry.Manage, m.last())
        // Spam is only its own line, never among the folders
        assertTrue(m.none { it is CategoryEntry.BuiltIn && it.folder == Folder.Spam })
    }

    @Test
    fun `every line carries its count and colour`() {
        val m = menu()
        val builtIn = m.filterIsInstance<CategoryEntry.BuiltIn>().associateBy { it.folder }
        assertEquals(31, builtIn.getValue(Folder.All).count)
        assertEquals(4, builtIn.getValue(Folder.Personal).count)
        assertEquals(0, builtIn.getValue(Folder.Insurance).count)
        assertEquals(0xFF2F80EDL, builtIn.getValue(Folder.Personal).argb)
        val custom = m.filterIsInstance<CategoryEntry.Custom>().associateBy { it.folder.id }
        assertEquals(3, custom.getValue(1).count)
        assertEquals(0, custom.getValue(2).count)
    }

    @Test
    fun `exactly one line is chosen, and the button shows it`() {
        val m = menu(selected = Folder.Banks)
        assertEquals(CategoryEntry.BuiltIn(Folder.Banks, 2, null, selected = true), m.current())
        assertEquals(1, m.count { (it as? CategoryEntry.BuiltIn)?.selected == true || (it as? CategoryEntry.Custom)?.selected == true })
    }

    @Test
    fun `a category of the user's is chosen instead of any folder`() {
        val m = menu(custom = 1)
        assertEquals(CategoryEntry.Custom(work, 3, selected = true), m.current())
        assertTrue(m.filterIsInstance<CategoryEntry.BuiltIn>().none { it.selected })
    }

    @Test
    fun `a deleted category of the user's falls back to All`() {
        val m = menu(custom = 99)
        assertEquals(Folder.All, (m.current() as CategoryEntry.BuiltIn).folder)
    }

    @Test
    fun `choosing a folder or a category selects it in place`() {
        val m = menu()
        var folder: Folder? = null
        var custom: CustomFolder? = null
        var managed = false
        m.first { it is CategoryEntry.BuiltIn && it.folder == Folder.Codes }.action().dispatch({ folder = it }, { custom = it }, { managed = true })
        assertEquals(Folder.Codes, folder)
        m.first { it is CategoryEntry.Custom && it.folder == committee }.action().dispatch({ folder = it }, { custom = it }, { managed = true })
        assertEquals(committee, custom)
        assertEquals(false, managed)
    }

    @Test
    fun `Spam opens the spam screen, as its chip did`() {
        var folder: Folder? = null
        val action = menu().first { it is CategoryEntry.Spam }.action()
        assertEquals(CategoryAction.OpenSpam, action)
        action.dispatch({ folder = it }, {}, {})
        // The inbox route opens the spam screen for Folder.Spam (and never makes it the chosen folder)
        assertEquals(Folder.Spam, folder)
    }

    @Test
    fun `Manage categories opens the categories screen`() {
        var managed = false
        val action = menu().last().action()
        assertEquals(CategoryAction.ManageCategories, action)
        action.dispatch({ error("not a folder") }, { error("not a category") }, { managed = true })
        assertTrue(managed)
    }

    @Test
    fun `every line has its own icon - the categories icon for All, then one per category`() {
        val icons = menu().associate { entry ->
            val key = when (entry) {
                is CategoryEntry.BuiltIn -> entry.folder.name
                is CategoryEntry.Custom -> "custom"
                is CategoryEntry.Spam -> "spam"
                CategoryEntry.Manage -> "manage"
            }
            key to entry.icon
        }
        assertSame(Icons.Rounded.Category, icons.getValue("All"))
        assertSame(Icons.Rounded.Person, icons.getValue("Personal"))
        assertSame(Icons.Rounded.AccountBalance, icons.getValue("Banks"))
        assertSame(Icons.Rounded.HealthAndSafety, icons.getValue("Insurance"))
        assertSame(Icons.Rounded.Key, icons.getValue("Codes"))
        assertSame(Icons.Rounded.LocalShipping, icons.getValue("Deliveries"))
        assertSame(Icons.AutoMirrored.Rounded.Label, icons.getValue("custom"))
        assertSame(Icons.Rounded.Warning, icons.getValue("spam"))
        assertSame(Icons.Rounded.Tune, icons.getValue("manage"))
    }

    @Test
    fun `Settings and the menu use the same icons for the same categories`() {
        assertSame(Icons.Rounded.Category, categoryIcon(Folder.All))
        Folder.entries.filter { it != Folder.All && it != Folder.Spam }.forEach { f ->
            assertSame(f.name, menu().filterIsInstance<CategoryEntry.BuiltIn>().first { it.folder == f }.icon, categoryIcon(f))
        }
        assertSame(CustomCategoryIcon, menu().filterIsInstance<CategoryEntry.Custom>().first().icon)
    }
}
