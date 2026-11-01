package com.havivon.pigeon.ui.app

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import com.havivon.pigeon.domain.model.PendingDeletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Short messages at the bottom of the screen, shown by the app shell so they outlive the screen
 * that asked (deleting a conversation goes back to the inbox, and its undo shows there).
 */
class Notices(private val host: SnackbarHostState, private val scope: CoroutineScope, private val undoLabel: String) {

    fun show(message: String) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    /** "Deleted · Undo". The snackbar is shorter than the undo window, so Undo always works while it shows. */
    fun undo(message: String, deletion: PendingDeletion) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) deletion.undo()
        }
    }
}

/** Checks that the person holding the phone is its owner: fingerprint, face or the screen lock. */
interface Authenticator {
    /** Whether the phone has anything to check against (a screen lock or enrolled biometrics). */
    fun available(): Boolean

    /** Asks, unless the owner proved it a moment ago; [onResult] true on success. Without a screen lock it succeeds at once. */
    fun authenticate(onResult: (Boolean) -> Unit)
}

val LocalNotices = staticCompositionLocalOf<Notices> { error("No Notices provided") }

val LocalAuthenticator = staticCompositionLocalOf<Authenticator> {
    object : Authenticator {
        override fun available() = false
        override fun authenticate(onResult: (Boolean) -> Unit) = onResult(true)
    }
}

/** Whether Pigeon is the default SMS app, and the request to make it so (from the gate in the shell). */
data class DefaultAppAccess(val isDefault: Boolean, val makeDefault: () -> Unit)

val LocalDefaultApp = staticCompositionLocalOf { DefaultAppAccess(isDefault = true, makeDefault = {}) }
