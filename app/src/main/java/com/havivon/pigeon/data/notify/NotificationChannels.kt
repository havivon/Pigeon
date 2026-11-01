package com.havivon.pigeon.data.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.havivon.pigeon.R

/**
 * The notification channels. Android lets the user tune each channel (sound, vibration), and a
 * channel's lock-screen visibility, once created, belongs to the user.
 */
object NotificationChannels {
    const val MESSAGES = "messages"

    /**
     * Messages while "message text on the lock screen" is off. A notification's own
     * VISIBILITY_PRIVATE only hides the text when the phone's lock-screen setting already hides
     * sensitive content; a channel created with VISIBILITY_PRIVATE hides it whatever that setting
     * is. So the setting switches channels, and this one is only created once it is first needed.
     */
    const val MESSAGES_HIDDEN = "messages_hidden"

    const val REMINDERS = "reminders"

    /** Reminders while "message text on the lock screen" is off, for the same reason as [MESSAGES_HIDDEN]. */
    const val REMINDERS_HIDDEN = "reminders_hidden"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(MESSAGES, context.getString(R.string.notification_channel_messages), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(REMINDERS, context.getString(R.string.notification_channel_reminders), NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    /** The channel for a message notification, creating the hidden-text one when it is first needed. */
    fun forMessages(context: Context, lockScreenText: Boolean): String {
        if (lockScreenText) return MESSAGES
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(MESSAGES_HIDDEN) == null) {
            val channel = NotificationChannel(MESSAGES_HIDDEN, context.getString(R.string.notification_channel_messages_hidden), NotificationManager.IMPORTANCE_HIGH)
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            manager.createNotificationChannel(channel)
        }
        return MESSAGES_HIDDEN
    }

    /** The channel for a reminder notification, creating the hidden-text one when it is first needed. */
    fun forReminders(context: Context, lockScreenText: Boolean): String {
        if (lockScreenText) return REMINDERS
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(REMINDERS_HIDDEN) == null) {
            val channel = NotificationChannel(REMINDERS_HIDDEN, context.getString(R.string.notification_channel_reminders_hidden), NotificationManager.IMPORTANCE_HIGH)
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            manager.createNotificationChannel(channel)
        }
        return REMINDERS_HIDDEN
    }
}
