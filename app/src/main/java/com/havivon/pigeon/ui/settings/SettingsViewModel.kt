package com.havivon.pigeon.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.AppTheme
import com.havivon.pigeon.domain.repository.BlockedKeywordsRepository
import com.havivon.pigeon.domain.repository.FoldersRepository
import com.havivon.pigeon.domain.repository.RulesRepository
import com.havivon.pigeon.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val appIcon: AppIcon = AppIcon.Envelope,
    /** For the "Color rules" row: how many the user has. */
    val ruleCount: Int = 0,
    /** For the "Blocked words" row. */
    val keywordCount: Int = 0,
    /** For the "Categories" row: how many of their own the user made. */
    val folderCount: Int = 0,
)

/**
 * The settings screen. Each switch writes its setting at once; the places that depend on a setting
 * read it from the same repository (the shell for screenshots and app lock, the notifier for the
 * lock-screen text, the widgets, the code cleanup), so nothing else has to be told.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    rules: RulesRepository,
    keywords: BlockedKeywordsRepository,
    folders: FoldersRepository,
    currentIcon: AppIcon,
    /** Switches the launcher entry (`AppIconSwitcher`). */
    private val applyIcon: (AppIcon) -> Unit,
) : ViewModel() {

    private val icon = MutableStateFlow(currentIcon)

    val state: StateFlow<SettingsUiState> = combine(settings.settings, icon, rules.rules, keywords.keywords, folders.folders) { s, i, r, k, f ->
        SettingsUiState(s, i, r.size, k.size, f.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(appIcon = currentIcon))

    fun setLanguage(value: AppLanguage) = launch { settings.setLanguage(value) }
    fun setTheme(value: AppTheme) = launch { settings.setTheme(value) }
    fun setLockScreenText(value: Boolean) = launch { settings.setLockScreenText(value) }
    fun setWidgetsShowText(value: Boolean) = launch { settings.setWidgetsShowText(value) }
    fun setBlockScreenshots(value: Boolean) = launch { settings.setBlockScreenshots(value) }
    fun setAutoDeleteCodes(value: Boolean) = launch { settings.setAutoDeleteCodes(value) }

    /** The screen asks for the fingerprint or screen lock first (see `SettingsRoute`), so this only stores the answer. */
    fun setAppLock(value: Boolean) = launch { settings.setAppLock(value) }

    fun setAppIcon(value: AppIcon) {
        applyIcon(value)
        icon.value = value
        launch { settings.setAppIcon(value) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
