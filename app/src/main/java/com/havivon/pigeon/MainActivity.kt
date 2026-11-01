package com.havivon.pigeon

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.LaunchRequests
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppTheme
import com.havivon.pigeon.ui.app.PigeonShell
import com.havivon.pigeon.ui.common.LanguageSync
import com.havivon.pigeon.ui.common.appLocale
import com.havivon.pigeon.ui.lock.BiometricAuthenticator
import com.havivon.pigeon.ui.navigation.Navigator
import com.havivon.pigeon.ui.theme.HebSmsTheme
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The app's one activity. An AppCompatActivity, for BiometricPrompt (locked chats, app lock) and
 * the per-app language. What it was opened for (a draft from an `sms:` link, a notification's
 * conversation) is parsed and held in [LaunchRequests] for the screens.
 */
class MainActivity : AppCompatActivity() {

    private companion object {
        // The scrims enableEdgeToEdge() uses by default behind a three-button navigation bar
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }

    private val navigator: Navigator by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After a rotation the intent was already handled
        if (savedInstanceState == null) LaunchRequests.handle(intent)
        enableEdgeToEdge()
        // The ViewModels hold labels in the language they were built in
        navigator.ensureLanguage(appLocale(this).toLanguageTag())
        val authenticator = BiometricAuthenticator(this)
        applyLanguageSetting()
        val themes = AppContainer.from(this).settings.settings.map { it.theme }.distinctUntilChanged()
        setContent {
            val theme by themes.collectAsState(initial = AppTheme.System)
            val dark = when (theme) {
                AppTheme.System -> isSystemInDarkTheme()
                AppTheme.Light -> false
                AppTheme.Dark -> true
            }
            // The system bars' icons follow the app's theme, not only the phone's
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose { }
            }
            HebSmsTheme(darkTheme = dark) { PigeonShell(navigator, authenticator) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LaunchRequests.handle(intent)
    }

    /**
     * The language chosen in the app's settings (system, Hebrew or English), applied now and whenever
     * it changes. Before that, [LanguageSync] adopts a choice made in the system's per-app language
     * settings (Android 13+), so whichever of the two the user touched last wins.
     */
    private fun applyLanguageSetting() {
        val settings = AppContainer.from(this).settings
        lifecycleScope.launch {
            LanguageSync.adopt(settings.settings.first().language, AppCompatDelegate.getApplicationLocales()[0], Build.VERSION.SDK_INT)
                ?.let { settings.setLanguage(it) }
            settings.settings.map { it.language }.distinctUntilChanged().collect { language ->
                if (languageKey(AppCompatDelegate.getApplicationLocales()[0]) != languageKey(language.tag?.let(Locale::forLanguageTag))) {
                    // Recreates the activity in the new language when it differs
                    AppCompatDelegate.setApplicationLocales(
                        if (language == AppLanguage.System) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language.tag),
                    )
                }
            }
        }
    }

    /** Hebrew is "iw" or "he" depending on the Android version: one key for both. Null is "follow the phone". */
    private fun languageKey(locale: Locale?): String? = locale?.language?.let { if (it == "he") "iw" else it }
}
