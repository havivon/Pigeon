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
     * Whether the app may ask to become the default SMS app. The default app is the only one Android
     * delivers new messages to, so this is true only because the app now handles them: the
     * SMS_DELIVER receiver stores every incoming SMS in the system database before anything else
     * (`SmsDeliverReceiver`, `IncomingSmsHandler`), sending writes to the Outbox and follows the
     * radio's result (`TelephonySmsSender`), and the manifest has every component the role requires
     * (SMS_DELIVER, WAP_PUSH_DELIVER, RESPOND_VIA_MESSAGE, SENDTO). Set it back to false if any of
     * that is ever taken out: while it is false the app never shows the prompt or fires the request.
     */
    const val SMS_HANDLING_READY = true

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
