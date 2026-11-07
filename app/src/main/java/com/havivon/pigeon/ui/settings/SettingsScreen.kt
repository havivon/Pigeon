package com.havivon.pigeon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppTheme
import com.havivon.pigeon.domain.model.SmsAccess
import com.havivon.pigeon.ui.components.FixedHeaderLayout
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.components.SectionLabel
import com.havivon.pigeon.ui.components.listBottomPadding
import com.havivon.pigeon.ui.theme.HebTheme

/** What the phone says, which the settings screen shows next to the user's own choices. */
data class SettingsEnvironment(
    /** Whether the phone has a screen lock or biometrics to lock the app with. */
    val canLock: Boolean = true,
    val contactsAllowed: Boolean = true,
    val notificationsAllowed: Boolean = true,
    /** Null on phones before Android 12, where exact alarms need no permission. */
    val exactAlarmsAllowed: Boolean? = null,
    /** Default app, viewing only, or no access: the "Messaging app" row says which, with the action that fits. */
    val smsAccess: SmsAccess = SmsAccess.Default,
    val versionName: String = "",
)

/** Everything a row in the settings can do. */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onLanguage: (AppLanguage) -> Unit = {},
    val onTheme: (AppTheme) -> Unit = {},
    val onAppIcon: (AppIcon) -> Unit = {},
    val onOpenRules: () -> Unit = {},
    val onOpenKeywords: () -> Unit = {},
    val onOpenFolders: () -> Unit = {},
    val onOpenBlockedNumbers: () -> Unit = {},
    val onBlockSpamOnPhone: (Boolean) -> Unit = {},
    val onAutoCopyCodes: (Boolean) -> Unit = {},
    val onOpenRecycleBin: () -> Unit = {},
    val onExportSettings: () -> Unit = {},
    val onImportSettings: () -> Unit = {},
    val onAutoDeleteCodes: (Boolean) -> Unit = {},
    val onLockScreenText: (Boolean) -> Unit = {},
    val onSystemNotifications: () -> Unit = {},
    val onAllowExactAlarms: () -> Unit = {},
    val onBlockScreenshots: (Boolean) -> Unit = {},
    val onAppLock: (Boolean) -> Unit = {},
    val onContactsPermission: () -> Unit = {},
    val onNotificationsPermission: () -> Unit = {},
    val onWidgetsShowText: (Boolean) -> Unit = {},
    val onDefaultApp: () -> Unit = {},
    /** "Just view": asks for the permissions to read the messages while another app stays the default. */
    val onViewOnly: () -> Unit = {},
)

