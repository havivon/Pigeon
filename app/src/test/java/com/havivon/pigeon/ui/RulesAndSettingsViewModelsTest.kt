package com.havivon.pigeon.ui

import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.AppTheme
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.fakes.FakeBlockedKeywords
import com.havivon.pigeon.fakes.FakeRules
import com.havivon.pigeon.fakes.FakeSettings
import com.havivon.pigeon.fakes.MainDispatcherRule
import com.havivon.pigeon.ui.keywords.BlockedKeywordsViewModel
import com.havivon.pigeon.ui.keywords.MAX_KEYWORD_LENGTH
import com.havivon.pigeon.ui.rules.MAX_RULE_LABEL
import com.havivon.pigeon.ui.rules.MAX_RULE_TEXT
import com.havivon.pigeon.ui.rules.RuleKind
import com.havivon.pigeon.ui.rules.RuleSwatches
import com.havivon.pigeon.ui.rules.RulesViewModel
import com.havivon.pigeon.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

@OptIn(ExperimentalCoroutinesApi::class)
class RulesViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val repo = FakeRules(
        listOf(
            ColorRule(1, "Bank", RuleMatch.Sender("Bank"), RuleSwatches[0]),
            ColorRule(2, "Deliveries", RuleMatch.Keyword("delivery"), RuleSwatches[1]),
            ColorRule(3, "Mom", RuleMatch.Sender("Mom"), RuleSwatches[2], enabled = false),
        ),
    )

    private fun TestScope.collect(flow: Flow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
    }

    private fun TestScope.vm() = RulesViewModel(repo).also { collect(it.rules) }

    @Test
    fun `a new rule starts with the first colour no rule uses`() = runTest {
        val vm = vm()
        vm.startNew()
        val e = vm.editor.value!!
        assertNull(e.editingId)
        assertEquals(RuleKind.Sender, e.kind)
        assertEquals(RuleSwatches[3], e.argb)
        assertFalse(e.canSave)
    }

    @Test
    fun `saving a new sender rule adds it at the end`() = runTest {
        val vm = vm()
        vm.startNew()
        vm.setText("  Leumi ")
        vm.setColor(RuleSwatches[6])
        vm.save()
        assertNull(vm.editor.value)
        val added = repo.state.value.last()
        assertEquals(ColorRule(4, "Leumi", RuleMatch.Sender("Leumi"), RuleSwatches[6], enabled = true), added)
        assertEquals(listOf(1, 2, 3, 4), vm.rules.value.map { it.id })
    }

    @Test
    fun `a keyword rule uses its own tag when given, otherwise the word`() = runTest {
        val vm = vm()
        vm.startNew()
        vm.setKind(RuleKind.Keyword)
        vm.setText("invoice")
        vm.save()
        assertEquals(ColorRule(4, "invoice", RuleMatch.Keyword("invoice"), RuleSwatches[3]), repo.state.value.last())

        vm.startNew()
        vm.setKind(RuleKind.Keyword)
        vm.setText("invoice")
        vm.setLabel("Bills")
        vm.save()
        assertEquals("Bills", repo.state.value.last().label)
    }

    @Test
    fun `a blank word cannot be saved`() = runTest {
        val vm = vm()
        vm.startNew()
        vm.setText("   ")
        vm.save()
        // The editor stays open and nothing is added
        assertNotNull(vm.editor.value)
        assertEquals(3, repo.state.value.size)
    }

    @Test
    fun `texts are cut to their limits`() = runTest {
        val vm = vm()
        vm.startNew()
        vm.setText("x".repeat(500))
        vm.setLabel("y".repeat(500))
        assertEquals(MAX_RULE_TEXT, vm.editor.value!!.text.length)
        assertEquals(MAX_RULE_LABEL, vm.editor.value!!.label.length)
        assertEquals(MAX_RULE_LABEL, vm.editor.value!!.tag.length)
    }

    @Test
    fun `editing loads the rule, and saving keeps its place and its switch`() = runTest {
        val vm = vm()
        val mom = repo.state.value.first { it.id == 3 }
        vm.startEdit(mom)
        val e = vm.editor.value!!
        assertEquals(3, e.editingId)
        assertEquals("Mom", e.text)
        // The tag is the word itself, so it keeps following the word
        assertEquals("", e.label)
        assertFalse(e.enabled)
        vm.setText("Mother")
        vm.setColor(RuleSwatches[5])
        vm.save()
        val saved = repo.state.value.first { it.id == 3 }
        assertEquals(ColorRule(3, "Mother", RuleMatch.Sender("Mother"), RuleSwatches[5], enabled = false), saved)
        assertEquals(listOf(1, 2, 3), repo.state.value.map { it.id })
    }

    @Test
    fun `editing keeps a tag that differs from the word, and the kind can change`() = runTest {
        val vm = vm()
        vm.startEdit(repo.state.value.first { it.id == 2 })
        assertEquals(RuleKind.Keyword, vm.editor.value!!.kind)
        assertEquals("Deliveries", vm.editor.value!!.label)
        vm.setKind(RuleKind.Sender)
        vm.save()
        assertEquals(RuleMatch.Sender("delivery"), repo.state.value.first { it.id == 2 }.match)
        assertEquals("Deliveries", repo.state.value.first { it.id == 2 }.label)
    }

    @Test
    fun `deleting removes the rule being edited and closes the editor`() = runTest {
        val vm = vm()
        vm.startEdit(repo.state.value.first { it.id == 2 })
        vm.deleteEditing()
        assertNull(vm.editor.value)
        assertEquals(listOf(1, 3), repo.state.value.map { it.id })
    }

    @Test
    fun `a new rule has nothing to delete`() = runTest {
        val vm = vm()
        vm.startNew()
        vm.deleteEditing()
        assertEquals(3, repo.state.value.size)
        assertNotNull(vm.editor.value)
    }

    @Test
    fun `dismissing the editor changes nothing`() = runTest {
        val vm = vm()
        vm.startEdit(repo.state.value.first())
        vm.setText("Changed")
        vm.dismissEditor()
        assertNull(vm.editor.value)
        assertEquals("Bank", (repo.state.value.first().match as RuleMatch.Sender).name)
    }

    @Test
    fun `the switch turns a rule on or off`() = runTest {
        val vm = vm()
        vm.setEnabled(repo.state.value.first { it.id == 3 }, true)
        assertTrue(repo.state.value.first { it.id == 3 }.enabled)
        vm.setEnabled(repo.state.value.first { it.id == 1 }, false)
        assertFalse(repo.state.value.first { it.id == 1 }.enabled)
    }

    @Test
    fun `reordering puts the rules in the dragged order`() = runTest {
        val vm = vm()
        vm.reorder(listOf(3, 1, 2))
        assertEquals(listOf(listOf(3, 1, 2)), repo.reordered)
        assertEquals(listOf(3, 1, 2), vm.rules.value.map { it.id })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BlockedKeywordsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val repo = FakeBlockedKeywords(listOf("loan"))

    private fun TestScope.vm() = BlockedKeywordsViewModel(repo).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.keywords.collect { } }
    }

    @Test
    fun `a typed word is added trimmed, and the field is cleared`() = runTest {
        val vm = vm()
        vm.setDraft("  prize ")
        vm.add()
        assertEquals(listOf("loan", "prize"), vm.keywords.value.map { it.word })
        assertEquals("", vm.draft.value)
        assertFalse(vm.duplicate.value)
    }

    @Test
    fun `a blank word is ignored`() = runTest {
        val vm = vm()
        vm.setDraft("   ")
        vm.add()
        assertEquals(1, vm.keywords.value.size)
        assertFalse(vm.duplicate.value)
    }

    @Test
    fun `a word already on the list is refused, whatever its case, and stays in the field`() = runTest {
        val vm = vm()
        vm.setDraft("LOAN")
        vm.add()
        assertEquals(1, vm.keywords.value.size)
        assertTrue(vm.duplicate.value)
        assertEquals("LOAN", vm.draft.value)
        // Typing again clears the complaint
        vm.setDraft("LOANS")
        assertFalse(vm.duplicate.value)
    }

    @Test
    fun `the word is cut to its limit`() = runTest {
        val vm = vm()
        vm.setDraft("x".repeat(500))
        assertEquals(MAX_KEYWORD_LENGTH, vm.draft.value.length)
    }

    @Test
    fun `a word can be removed`() = runTest {
        val vm = vm()
        vm.delete(vm.keywords.value.single())
        assertTrue(vm.keywords.value.isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettings()
    private val rules = FakeRules(listOf(ColorRule(1, "Bank", RuleMatch.Sender("Bank"), RuleSwatches[0])))
    private val keywords = FakeBlockedKeywords(listOf("loan", "prize"))
    private val appliedIcons = mutableListOf<AppIcon>()

    private fun TestScope.vm() = SettingsViewModel(settings, rules, keywords, AppIcon.Envelope) { appliedIcons += it }.also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    @Test
    fun `the state has the settings and the counts for the rows`() = runTest {
        val vm = vm()
        assertEquals(AppSettings(), vm.state.value.settings)
        assertEquals(1, vm.state.value.ruleCount)
        assertEquals(2, vm.state.value.keywordCount)
        assertEquals(AppIcon.Envelope, vm.state.value.appIcon)
    }

    @Test
    fun `the counts follow the rules and the words`() = runTest {
        val vm = vm()
        rules.add("Mom", RuleMatch.Sender("Mom"), RuleSwatches[1])
        keywords.add("win")
        assertEquals(2, vm.state.value.ruleCount)
        assertEquals(3, vm.state.value.keywordCount)
    }

    @Test
    fun `language and theme are stored`() = runTest {
        val vm = vm()
        vm.setLanguage(AppLanguage.Hebrew)
        vm.setTheme(AppTheme.Dark)
        assertEquals(AppLanguage.Hebrew, settings.state.value.language)
        assertEquals(AppTheme.Dark, settings.state.value.theme)
        assertEquals(AppLanguage.Hebrew, vm.state.value.settings.language)
        assertEquals(AppTheme.Dark, vm.state.value.settings.theme)
    }

    @Test
    fun `each switch writes its own setting and nothing else`() = runTest {
        val vm = vm()
        vm.setLockScreenText(false)
        assertEquals(AppSettings(lockScreenText = false), settings.state.value)
        vm.setWidgetsShowText(false)
        vm.setBlockScreenshots(true)
        vm.setAppLock(true)
        vm.setAutoDeleteCodes(false)
        assertEquals(
            AppSettings(lockScreenText = false, widgetsShowText = false, blockScreenshots = true, appLock = true, autoDeleteCodes = false),
            settings.state.value,
        )
    }

    @Test
    fun `choosing an icon switches the launcher entry and is remembered`() = runTest {
        val vm = vm()
        vm.setAppIcon(AppIcon.Bubble)
        assertEquals(listOf(AppIcon.Bubble), appliedIcons)
        assertEquals(AppIcon.Bubble, vm.state.value.appIcon)
        assertEquals(AppIcon.Bubble, settings.state.value.appIcon)
    }
}
