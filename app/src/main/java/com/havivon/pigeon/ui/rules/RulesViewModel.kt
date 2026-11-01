package com.havivon.pigeon.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.repository.RulesRepository
import com.havivon.pigeon.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The colours-and-rules screen, reached from the inbox's settings icon. Here: the rules on/off and
 * the launcher icon. Adding, editing and reordering rules come with the settings stage.
 */
class RulesViewModel(
    private val rulesRepository: RulesRepository,
    private val settings: SettingsRepository,
    currentIcon: AppIcon,
    /** Switches the launcher entry (`AppIconSwitcher`). */
    private val applyIcon: (AppIcon) -> Unit,
) : ViewModel() {

    val rules: StateFlow<List<ColorRule>> = rulesRepository.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _appIcon = MutableStateFlow(currentIcon)
    val appIcon: StateFlow<AppIcon> = _appIcon.asStateFlow()

    fun setEnabled(rule: ColorRule, enabled: Boolean) {
        viewModelScope.launch { rulesRepository.update(rule.copy(enabled = enabled)) }
    }

    fun setAppIcon(icon: AppIcon) {
        applyIcon(icon)
        _appIcon.value = icon
        viewModelScope.launch { settings.setAppIcon(icon) }
    }
}