/** The settings, in sections: look, messages, notifications, privacy, widgets, the default app, about. */
@Composable
fun SettingsScreen(state: SettingsUiState, env: SettingsEnvironment, actions: SettingsActions) {
    val c = HebTheme.colors
    val s = state.settings
    FixedHeaderLayout(
        Modifier.fillMaxSize().background(c.background),
        header = {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), actions.onBack)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_title), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(R.string.settings_subtitle), color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp)
                    }
                }
            }
        },
    ) { top ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = top, bottom = listBottomPadding(32.dp)),
        ) {
            item { SectionLabel(stringResource(R.string.settings_section_appearance), Icons.Rounded.Palette) }
            item {
                SettingsCard {
                    ChoiceRow(
                        Icons.Rounded.Language,
                        stringResource(R.string.settings_language),
                        listOf(
                            stringResource(R.string.settings_by_phone) to AppLanguage.System,
                            stringResource(R.string.settings_language_hebrew) to AppLanguage.Hebrew,
                            stringResource(R.string.settings_language_english) to AppLanguage.English,
                        ),
                        s.language,
                        actions.onLanguage,
                    )
                    RowDivider()
                    ChoiceRow(
                        Icons.Rounded.DarkMode,
                        stringResource(R.string.settings_theme),
                        listOf(
                            stringResource(R.string.settings_by_phone) to AppTheme.System,
                            stringResource(R.string.settings_theme_light) to AppTheme.Light,
                            stringResource(R.string.settings_theme_dark) to AppTheme.Dark,
                        ),
                        s.theme,
                        actions.onTheme,
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.rules_app_icon)) }
            item { AppIconPicker(state.appIcon, actions.onAppIcon) }

            item { SectionLabel(stringResource(R.string.settings_section_messages), Icons.Rounded.Sms) }
            item {
                SettingsCard {
                    NavRow(
                        Icons.Rounded.Palette,
                        stringResource(R.string.settings_color_rules),
                        pluralStringResource(R.plurals.settings_rules_count, state.ruleCount, state.ruleCount),
                        actions.onOpenRules,
                    )
                    RowDivider()
                    NavRow(
                        Icons.Rounded.Block,
                        stringResource(R.string.settings_blocked_words),
                        pluralStringResource(R.plurals.settings_words_count, state.keywordCount, state.keywordCount),
                        actions.onOpenKeywords,
                    )
                    RowDivider()
                    NavRow(
                        Icons.Rounded.PhoneDisabled,
                        stringResource(R.string.settings_blocked_numbers),
                        when (val n = state.blockedNumberCount) {
                            null -> stringResource(R.string.settings_blocked_numbers_unavailable)
                            0 -> stringResource(R.string.settings_blocked_numbers_none)
                            else -> pluralStringResource(R.plurals.settings_blocked_numbers_count, n, n)
                        },
                        actions.onOpenBlockedNumbers,
                    )
                    RowDivider()
                    SwitchRow(
                        Icons.Rounded.GppBad,
                        stringResource(R.string.settings_block_on_phone),
                        stringResource(R.string.settings_block_on_phone_note),
                        s.blockSpamOnPhone,
                        onChange = actions.onBlockSpamOnPhone,
                    )
                    RowDivider()
                    NavRow(
                        Icons.Rounded.Category,
                        stringResource(R.string.settings_folders),
                        if (state.folderCount == 0) stringResource(R.string.settings_folders_none)
                        else pluralStringResource(R.plurals.settings_folders_count, state.folderCount, state.folderCount),
                        actions.onOpenFolders,
                    )
                    RowDivider()
                    NavRow(
                        Icons.Rounded.RestoreFromTrash,
                        stringResource(R.string.settings_recycle_bin),
                        stringResource(R.string.settings_recycle_bin_note),
                        actions.onOpenRecycleBin,
                    )
                    RowDivider()
                    SwitchRow(
                        Icons.Rounded.AutoDelete,
                        stringResource(R.string.settings_auto_delete),
                        stringResource(R.string.settings_auto_delete_note),
                        s.autoDeleteCodes,
                        onChange = actions.onAutoDeleteCodes,
                    )
                    RowDivider()
                    SwitchRow(
                        Icons.Rounded.ContentPaste,
                        stringResource(R.string.settings_auto_copy),
                        stringResource(R.string.settings_auto_copy_note),
                        s.autoCopyCodes,
                        onChange = actions.onAutoCopyCodes,
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_section_notifications), Icons.Rounded.Notifications) }
            item {
                SettingsCard {
                    SwitchRow(
                        Icons.Rounded.ScreenLockPortrait,
                        stringResource(R.string.settings_lock_screen_text),
                        stringResource(R.string.settings_lock_screen_text_note),
                        s.lockScreenText,
                        onChange = actions.onLockScreenText,
                    )
                    RowDivider()
                    NavRow(
                        Icons.Rounded.Tune,
                        stringResource(R.string.settings_system_notifications),
                        stringResource(R.string.settings_system_notifications_note),
                        actions.onSystemNotifications,
                    )
                    if (env.exactAlarmsAllowed == false) {
                        RowDivider()
                        NavRow(
                            Icons.Rounded.Alarm,
                            stringResource(R.string.settings_exact_alarms),
                            stringResource(R.string.settings_exact_alarms_note),
                            actions.onAllowExactAlarms,
                        )
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.settings_section_privacy), Icons.Rounded.Shield) }
            item {
                SettingsCard {
                    SwitchRow(
                        Icons.Rounded.PhoneAndroid,
                        stringResource(R.string.settings_block_screenshots),
                        stringResource(R.string.settings_block_screenshots_note),
                        s.blockScreenshots,
                        onChange = actions.onBlockScreenshots,
                    )
                    RowDivider()
                    // Turning it off is always possible, even if the phone's screen lock was removed since
                    SwitchRow(
                        Icons.Rounded.Fingerprint,
                        stringResource(R.string.settings_app_lock),
                        stringResource(if (env.canLock || s.appLock) R.string.settings_app_lock_note else R.string.settings_app_lock_unavailable),
                        s.appLock,
                        enabled = env.canLock || s.appLock,
                        onChange = actions.onAppLock,
                    )
                    RowDivider()
                    PermissionRow(
                        Icons.Rounded.Contacts,
                        stringResource(R.string.settings_permission_contacts),
                        stringResource(R.string.settings_permission_contacts_note),
                        env.contactsAllowed,
                        actions.onContactsPermission,
                    )
                    RowDivider()
                    PermissionRow(
                        Icons.Rounded.Notifications,
                        stringResource(R.string.settings_permission_notifications),
                        stringResource(R.string.settings_permission_notifications_note),
                        env.notificationsAllowed,
                        actions.onNotificationsPermission,
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_section_widgets), Icons.Rounded.Widgets) }
            item {
                SettingsCard {
                    SwitchRow(
                        Icons.Rounded.Widgets,
                        stringResource(R.string.settings_widgets_text),
                        stringResource(R.string.settings_widgets_text_note),
                        s.widgetsShowText,
                        onChange = actions.onWidgetsShowText,
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_section_backup), Icons.Rounded.Backup) }
            item {
                SettingsCard {
                    NavRow(
                        Icons.Rounded.FileUpload,
                        stringResource(R.string.settings_backup_export),
                        stringResource(R.string.settings_backup_export_note),
                        actions.onExportSettings,
                    )
                    RowDivider()
                    NavRow(
                        Icons.Rounded.FileDownload,
                        stringResource(R.string.settings_backup_import),
                        stringResource(R.string.settings_backup_import_note),
                        actions.onImportSettings,
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_section_default), Icons.Rounded.Sms) }
            item { DefaultAppCard(env.smsAccess, actions.onDefaultApp, actions.onViewOnly) }

            item { SectionLabel(stringResource(R.string.settings_section_about)) }
            item {
                SettingsCard {
                    InfoRow(Icons.Rounded.PhoneAndroid, stringResource(R.string.app_name), stringResource(R.string.settings_version, env.versionName))
                    RowDivider()
                    InfoRow(Icons.Rounded.Lock, stringResource(R.string.settings_no_internet_title), stringResource(R.string.settings_no_internet))
                }
            }
            // The credit every one of the user's apps carries, as in HebReminder
            item {
                Text(
                    stringResource(R.string.credit_line),
                    color = HebTheme.colors.onSurfaceMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/** A white rounded card holding a group of rows. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 3.dp, shape, spotColor = c.accent.copy(alpha = 0.3f))
            .clip(shape)
            .background(c.surface),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    Box(Modifier.padding(start = 68.dp).fillMaxWidth().height(1.dp).background(HebTheme.colors.divider))
}

/** The round accent icon at the start of a row. */
@Composable
private fun RowIcon(icon: ImageVector, tint: Color = HebTheme.colors.accent) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RowText(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    val c = HebTheme.colors
    Column(modifier) {
        Text(title, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, color = c.onSurfaceMuted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

/** A row that opens another screen or a system page. */
@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        RowText(title, subtitle, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = HebTheme.colors.onSurfaceMuted)
    }
}

/** A row with an on/off switch; the whole row toggles it. */
@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = HebTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .then(if (enabled) Modifier.clickable { onChange(!checked) } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        RowText(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White),
        )
    }
}

/** A permission: its state, and a tap that asks for it (or opens the app's settings once it was refused for good). */
@Composable
private fun PermissionRow(icon: ImageVector, title: String, note: String, allowed: Boolean, onClick: () -> Unit) {
    val c = HebTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        RowText(title, note, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        if (allowed) {
            Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.settings_permission_allowed), tint = Color(0xFF1E9E6A))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.settings_permission_allowed), color = c.onSurfaceMuted, fontSize = 13.sp)
        } else {
            PillButton(stringResource(R.string.settings_permission_allow), onClick = onClick)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        RowText(title, text, Modifier.weight(1f))
    }
}

