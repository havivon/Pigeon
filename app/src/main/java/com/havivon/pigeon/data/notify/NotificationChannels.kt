package com.havivon.pigeon.data.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.havivon.pigeon.R

/**
 * The notification channels. Android lets the user tune each channel (sound, vibration), and a
 * channel's lock-screen visibility and app-icon badge, once created, belong to the user. So each
 * setting that needs another value gets its own channel, created when first needed with the user's
 * sound and vibration copied from the one it replaces, and the one no longer used is deleted (Android
 * brings it back, with its own settings, if it is needed again).
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

    /** Messages while "number on the app icon" is off (the default): the same, without a badge on the launcher icon. */
    const val MESSAGES_NO_BADGE = "messages_nobadge"
    const val MESSAGES_HIDDEN_NO_BADGE = "messages_hidden_nobadge"

    /** The old reminder channels, which put a badge on the icon; reminders now never do (the number counts messages). */
    private const val OLD_REMINDERS = "reminders"
    private const val OLD_REMINDERS_HIDDEN = "reminders_hidden"

    const val REMINDERS = "reminders_nobadge"

    /** Reminders while "message text on the lock screen" is off, for the same reason as [MESSAGES_HIDDEN]. */
    const val REMINDERS_HIDDEN = "reminders_hidden_nobadge"

    /** Every message channel, by (text hidden, badge). */
    internal fun messageChannel(hidden: Boolean, badge: Boolean): String = when {
        hidden && badge -> MESSAGES_HIDDEN
        hidden -> MESSAGES_HIDDEN_NO_BADGE
        badge -> MESSAGES
        else -> MESSAGES_NO_BADGE
    }

    /** Whether [channel] is one of the message channels (not a reminder's). */
    internal fun isMessages(channel: String): Boolean = channel in MESSAGE_CHANNELS

    /** Whether a message channel hides the text on the lock screen. */
    internal fun isHidden(channel: String): Boolean = channel == MESSAGES_HIDDEN || channel == MESSAGES_HIDDEN_NO_BADGE

    /** The same channel with or without the badge. */
    internal fun withBadge(channel: String, badge: Boolean): String = messageChannel(hidden = isHidden(channel), badge = badge)

    private val MESSAGE_CHANNELS = listOf(MESSAGES, MESSAGES_HIDDEN, MESSAGES_NO_BADGE, MESSAGES_HIDDEN_NO_BADGE)

    /** Called on launch: the reminder channels. The message channel is created by the first message, as the settings say. */
    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        forReminders(context, lockScreenText = true)
        // Before the old one goes: the user's sound for hidden reminders is copied from it
        if (manager.getNotificationChannel(OLD_REMINDERS_HIDDEN) != null) forReminders(context, lockScreenText = false)
        val busy = activeChannels(manager)
        for (old in listOf(OLD_REMINDERS, OLD_REMINDERS_HIDDEN)) {
            if (old !in busy && manager.getNotificationChannel(old) != null) manager.deleteNotificationChannel(old)
        }
    }

    /** The channel for a message notification, creating it when it is first needed. */
    fun forMessages(context: Context, lockScreenText: Boolean, badge: Boolean): String {
        val id = messageChannel(hidden = !lockScreenText, badge = badge)
        ensure(
            context, id,
            nameRes = if (lockScreenText) R.string.notification_channel_messages else R.string.notification_channel_messages_hidden,
            hidden = !lockScreenText, badge = badge,
            // The user's sound and vibration: from the same channel with the other badge, or from the plain one
            copyFrom = listOf(withBadge(id, !badge), messageChannel(hidden = false, badge = badge), messageChannel(hidden = false, badge = !badge)),
        )
        return id
    }

    /** The channel for a reminder notification, creating it when it is first needed. Never a badge. */
    fun forReminders(context: Context, lockScreenText: Boolean): String {
        val id = if (lockScreenText) REMINDERS else REMINDERS_HIDDEN
        ensure(
            context, id,
            nameRes = if (lockScreenText) R.string.notification_channel_reminders else R.string.notification_channel_reminders_hidden,
            hidden = !lockScreenText, badge = false,
            copyFrom = if (lockScreenText) listOf(OLD_REMINDERS) else listOf(OLD_REMINDERS_HIDDEN, REMINDERS, OLD_REMINDERS),
        )
        return id
    }

    /**
     * Deletes the message channels the settings no longer use, unless a notification is still in one ([busy]: it would
     * go with the channel). In system settings the user then sees one "Messages", not two.
     */
    internal fun deleteUnused(context: Context, inUse: Set<String>, busy: Set<String>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        for (id in MESSAGE_CHANNELS) {
            if (id !in inUse && id !in busy && manager.getNotificationChannel(id) != null) manager.deleteNotificationChannel(id)
        }
    }

    private fun activeChannels(manager: NotificationManager): Set<String> =
        try {
            manager.activeNotifications.mapNotNull { it.notification.channelId }.toSet()
        } catch (_: RuntimeException) {
            emptySet()
        }

    private fun ensure(context: Context, id: String, nameRes: Int, hidden: Boolean, badge: Boolean, copyFrom: List<String>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(id) != null) return
        val source = copyFrom.firstNotNullOfOrNull { manager.getNotificationChannel(it) }
        val channel = NotificationChannel(id, context.getString(nameRes), source?.importance ?: NotificationManager.IMPORTANCE_HIGH)
        if (source != null) {
            channel.setSound(source.sound, source.audioAttributes)
            channel.enableVibration(source.shouldVibrate())
            source.vibrationPattern?.let { channel.vibrationPattern = it }
            channel.enableLights(source.shouldShowLights())
            channel.lightColor = source.lightColor
        }
        if (hidden) channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        channel.setShowBadge(badge)
        manager.createNotificationChannel(channel)
    }
}
