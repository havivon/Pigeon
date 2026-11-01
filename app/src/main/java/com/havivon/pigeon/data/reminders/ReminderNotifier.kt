package com.havivon.pigeon.data.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.havivon.pigeon.MainActivity
import com.havivon.pigeon.R
import com.havivon.pigeon.data.LaunchRequests
import com.havivon.pigeon.data.notify.NotificationChannels
import com.havivon.pigeon.domain.model.Reminder
import com.havivon.pigeon.domain.reminders.ReminderPlan
import com.havivon.pigeon.domain.reminders.ReminderTimes
import com.havivon.pigeon.domain.reminders.reminderTexts
import com.havivon.pigeon.domain.repository.ContactsRepository
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.RemindersRepository
import com.havivon.pigeon.domain.repository.SettingsRepository
import com.havivon.pigeon.domain.seam.ReminderScheduler
import kotlinx.coroutines.flow.first

private const val TAG_REMINDER = "reminder"

/** The app's violet, as in the message notifications. */
private const val ACCENT = 0xFF5B3FD9.toInt()

/**
 * What happens when a reminder is due: its notification (the message text, the sender, a tap opens
 * the conversation, "Done" and "Snooze 1 hour" buttons), those two buttons, and putting the alarms
 * back after they were lost.
 *
 * A reminder is marked done the moment it is shown, so it fires once: swiping the notification away
 * does not bring it back after a reboot. Snoozing makes a new reminder an hour later.
 * Nothing of a locked conversation shows, and the lock-screen setting picks the channel (see
 * [NotificationChannels.forReminders]).
 */
class ReminderNotifier(
    private val context: Context,
    private val reminders: RemindersRepository,
    private val messages: MessageRepository,
    private val contacts: ContactsRepository,
    private val locks: LocksRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** The alarm went off: show the reminder, once. */
    suspend fun fire(reminderId: Long) {
        val reminder = reminders.get(reminderId) ?: return
        if (reminder.done) return
        reminders.setDone(reminderId, true)
        post(reminder)
    }

    /** "Done": the notification goes away. */
    suspend fun done(reminderId: Long) {
        reminders.setDone(reminderId, true)
        cancelNotification(reminderId)
    }

    /** "Snooze 1 hour": the same reminder again in an hour. */
    suspend fun snooze(reminderId: Long) {
        val reminder = reminders.get(reminderId) ?: return
        reminders.setDone(reminderId, true)
        cancelNotification(reminderId)
        val id = reminders.add(reminder.threadId, reminder.messageId, reminder.text, ReminderTimes.snoozedUntil(now()))
        reminders.get(id)?.let(scheduler::schedule)
    }

    /** After a reboot, an update or a clock change: the alarms again, and the ones that were missed shown now. */
    suspend fun restoreAll() {
        val plan = ReminderPlan.afterRestart(reminders.allPending(), now())
        plan.schedule.forEach(scheduler::schedule)
        plan.fireNow.forEach { fire(it.id) }
    }

    private fun cancelNotification(reminderId: Long) {
        manager.cancel(TAG_REMINDER, notificationId(reminderId))
    }

    private suspend fun post(reminder: Reminder) {
        if (!manager.areNotificationsEnabled()) return
        val s = settings.settings.first()
        val locked = locks.isLocked(reminder.threadId)
        val address = if (locked) null else messages.recentMessages(reminder.threadId, 1).lastOrNull()?.address
        val sender = address?.let { contacts.displayName(it) ?: it }
        val texts = reminderTexts(
            reminderText = reminder.text,
            senderName = sender,
            locked = locked,
            lockedTitle = context.getString(R.string.notification_locked_title),
            generic = context.getString(R.string.reminder_notification_title),
        )
        val channel = NotificationChannels.forReminders(context, s.lockScreenText)
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.reminder_notification_title))
            .build()
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentTitle(texts.title)
            .setContentText(texts.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texts.text))
            .setWhen(reminder.triggerAtMillis)
            .setContentIntent(openThread(reminder.threadId))
            .addAction(0, context.getString(R.string.reminder_done), ReminderReceiver.doneIntent(context, reminder.id))
            .addAction(0, context.getString(R.string.reminder_snooze), ReminderReceiver.snoozeIntent(context, reminder.id))
        try {
            manager.notify(TAG_REMINDER, notificationId(reminder.id), builder.build())
        } catch (_: SecurityException) {
            // Notification permission withdrawn between the check and the post
        }
    }

    /** The same intent a message notification uses, so a tap lands in the conversation. */
    private fun openThread(threadId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("pigeon://thread/$threadId"))
            .putExtra(LaunchRequests.EXTRA_THREAD_ID, threadId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        fun notificationId(reminderId: Long): Int = (reminderId xor (reminderId ushr 32)).toInt()
    }
}
