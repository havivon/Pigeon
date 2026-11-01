package com.havivon.pigeon.data.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.havivon.pigeon.MainActivity
import com.havivon.pigeon.R
import com.havivon.pigeon.data.LaunchRequests
import com.havivon.pigeon.data.sms.NewMessageNotifier
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.repository.ContactsRepository
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.SettingsRepository
import com.havivon.pigeon.domain.seam.CodeFinder
import kotlinx.coroutines.flow.first

/** Messages shown in one notification; older unread ones are summed up in the count. */
private const val MAX_LINES = 8

/** What a notification shows after an inline reply: the last few messages of the conversation. */
private const val REPLIED_LINES = 6

private const val TAG_THREAD = "thread"
private const val TAG_UNSAVED = "unsaved"

/** The app's violet, for the notification's accent. */
private const val ACCENT = 0xFF5B3FD9.toInt()

/**
 * One notification per conversation, in MessagingStyle, with reply, mark-as-read and (when the
 * message has one) copy-code actions.
 *
 * What never shows: anything of a locked conversation (not even the sender, only "Locked
 * conversation" and a count), and with "text on the lock screen" off, any text on the lock screen.
 */
class MessageNotifier(
    private val context: Context,
    private val messages: MessageRepository,
    private val contacts: ContactsRepository,
    private val locks: LocksRepository,
    private val settings: SettingsRepository,
    private val codeFinder: CodeFinder,
    /** Whether the conversation is open on the screen: its messages then show there instead. */
    private val isOnScreen: (Long) -> Boolean = { false },
) : NewMessageNotifier {
    private val manager = NotificationManagerCompat.from(context)

    /** Shows (or refreshes) the conversation's notification with its unread messages, alerting the user. */
    override suspend fun notifyThread(threadId: Long) {
        if (isOnScreen(threadId)) return
        val unread = messages.unreadMessages(threadId)
        if (unread.isEmpty()) {
            cancel(threadId)
            return
        }
        post(threadId, unread.takeLast(MAX_LINES), unreadCount = unread.size, silent = false)
    }

    /** After an inline reply: the reply joins the conversation in the notification, without a new alert. */
    suspend fun showReplied(threadId: Long) {
        val recent = messages.recentMessages(threadId, REPLIED_LINES)
        if (recent.isEmpty()) {
            cancel(threadId)
            return
        }
        post(threadId, recent, unreadCount = 0, silent = true)
    }

    /**
     * A message the system database refused to store (it should never happen while Pigeon is the
     * default SMS app). The notification is then its only copy, so it shows the text, still
     * following the lock-screen setting.
     */
    override suspend fun notifyUnsaved(address: String, body: String, dateMillis: Long) {
        val s = settings.settings.first()
        val title = contacts.displayName(address) ?: address
        val builder = baseBuilder(NotificationChannels.forMessages(context, s.lockScreenText), unreadCount = 1)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setWhen(dateMillis)
            .setContentIntent(openApp(threadId = 0))
        notify(TAG_UNSAVED, address.hashCode(), builder)
    }

    fun cancel(threadId: Long) {
        manager.cancel(TAG_THREAD, notificationId(threadId))
    }

    private suspend fun post(threadId: Long, lines: List<SmsMessage>, unreadCount: Int, silent: Boolean) {
        val s = settings.settings.first()
        val channel = NotificationChannels.forMessages(context, s.lockScreenText)
        val builder = baseBuilder(channel, unreadCount)
            .setSilent(silent)
            .setContentIntent(openApp(threadId))
            .addAction(markReadAction(threadId))

        if (locks.isLocked(threadId)) {
            // Nothing of the conversation: no sender, no text, no reply or code
            builder.setContentTitle(context.getString(R.string.notification_locked_title))
                .setContentText(newMessages(unreadCount.coerceAtLeast(1)))
            notify(TAG_THREAD, notificationId(threadId), builder)
            return
        }

        val address = (lines.lastOrNull { !it.type.outgoing } ?: lines.last()).address
        val title = contacts.displayName(address) ?: address
        val me = Person.Builder().setName(context.getString(R.string.thread_sender_me)).build()
        val sender = Person.Builder().setName(title).setKey(address).build()
        val style = NotificationCompat.MessagingStyle(me)
        for (m in lines) style.addMessage(NotificationCompat.MessagingStyle.Message(m.body, m.dateMillis, if (m.type.outgoing) null else sender))

        builder.setStyle(style)
            .setContentTitle(title)
            .setContentText(lines.last().body)
            .setWhen(lines.last().dateMillis)
            .addAction(replyAction(threadId, address))
        lines.lastOrNull { !it.type.outgoing }?.let { codeFinder.find(it.body) }?.let { builder.addAction(copyCodeAction(threadId, it)) }
        notify(TAG_THREAD, notificationId(threadId), builder)
    }

    /** [unreadCount] 0 (after a reply) shows no count. */
    private fun baseBuilder(channel: String, unreadCount: Int): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setNumber(unreadCount)
            // Hidden on the lock screen when the phone hides sensitive content; the hidden-text channel forces it
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(channel, unreadCount))

    /** What a locked screen shows instead: the app and a count, nothing more. */
    private fun publicVersion(channel: String, unreadCount: Int) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(context.getString(R.string.app_name))
            .apply { if (unreadCount > 0) setContentText(newMessages(unreadCount)) }
            .build()

    private fun newMessages(count: Int) = context.resources.getQuantityString(R.plurals.widget_unread_new_messages, count, count)

    private fun notify(tag: String, id: Int, builder: NotificationCompat.Builder) {
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(tag, id, builder.build())
        } catch (_: SecurityException) {
            // Notification permission withdrawn between the check and the post
        }
    }

    private fun openApp(threadId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("pigeon://thread/$threadId"))
            .putExtra(LaunchRequests.EXTRA_THREAD_ID, threadId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun replyAction(threadId: Long, address: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY)
            .setLabel(context.getString(R.string.notification_reply))
            .build()
        // Mutable, because the system adds the typed text; the target is explicit and not exported
        val intent = NotificationActionReceiver.intent(context, NotificationActionReceiver.ACTION_REPLY, threadId)
            .putExtra(NotificationActionReceiver.EXTRA_ADDRESS, address)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(0, context.getString(R.string.notification_reply), pending)
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            // A stranger holding the locked phone must not send messages as its owner
            .setAuthenticationRequired(true)
            .build()
    }

    private fun markReadAction(threadId: Long): NotificationCompat.Action {
        val intent = NotificationActionReceiver.intent(context, NotificationActionReceiver.ACTION_MARK_READ, threadId)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(0, context.getString(R.string.notification_mark_read), pending)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    private fun copyCodeAction(threadId: Long, code: String): NotificationCompat.Action {
        val intent = NotificationActionReceiver.intent(context, NotificationActionReceiver.ACTION_COPY_CODE, threadId)
            .putExtra(NotificationActionReceiver.EXTRA_CODE, code)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(0, context.getString(R.string.notification_copy_code, code), pending)
            .setShowsUserInterface(false)
            .setAuthenticationRequired(true)
            .build()
    }

    companion object {
        /** A thread's notification id. Thread ids are small, so the cut to Int never collides in practice. */
        fun notificationId(threadId: Long): Int = (threadId xor (threadId ushr 32)).toInt()
    }
}
