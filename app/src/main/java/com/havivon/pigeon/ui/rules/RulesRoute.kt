package com.havivon.pigeon.ui.rules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.data.AppIconSwitcher
import com.havivon.pigeon.ui.navigation.pigeonViewModel

/** The colours-and-rules screen on real data: rules on/off and the launcher icon. */
@Composable
fun RulesRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = pigeonViewModel { app ->
        val appContext = context.applicationContext
        RulesViewModel(app.rules, app.settings, AppIconSwitcher.current(appContext)) { AppIconSwitcher.set(appContext, it) }
    }
    val rules by vm.rules.collectAsStateWithLifecycle()
    val icon by vm.appIcon.collectAsStateWithLifecycle()
    RulesScreen(
        rules = rules,
        newRuleWord = "",
        appIcon = icon,
        onAppIcon = vm::setAppIcon,
        onBack = onBack,
        onToggle = vm::setEnabled,
        hideUnfinished = true,
    )
}