/** A label with a few options as one segmented pill: the chosen one is filled with the header gradient. */
@Composable
private fun <T> ChoiceRow(icon: ImageVector, title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    val c = HebTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon)
            Spacer(Modifier.width(14.dp))
            Text(title, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.surfaceVariant).padding(3.dp)) {
            options.forEach { (label, value) ->
                val on = value == selected
                Text(
                    label,
                    color = if (on) Color.White else c.onSurfaceMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .then(if (on) Modifier.background(c.header) else Modifier)
                        .clickable { onSelect(value) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * What Pigeon may do with the messages (the messaging app, viewing only, no access), and the
 * button that goes further: "Change" for the default app, "Make default" otherwise, and with no
 * access also "Just view".
 */
@Composable
private fun DefaultAppCard(access: SmsAccess, onClick: () -> Unit, onViewOnly: () -> Unit) {
    val isDefault = access == SmsAccess.Default
    val (title, note) = when (access) {
        SmsAccess.Default -> R.string.settings_default_yes to R.string.settings_default_yes_note
        SmsAccess.Viewer -> R.string.settings_default_viewer to R.string.settings_default_viewer_note
        SmsAccess.None -> R.string.settings_default_none to R.string.settings_default_none_note
    }
    SettingsCard {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            val icon = when (access) {
                SmsAccess.Default -> Icons.Rounded.Check
                SmsAccess.Viewer -> Icons.Rounded.Visibility
                SmsAccess.None -> Icons.Rounded.Sms
            }
            RowIcon(icon, if (isDefault) Color(0xFF1E9E6A) else HebTheme.colors.accent)
            Spacer(Modifier.width(14.dp))
            RowText(stringResource(title), stringResource(note), Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PillButton(
                    stringResource(if (isDefault) R.string.settings_default_change else R.string.settings_default_make),
                    filled = !isDefault,
                    onClick = onClick,
                )
                if (access == SmsAccess.None) {
                    Spacer(Modifier.height(6.dp))
                    PillButton(stringResource(R.string.settings_default_view_only), onClick = onViewOnly)
                }
            }
        }
    }
}
