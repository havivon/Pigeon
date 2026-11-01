package com.havivon.pigeon

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.LaunchRequests
import com.havivon.pigeon.domain.model.AppLanguage
import com.havivon.pigeon.ui.app.PigeonShell
import com.havivon.pigeon.ui.common.appLocale
import com.havivon.pigeon.ui.lock.BiometricAuthenticator
import com.havivon.pigeon.ui.navigation.Navigator
import com.havivon.pigeon.ui.theme.HebSmsTheme
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The app's one activity. An AppCompatActivity, for BiometricPrompt (locked chats, app lock) and
 * the per-app language. What it was opened for (a draft from an `sms:` link, a notification's
 * conversation) is parsed and held in [LaunchRequests] for the screens.
 */
class MainActivity : AppCompatActivity() {

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
        setContent { HebSmsTheme { PigeonShell(navigator, authenticator) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LaunchRequests.handle(intent)
    }

    /** The language chosen in the app's settings (system, Hebrew or English), applied now and whenever it changes. */
    private fun applyLanguageSetting() {
        val settings = AppContainer.from(this).settings
        lifecycleScope.launch {
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
