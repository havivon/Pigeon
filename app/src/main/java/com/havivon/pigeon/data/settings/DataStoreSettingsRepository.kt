package com.havivon.pigeon.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.migrations.SharedPreferencesView
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.AppTheme
import com.havivon.pigeon.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The DataStore keys. Renaming one loses the user's value; add a migration instead. */
internal object SettingsKeys {
    val LANGUAGE = stringPreferencesKey("language")
    val THEME = stringPreferencesKey("theme")
    val APP_ICON = stringPreferencesKey("app_icon")
    val LOCK_SCREEN_TEXT = booleanPreferencesKey("lock_screen_text")
    val WIDGETS_SHOW_TEXT = booleanPreferencesKey("widgets_show_text")
    val BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
    val APP_LOCK = booleanPreferencesKey("app_lock")
    val AUTO_DELETE_CODES = booleanPreferencesKey("auto_delete_codes")
    val AUTO_DELETE_CODES_SINCE = longPreferencesKey("auto_delete_codes_since")
    val WELCOME_DONE = booleanPreferencesKey("welcome_done")
    val PERMISSIONS_ASKED = booleanPreferencesKey("permissions_asked")
}

/** Reads the settings, with the defaults of [AppSettings] for anything never set (or unknown). */
internal fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        language = this[SettingsKeys.LANGUAGE]?.let { v -> AppLanguage.entries.firstOrNull { it.name == v } } ?: defaults.language,
        theme = this[SettingsKeys.THEME]?.let { v -> AppTheme.entries.firstOrNull { it.name == v } } ?: defaults.theme,
        appIcon = this[SettingsKeys.APP_ICON]?.let { v -> AppIcon.entries.firstOrNull { it.name == v } } ?: defaults.appIcon,
        lockScreenText = this[SettingsKeys.LOCK_SCREEN_TEXT] ?: defaults.lockScreenText,
        widgetsShowText = this[SettingsKeys.WIDGETS_SHOW_TEXT] ?: defaults.widgetsShowText,
        blockScreenshots = this[SettingsKeys.BLOCK_SCREENSHOTS] ?: defaults.blockScreenshots,
        appLock = this[SettingsKeys.APP_LOCK] ?: defaults.appLock,
        autoDeleteCodes = this[SettingsKeys.AUTO_DELETE_CODES] ?: defaults.autoDeleteCodes,
        autoDeleteCodesSinceMillis = this[SettingsKeys.AUTO_DELETE_CODES_SINCE],
        welcomeDone = this[SettingsKeys.WELCOME_DONE] ?: defaults.welcomeDone,
        permissionsAsked = this[SettingsKeys.PERMISSIONS_ASKED] ?: defaults.permissionsAsked,
    )
}

/**
 * Copies a value the preview stored in SharedPreferences into the DataStore. Only a value that
 * was really stored is copied, so a never-set one keeps its default.
 */
internal fun migrateBoolean(current: Preferences, key: Preferences.Key<Boolean>, stored: Boolean?): Preferences =
    if (stored == null) current else current.toMutablePreferences().apply { this[key] = stored }

/** The SharedPreferences files and keys the design preview used, before there was a DataStore. */
private const val LEGACY_WIDGET_FILE = "widget_settings"
private const val LEGACY_WIDGET_SHOW_CONTENT = "show_content"
private const val LEGACY_ONBOARDING_FILE = "onboarding"
private const val LEGACY_WELCOME_DONE = "welcome_done"

private fun SharedPreferencesView.booleanOrNull(key: String): Boolean? = if (contains(key)) getBoolean(key, false) else null

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, LEGACY_WIDGET_FILE, setOf(LEGACY_WIDGET_SHOW_CONTENT)) { prefs: SharedPreferencesView, current: Preferences ->
                migrateBoolean(current, SettingsKeys.WIDGETS_SHOW_TEXT, prefs.booleanOrNull(LEGACY_WIDGET_SHOW_CONTENT))
            },
            SharedPreferencesMigration(context, LEGACY_ONBOARDING_FILE, setOf(LEGACY_WELCOME_DONE)) { prefs: SharedPreferencesView, current: Preferences ->
                migrateBoolean(current, SettingsKeys.WELCOME_DONE, prefs.booleanOrNull(LEGACY_WELCOME_DONE))
            },
        )
    },
)

/** [SettingsRepository] on a Preferences DataStore in the app's private files. */
class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val store = context.applicationContext.settingsStore

    override val settings: Flow<AppSettings> = store.data.map { it.toAppSettings() }

    override suspend fun setLanguage(value: AppLanguage) = put(SettingsKeys.LANGUAGE, value.name)
    override suspend fun setTheme(value: AppTheme) = put(SettingsKeys.THEME, value.name)
    override suspend fun setAppIcon(value: AppIcon) = put(SettingsKeys.APP_ICON, value.name)
    override suspend fun setLockScreenText(value: Boolean) = put(SettingsKeys.LOCK_SCREEN_TEXT, value)
    override suspend fun setWidgetsShowText(value: Boolean) = put(SettingsKeys.WIDGETS_SHOW_TEXT, value)
    override suspend fun setBlockScreenshots(value: Boolean) = put(SettingsKeys.BLOCK_SCREENSHOTS, value)
    override suspend fun setAppLock(value: Boolean) = put(SettingsKeys.APP_LOCK, value)
    override suspend fun setWelcomeDone(value: Boolean) = put(SettingsKeys.WELCOME_DONE, value)
    override suspend fun setPermissionsAsked(value: Boolean) = put(SettingsKeys.PERMISSIONS_ASKED, value)

    /** Turning it on starts the count again from now; turning it off forgets when it started. */
    override suspend fun setAutoDeleteCodes(value: Boolean) {
        store.edit {
            if (value && it[SettingsKeys.AUTO_DELETE_CODES] == false) it[SettingsKeys.AUTO_DELETE_CODES_SINCE] = System.currentTimeMillis()
            if (!value) it.remove(SettingsKeys.AUTO_DELETE_CODES_SINCE)
            it[SettingsKeys.AUTO_DELETE_CODES] = value
        }
    }

    override suspend fun setAutoDeleteCodesSince(millis: Long?) {
        store.edit { if (millis == null) it.remove(SettingsKeys.AUTO_DELETE_CODES_SINCE) else it[SettingsKeys.AUTO_DELETE_CODES_SINCE] = millis }
    }

    override suspend fun restoreBackedUp(values: AppSettings) {
        store.edit {
            it[SettingsKeys.LANGUAGE] = values.language.name
            it[SettingsKeys.THEME] = values.theme.name
            it[SettingsKeys.APP_ICON] = values.appIcon.name
            it[SettingsKeys.LOCK_SCREEN_TEXT] = values.lockScreenText
            it[SettingsKeys.WIDGETS_SHOW_TEXT] = values.widgetsShowText
            it[SettingsKeys.BLOCK_SCREENSHOTS] = values.blockScreenshots
            it[SettingsKeys.APP_LOCK] = values.appLock
            it[SettingsKeys.AUTO_DELETE_CODES] = values.autoDeleteCodes
            val since = values.autoDeleteCodesSinceMillis
            if (since == null) it.remove(SettingsKeys.AUTO_DELETE_CODES_SINCE) else it[SettingsKeys.AUTO_DELETE_CODES_SINCE] = since
        }
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }
}
