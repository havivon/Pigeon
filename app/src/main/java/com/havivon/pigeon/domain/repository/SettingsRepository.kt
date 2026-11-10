package com.havivon.pigeon.domain.repository

import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.AppTheme
import kotlinx.coroutines.flow.Flow

/** The user's settings, kept in the app's private storage. */
interface SettingsRepository {

    val settings: Flow<AppSettings>

    suspend fun setLanguage(value: AppLanguage)
    suspend fun setTheme(value: AppTheme)

    /** Stores a skin's id; an unknown one is stored as the default (Sunset). */
    suspend fun setSkin(id: String)
    suspend fun setAppIcon(value: AppIcon)
    suspend fun setLockScreenText(value: Boolean)
    suspend fun setWidgetsShowText(value: Boolean)
    suspend fun setBlockScreenshots(value: Boolean)
    suspend fun setAppLock(value: Boolean)
    suspend fun setAutoDeleteCodes(value: Boolean)
    suspend fun setAutoDeleteCodesSince(millis: Long?)
    suspend fun setBlockSpamOnPhone(value: Boolean)
    suspend fun setAutoCopyCodes(value: Boolean)
    suspend fun setWelcomeDone(value: Boolean)
    suspend fun setPermissionsAsked(value: Boolean)

    /**
     * Writes the settings an imported file covers, in one step: everything but the welcome and permission flags,
     * which belong to this phone. [AppSettings.autoDeleteCodesSinceMillis] is written as given.
     */
    suspend fun restoreBackedUp(values: AppSettings)
}
