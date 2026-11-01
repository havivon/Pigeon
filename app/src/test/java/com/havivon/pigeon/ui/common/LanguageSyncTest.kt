package com.havivon.pigeon.ui.common

import com.havivon.pigeon.domain.model.AppLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageSyncTest {

    private val hebrew = Locale.forLanguageTag("iw")
    private val english = Locale.ENGLISH

    @Test
    fun `a language picked in the system settings is adopted when it differs`() {
        assertEquals(AppLanguage.English, LanguageSync.adopt(AppLanguage.Hebrew, english, sdk = 33))
        assertEquals(AppLanguage.Hebrew, LanguageSync.adopt(AppLanguage.System, hebrew, sdk = 34))
        assertEquals(AppLanguage.Hebrew, LanguageSync.adopt(AppLanguage.English, hebrew, sdk = 33))
    }

    @Test
    fun `switching back to the phone's language in the system settings is adopted too`() {
        assertEquals(AppLanguage.System, LanguageSync.adopt(AppLanguage.Hebrew, null, sdk = 33))
        assertEquals(AppLanguage.System, LanguageSync.adopt(AppLanguage.English, Locale.ROOT, sdk = 33))
    }

    @Test
    fun `nothing changes when both agree`() {
        assertNull(LanguageSync.adopt(AppLanguage.Hebrew, hebrew, sdk = 33))
        assertNull(LanguageSync.adopt(AppLanguage.English, english, sdk = 33))
        assertNull(LanguageSync.adopt(AppLanguage.System, null, sdk = 33))
    }

    @Test
    fun `Hebrew is iw or he depending on the Android version`() {
        assertEquals(AppLanguage.Hebrew, LanguageSync.languageOf(Locale.forLanguageTag("he")))
        assertEquals(AppLanguage.Hebrew, LanguageSync.languageOf(Locale.forLanguageTag("iw")))
        assertNull(LanguageSync.adopt(AppLanguage.Hebrew, Locale.forLanguageTag("he-IL"), sdk = 34))
    }

    @Test
    fun `below Android 13 only the in-app setting exists`() {
        assertNull(LanguageSync.adopt(AppLanguage.Hebrew, english, sdk = 32))
        assertNull(LanguageSync.adopt(AppLanguage.English, hebrew, sdk = 26))
    }

    @Test
    fun `a language the app does not offer is ignored`() {
        assertNull(LanguageSync.languageOf(Locale.FRENCH))
        assertNull(LanguageSync.adopt(AppLanguage.Hebrew, Locale.FRENCH, sdk = 33))
    }
}
