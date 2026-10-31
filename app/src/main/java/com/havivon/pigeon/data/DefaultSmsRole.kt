package com.havivon.pigeon.data

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony

/** Whether Pigeon is the phone's default SMS app, and the system screens that change it. */
object DefaultSmsRole {

    /**
     * Whether the app may ask to become the default SMS app. It must not before it has the
     * SMS_DELIVER receiver that stores incoming messages: the default app is the only one Android
     * delivers new messages to, so without the receiver the user's real incoming SMS would be lost.
     * Flip to true in the SMS stage, together with the manifest components Android requires of a
     * default SMS app. While it is false the running app never shows the prompt and never fires
     * the request; the prompt screens only exist, and are rendered in the design snapshots.
     */
    const val SMS_HANDLING_READY = false

    fun isDefault(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }

    /** The system's request to make Pigeon the default SMS app, or null if this phone offers no such request. */
    fun requestIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_SMS)) roles.createRequestRoleIntent(RoleManager.ROLE_SMS) else null
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }

    /**
     * The system's default-apps screen. Used when the system no longer shows its own dialog: after
     * the user has refused it, Android 10+ can return RESULT_CANCELED at once without showing anything.
     */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
}
