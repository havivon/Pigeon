package com.havivon.pigeon.ui.settings

import androidx.compose.ui.unit.dp
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.fakes.FakeSettings
import com.havivon.pigeon.fakes.MainDispatcherRule
import com.havivon.pigeon.ui.theme.Skin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkinPickerViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `no card is checked before the stored skin is read`() {
        val vm = SkinPickerViewModel(FakeSettings(AppSettings(skin = "forest")))
        assertNull(vm.current.value)
    }

    @Test
    fun `the stored skin is the checked one, and a pick is stored and checked at once`() = runTest {
        val settings = FakeSettings(AppSettings(skin = "forest"))
        val vm = SkinPickerViewModel(settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.current.collect { } }
        assertEquals(Skin.Forest, vm.current.value)
        vm.pick(Skin.Graphite)
        assertEquals("graphite", settings.state.value.skin)
        assertEquals(Skin.Graphite, vm.current.value)
    }

    @Test
    fun `an unknown stored skin shows Sunset checked`() = runTest {
        val vm = SkinPickerViewModel(FakeSettings(AppSettings(skin = "neon")))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.current.collect { } }
        assertEquals(Skin.Sunset, vm.current.value)
    }

    @Test
    fun `two columns on a phone, wider cards when the font is very large`() {
        assertEquals(140.dp, skinCardMinWidth(1f))
        assertEquals(140.dp, skinCardMinWidth(1.3f))
        assertEquals(220.dp, skinCardMinWidth(1.8f))
    }
}
