package com.havivon.pigeon.data.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMappingTest {

    @Test
    fun `defaults match Google Messages`() {
        val s = emptyPreferences().toAppSettings()
        assertEquals(AppSettings(), s)
        assertEquals(AppLanguage.System, s.language)
        assertEquals(AppTheme.System, s.theme)
        assertEquals(AppIcon.Envelope, s.appIcon)
        assertTrue(s.lockScreenText)
        assertTrue(s.widgetsShowText)
        assertFalse(s.blockScreenshots)
        assertFalse(s.appLock)
        assertFalse(s.autoDeleteCodes)
        assertNull(s.autoDeleteCodesSinceMillis)
        assertFalse(s.welcomeDone)
        assertTrue("Mark as spam blocks on the phone, as in Google Messages", s.blockSpamOnPhone)
        assertTrue("codes are copied for the keyboard", s.autoCopyCodes)
    }

    @Test
    fun `stored values are read`() {
        val prefs = mutablePreferencesOf(
            SettingsKeys.LANGUAGE to "Hebrew",
            SettingsKeys.THEME to "Dark",
            SettingsKeys.APP_ICON to "Bubble",
            SettingsKeys.LOCK_SCREEN_TEXT to false,
            SettingsKeys.WIDGETS_SHOW_TEXT to false,
            SettingsKeys.BLOCK_SCREENSHOTS to true,
            SettingsKeys.APP_LOCK to true,
            SettingsKeys.AUTO_DELETE_CODES to false,
            SettingsKeys.AUTO_DELETE_CODES_SINCE to 1234L,
            SettingsKeys.WELCOME_DONE to true,
            SettingsKeys.PERMISSIONS_ASKED to true,
            SettingsKeys.BLOCK_SPAM_ON_PHONE to false,
            SettingsKeys.AUTO_COPY_CODES to false,
        )
        assertEquals(
            AppSettings(
                language = AppLanguage.Hebrew,
                theme = AppTheme.Dark,
                appIcon = AppIcon.Bubble,
                lockScreenText = false,
                widgetsShowText = false,
                blockScreenshots = true,
                appLock = true,
                autoDeleteCodes = false,
                autoDeleteCodesSinceMillis = 1234L,
                welcomeDone = true,
                permissionsAsked = true,
                blockSpamOnPhone = false,
                autoCopyCodes = false,
            ),
            prefs.toAppSettings(),
        )
    }

    @Test
    fun `an unknown stored choice falls back to the default`() {
        val prefs = mutablePreferencesOf(SettingsKeys.LANGUAGE to "Klingon", SettingsKeys.APP_ICON to "Gone", SettingsKeys.THEME to "Sepia")
        assertEquals(AppLanguage.System, prefs.toAppSettings().language)
        assertEquals(AppIcon.Envelope, prefs.toAppSettings().appIcon)
        assertEquals(AppTheme.System, prefs.toAppSettings().theme)
    }

    @Test
    fun `the preview's widget setting carries over`() {
        val migrated = migrateBoolean(emptyPreferences(), SettingsKeys.WIDGETS_SHOW_TEXT, stored = false)
        assertFalse(migrated.toAppSettings().widgetsShowText)
    }

    @Test
    fun `a value the preview never stored keeps the default`() {
        val migrated = migrateBoolean(emptyPreferences(), SettingsKeys.WELCOME_DONE, stored = null)
        assertEquals(emptyPreferences(), migrated)
    }
}
