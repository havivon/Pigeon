package com.havivon.pigeon.ui.lock

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.app.Authenticator

/** A success counts for this long, so unlocking the app and then a locked chat asks only once. */
private const val REUSE_MS = 10_000L

/**
 * [Authenticator] with the system's BiometricPrompt: fingerprint or face, or the phone's PIN,
 * pattern or password (BIOMETRIC_WEAK or DEVICE_CREDENTIAL, which every Android from 9 offers).
 */
class BiometricAuthenticator(private val activity: FragmentActivity) : Authenticator {

    private var lastSuccess = 0L

    /** True while the prompt is up: the activity may stop and start under it (the PIN screen is its own activity). */
    var prompting = false
        private set

    override fun available(): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    override fun authenticate(onResult: (Boolean) -> Unit) {
        if (SystemClock.elapsedRealtime() - lastSuccess < REUSE_MS) {
            onResult(true)
            return
        }
        // No screen lock and no biometrics: there is nothing to check the owner against
        if (!available()) {
            onResult(true)
            return
        }
        if (prompting) return
        prompting = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    lastSuccess = SystemClock.elapsedRealtime()
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                    onResult(false)
                }
                // onAuthenticationFailed (one wrong finger) keeps the prompt open: nothing to do
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_prompt_title))
            .setSubtitle(activity.getString(R.string.lock_prompt_subtitle))
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: RuntimeException) {
            // The activity is going away: the prompt cannot show, so nothing opens
            prompting = false
            onResult(false)
        }
    }

    /** Forgets the last success: after the app was away, it asks again. */
    fun expire() {
        lastSuccess = 0L
    }
}
