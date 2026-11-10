package com.havivon.pigeon

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.LaunchRequests
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.AppTheme
import com.havivon.pigeon.ui.app.PigeonShell
import com.havivon.pigeon.ui.common.LanguageSync
import com.havivon.pigeon.ui.common.appLocale
import com.havivon.pigeon.ui.lock.BiometricAuthenticator
import com.havivon.pigeon.ui.navigation.Navigator
import com.havivon.pigeon.ui.theme.HebSmsTheme
import com.havivon.pigeon.ui.theme.Skin
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's one activity. An AppCompatActivity, for BiometricPrompt (locked chats, app lock) and
 * the per-app language. What it was opened for (a draft from an `sms:` link, a notification's
 * conversation) is parsed and held in [LaunchRequests] for the screens.
 */
class MainActivity : AppCompatActivity() {

    private companion object {
        // The opacity of the scrims enableEdgeToEdge() uses by default behind a three-button navigation bar; the colour is the skin's page
        const val LIGHT_SCRIM_ALPHA = 0xe6
        const val DARK_SCRIM_ALPHA = 0x80

        /** How long the first frame may wait for the stored theme and skin before it is drawn with the defaults. */
        const val STARTUP_READ_MS = 300L
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
        val settings = AppContainer.from(this).settings
        // The first frame is drawn in the user's theme and skin, never Sunset's for a moment: read them first (DataStore
        // answers in a few milliseconds; the defaults if it does not)
        val startup = runBlocking { withTimeoutOrNull(STARTUP_READ_MS) { settings.settings.first() } } ?: AppSettings()
        val startLook = Look(startup.theme, Skin.fromId(startup.skin))
        // The window under the first frame (Theme.Pigeon's is Sunset's page) takes the skin's page colour
        window.setBackgroundDrawable(ColorDrawable(startLook.skin.colors(startLook.theme.isDark(systemDark())).background.toArgb()))
        val looks = settings.settings.map { Look(it.theme, Skin.fromId(it.skin)) }.distinctUntilChanged()
        setContent {
            val look by looks.collectAsState(initial = startLook)
            val dark = look.theme.isDark(isSystemInDarkTheme())
            val colors = look.skin.colors(dark)
            // The system bars' icons follow the app's theme, not only the phone's; the scrim and the window follow the skin
            DisposableEffect(dark, look.skin) {
                val page = colors.background.toArgb()
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(ColorUtils.setAlphaComponent(page, LIGHT_SCRIM_ALPHA), ColorUtils.setAlphaComponent(page, DARK_SCRIM_ALPHA)) { dark },
                )
                window.setBackgroundDrawable(ColorDrawable(page))
                onDispose { }
            }
            HebSmsTheme(darkTheme = dark, skin = look.skin) { PigeonShell(navigator, authenticator) }
        }
    }

    /** What the screens are drawn in: the light/dark choice and the colour skin. */
    private data class Look(val theme: AppTheme, val skin: Skin)

    private fun AppTheme.isDark(systemDark: Boolean) = when (this) {
        AppTheme.System -> systemDark
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }

    private fun systemDark() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

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
