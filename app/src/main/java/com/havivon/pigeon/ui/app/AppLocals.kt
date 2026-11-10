package com.havivon.pigeon.ui.app

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.model.PendingDeletion
import com.havivon.pigeon.domain.model.SmsAccess
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

    /** A message with one action ("Only when Pigeon is your messaging app · Make default"). */
    fun action(message: String, actionLabel: String, onAction: () -> Unit) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(message, actionLabel = actionLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }

    /** "Deleted · Undo". The snackbar is shorter than the undo window, so Undo always works while it shows. */
    fun undo(message: String, deletion: PendingDeletion) = undo(message) { deletion.undo() }

    /** A message with "Undo", which runs [onUndo] when tapped. */
    fun undo(message: String, onUndo: () -> Unit) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onUndo()
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

/**
 * What Pigeon may do with the phone's SMS ([SmsAccess]) and the requests that change it, from the
 * gate in the shell: [makeDefault] asks for the default-app role, [viewOnly] for the permissions to
 * read (and send) while another app stays the default.
 */
data class DefaultAppAccess(
    val mode: SmsAccess,
    val makeDefault: () -> Unit,
    val viewOnly: () -> Unit = {},
    /** Whether the inbox shows the "viewing only" banner (a viewer that has not closed it this session). */
    val showViewerBanner: Boolean = false,
    val dismissViewerBanner: () -> Unit = {},
) {
    val isDefault: Boolean get() = mode == SmsAccess.Default
}

val LocalDefaultApp = staticCompositionLocalOf { DefaultAppAccess(SmsAccess.Default, makeDefault = {}) }

/**
 * For a control that changes the system database (delete, mark read, retry) tapped while Pigeon is
 * not the default SMS app: says it works only then, with "Make default" on the notice. The control
 * stays on the screen, so it always answers.
 */
@Composable
fun rememberNeedsDefaultNotice(): () -> Unit {
    val notices = LocalNotices.current
    val access = LocalDefaultApp.current
    val message = stringResource(R.string.needs_default_notice)
    val label = stringResource(R.string.needs_default_action)
    return remember(notices, access.makeDefault, message, label) { { notices.action(message, label, access.makeDefault) } }
}
