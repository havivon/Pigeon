package com.havivon.pigeon.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.ui.navigation.pigeonViewModel

/** The skin picker on real data, opened from Settings, Appearance. */
@Composable
fun SkinsRoute(onBack: () -> Unit) {
    val vm = pigeonViewModel { app -> SkinPickerViewModel(app.settings) }
    val current by vm.current.collectAsStateWithLifecycle()
    SkinPickerScreen(current, vm::pick, onBack)
}
