package com.havivon.pigeon.domain.repository

import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/** The user's settings, kept in the app's private storage. */
interface SettingsRepository {

    val settings: Flow<AppSettings>

    suspend fun setLanguage(value: AppLanguage)
    suspend fun setAppIcon(value: AppIcon)
    suspend fun setLockScreenText(value: Boolean)
    suspend fun setWidgetsShowText(value: Boolean)
    suspend fun setBlockScreenshots(value: Boolean)
    suspend fun setAppLock(value: Boolean)
    suspend fun setAutoDeleteCodes(value: Boolean)
    suspend fun setAutoDeleteCodesSince(millis: Long?)
    suspend fun setWelcomeDone(value: Boolean)
    suspend fun setPermissionsAsked(value: Boolean)
}
