package com.havivon.pigeon.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.havivon.pigeon.domain.model.SmsAccess

/**
 * What the app may do right now: its [SmsAccess] (default app, viewer, none) and the optional
 * permissions around it. Read in one place ([readAppAccess]) and kept by `AppContainer.access`,
 * so every screen and repository sees the same answer.
 */
data class AppAccess(
    val sms: SmsAccess,
    /** SEND_SMS: needed to send while another app is the default (the role includes it). */
    val canSend: Boolean,
    val contacts: Boolean,
    val notifications: Boolean,
)

fun readAppAccess(context: Context): AppAccess {
    val isDefault = DefaultSmsRole.isDefault(context)
    return AppAccess(
        sms = SmsAccess.of(isDefault, canReadSms = granted(context, Manifest.permission.READ_SMS)),
        canSend = isDefault || granted(context, Manifest.permission.SEND_SMS),
        contacts = granted(context, Manifest.permission.READ_CONTACTS),
        notifications = Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS),
    )
}

/**
 * The runtime permissions "Just view my messages" asks for: reading SMS (the one viewer mode
 * needs), sending, contact names and reminder notifications. Nothing that receives SMS: the default
 * app keeps receiving and notifying, so nothing is ever notified twice.
 */
fun viewerPermissions(): Array<String> = listOfNotNull(
    Manifest.permission.READ_SMS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.READ_CONTACTS,
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null,
).toTypedArray()

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
