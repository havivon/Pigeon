package com.havivon.pigeon.ui.common

import com.havivon.pigeon.domain.model.AppLanguage
import java.util.Locale

/**
 * Keeps the two places a language can be chosen from disagreeing.
 *
 * Android 13+ has a per-app language in the system settings (made possible by the app's locale
 * config), next to Pigeon's own language setting. Both are the user's choice, so the latest one
 * wins: when the app starts and the language picked in the system settings differs from the
 * language stored in the app's settings, the system's choice is adopted into the stored setting
 * (and the app then simply runs in it). Without this the stored setting would put the app's
 * language back as soon as the user left the system settings, undoing what they just picked.
 *
 * Below Android 13 only the in-app setting exists, so nothing is adopted.
 */
object LanguageSync {

    /** The first Android version with a per-app language in the system settings. */
    const val SYSTEM_PER_APP_LANGUAGE_SDK = 33

    /**
     * The language to store, or null to leave the stored one as it is. [systemChoice] is the
     * first locale of the per-app locales the system holds (null or empty means "follow the phone").
     * A language the app does not offer is ignored.
     */
    fun adopt(stored: AppLanguage, systemChoice: Locale?, sdk: Int): AppLanguage? {
        if (sdk < SYSTEM_PER_APP_LANGUAGE_SDK) return null
        val system = languageOf(systemChoice) ?: return null
        return system.takeIf { it != stored }
    }

    /** The app language a locale stands for. Hebrew is "iw" or "he" depending on the Android version. */
    fun languageOf(locale: Locale?): AppLanguage? = when (locale?.language) {
        null, "" -> AppLanguage.System
        "iw", "he" -> AppLanguage.Hebrew
        "en" -> AppLanguage.English
        else -> null
    }
}
