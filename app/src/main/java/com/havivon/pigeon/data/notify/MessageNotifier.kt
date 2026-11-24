package com.havivon.pigeon.data.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.havivon.pigeon.MainActivity
import com.havivon.pigeon.R
import com.havivon.pigeon.data.LaunchRequests
import com.havivon.pigeon.data.sms.NewMessageNotifier
import com.havivon.pigeon.domain.model.AppSettings
import com.havivon.pigeon.domain.model.SmsMessage
import com.havivon.pigeon.domain.repository.ContactsRepository
import com.havivon.pigeon.domain.repository.LocksRepository
import com.havivon.pigeon.domain.repository.MessageRepository
import com.havivon.pigeon.domain.repository.SettingsRepository
import com.havivon.pigeon.domain.seam.CodeFinder
import com.havivon.pigeon.ui.theme.Skin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Messages shown in one notification; older unread ones are summed up in the count. */
private const val MAX_LINES = 8

/** What a notification shows after an inline reply: the last few messages of the conversation. */
private const val REPLIED_LINES = 6

private const val TAG_THREAD = "thread"
private const val TAG_UNSAVED = "unsaved"

/** The one quiet "unread messages" notification, see [MessageNotifier]; its own group, so it is never bundled with the messages. */
private const val TAG_QUIET = "unread"
private const val QUIET_ID = 1
private const val QUIET_GROUP = "unread"

/** A notification's own unread count, kept in its extras: [Notification.number] may carry the rest of the badge too. */
private const val EXTRA_OWN = "com.havivon.pigeon.own_unread"


/**
 * One notification per conversation, in MessagingStyle, with reply, mark-as-read and (when the
 * message has one) copy-code actions.
 *
 * What never shows: anything of a locked conversation (not even the sender, only "Locked
 * conversation" and a count), and with "text on the lock screen" off, any text on the lock screen.
 *
 * The number on the app icon (the "number on the app icon" setting, off by default): a launcher sums the numbers of the
 * app's notifications, so with only one conversation's notification it showed that conversation's count. With the
 * setting on, the newest notification also carries the unread messages that have no notification ([unreadTotal], as
 * on the inbox's "All", spam left out), so the sum is all of them. With it off the notifications go to channels without
 * a badge. A launcher shows a number only while a notification is there, so when none is left and messages are still
 * unread, a quiet notification (no sound, only the count, not on the lock screen) carries it ([BadgePlan]). It comes back
 * if swiped away: the user asked for the number until all is read, and turns it off in the settings.
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
    /** Every unread message outside spam, for the number on the app icon. */
    private val unreadTotal: suspend () -> Int = { 0 },
) : NewMessageNotifier {
    private val manager = NotificationManagerCompat.from(context)
    private val system = context.getSystemService(NotificationManager::class.java)

    /** One change of the notifications at a time: each one's number depends on the others'. */
    private val busy = Mutex()

    /**
     * Notifications cancelled here a moment ago: the system removes them a little later, and until then they are still
     * listed as active. Rebalancing the badge must not post them again.
     */
    private val cancelled = java.util.Collections.synchronizedSet(HashSet<String>())

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
        val builder = baseBuilder(NotificationChannels.forMessages(context, s.lockScreenText, s.iconBadge), unreadCount = 1, Skin.fromId(s.skin).notificationArgb)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setWhen(dateMillis)
            .setContentIntent(openApp(threadId = 0))
        busy.withLock { notifyCounted(TAG_UNSAVED, address.hashCode(), builder, own = 1, s) }
    }

    fun cancel(threadId: Long) {
        cancelled += key(TAG_THREAD, notificationId(threadId))
        manager.cancel(TAG_THREAD, notificationId(threadId))
    }

    /**
     * Puts the number on the app icon right, after anything changed it (a message read, the setting turned on or off):
     * the newest notification carries the messages without one, the others their own count, each in the channel the
     * setting asks for.
     */
    suspend fun refreshBadge() = busy.withLock { balance(settings.settings.first(), justPosted = null) }

    private suspend fun post(threadId: Long, lines: List<SmsMessage>, unreadCount: Int, silent: Boolean) = busy.withLock {
        val s = settings.settings.first()
        val channel = NotificationChannels.forMessages(context, s.lockScreenText, s.iconBadge)
        val builder = baseBuilder(channel, unreadCount, Skin.fromId(s.skin).notificationArgb)
            .setSilent(silent)
            .setContentIntent(openApp(threadId))
            .addAction(markReadAction(threadId))

        if (locks.isLocked(threadId)) {
            // Nothing of the conversation: no sender, no text, no reply or code
            builder.setContentTitle(context.getString(R.string.notification_locked_title))
                .setContentText(newMessages(unreadCount.coerceAtLeast(1)))
            notifyCounted(TAG_THREAD, notificationId(threadId), builder, unreadCount, s)
            return@withLock
        }

        val address = (lines.lastOrNull { !it.type.outgoing } ?: lines.last()).address
        val contactName = contacts.displayName(address)
        val title = contactName ?: address
        val me = Person.Builder().setName(context.getString(R.string.thread_sender_me)).build()
        // No contact photos are read: the sender's picture is the whole Pigeon icon, not the grey silhouette
        val appIcon = AppIconBitmap.bitmap(context)
        val sender = Person.Builder().setName(title).setKey(address).setIcon(IconCompat.createWithBitmap(appIcon)).build()
        val style = NotificationCompat.MessagingStyle(me)
        for (m in lines) style.addMessage(NotificationCompat.MessagingStyle.Message(m.body, m.dateMillis, if (m.type.outgoing) null else sender))

        builder.setStyle(style)
            .setContentTitle(title)
            .setContentText(lines.last().body)
            .setWhen(lines.last().dateMillis)
            .addAction(replyAction(threadId, address))
        lines.lastOrNull { !it.type.outgoing }?.let { codeFinder.find(it.body, it.address, knownPerson = contactName != null) }?.let { builder.addAction(copyCodeAction(threadId, it)) }
        notifyCounted(TAG_THREAD, notificationId(threadId), builder, unreadCount, s)
    }

    /**
     * Posts a notification with its own unread count [own]; with the badge on it is the newest, so it also carries the
     * unread messages that have no notification. Then the others are set right (see [balance]).
     */
    private suspend fun notifyCounted(tag: String, id: Int, builder: NotificationCompat.Builder, own: Int, s: AppSettings) {
        cancelled -= key(tag, id)
        val others = active().filterNot { it.tag == tag && it.id == id }.map { it.shown() }
        // The newest, whatever the times say: a late message's own time may be hours ago
        val newest = BadgePlan.Shown(key(tag, id), own, Long.MAX_VALUE)
        val number = BadgePlan.of(others + newest, if (s.iconBadge) unreadTotal() else 0, s.iconBadge).numbers.getValue(newest.key)
        builder.setNumber(number).setDeleteIntent(dismissed(tag, id)).addExtras(Bundle().apply { putInt(EXTRA_OWN, own) })
        notify(tag, id, builder)
        balance(s, justPosted = key(tag, id))
    }

    /**
     * Sets every notification's number and channel as the badge setting asks: with it on, the newest one (or none, when
     * [justPosted] has just been given the rest) carries the messages without a notification; the others carry their own
     * count. Channels no longer used are deleted once no notification is left in them.
     */
    private suspend fun balance(s: AppSettings, justPosted: String?) {
        val badge = s.iconBadge
        val list = active().filterNot { key(it.tag, it.id) == justPosted }
        // After a post the new one carries the rest, so the others have only their own
        val plan = BadgePlan.of(list.map { it.shown() }, if (badge && justPosted == null) unreadTotal() else 0, badge)
        if (plan.quiet > 0) showQuiet(plan.quiet, s) else hideQuiet()
        var moved = false
        for (sbn in list) {
            val n = sbn.notification
            val channel = n.channelId ?: continue
            val wanted = if (NotificationChannels.isMessages(channel)) NotificationChannels.withBadge(channel, badge) else channel
            val number = plan.numbers.getValue(key(sbn.tag, sbn.id))
            if (wanted == channel && n.number == number) continue
            if (wanted != channel) {
                NotificationChannels.forMessages(context, lockScreenText = !NotificationChannels.isHidden(channel), badge = badge)
                moved = true
            }
            repost(sbn, wanted, number)
        }
        // Before an old channel goes, the one in use exists, with the user's sound copied from it
        val current = NotificationChannels.forMessages(context, s.lockScreenText, badge)
        if (!badge) NotificationChannels.deleteUnreadCount(context)
        if (!moved) NotificationChannels.deleteUnused(context, inUse = setOf(current), busy = list.mapNotNull { it.notification.channelId }.toSet())
    }

    /** The same notification with another number or channel, without a new sound. */
    private fun repost(sbn: StatusBarNotification, channel: String, number: Int) {
        if (!manager.areNotificationsEnabled()) return
        val rebuilt = Notification.Builder.recoverBuilder(context, sbn.notification)
            .setChannelId(channel)
            .setNumber(number)
            .setOnlyAlertOnce(true)
            .build()
        try {
            system.notify(sbn.tag, sbn.id, rebuilt)
        } catch (_: SecurityException) {
            // Notification permission withdrawn meanwhile
        }
    }

    /** Carries [count] while no message notification does. Posted silently, and only when the count changed or it is not there. */
    private fun showQuiet(count: Int, s: AppSettings) {
        val shown = quiet()
        if (shown != null && shown.notification.number == count && key(TAG_QUIET, QUIET_ID) !in cancelled) return
        cancelled -= key(TAG_QUIET, QUIET_ID)
        val builder = NotificationCompat.Builder(context, NotificationChannels.forUnreadCount(context))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(Skin.fromId(s.skin).notificationArgb)
            .setContentTitle(context.resources.getQuantityString(R.plurals.notification_unread_count, count, count))
            .setNumber(count)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setGroup(QUIET_GROUP)
            .setContentIntent(openApp(threadId = 0))
            .setDeleteIntent(dismissed(TAG_QUIET, QUIET_ID))
        notify(TAG_QUIET, QUIET_ID, builder)
    }

    private fun hideQuiet() {
        if (quiet() == null) return
        cancelled += key(TAG_QUIET, QUIET_ID)
        manager.cancel(TAG_QUIET, QUIET_ID)
    }

    private fun quiet(): StatusBarNotification? =
        try {
            system.activeNotifications.firstOrNull { it.tag == TAG_QUIET && it.id == QUIET_ID }
        } catch (_: RuntimeException) {
            null
        }

    /** The user swiped a notification away: the number it carried moves on (see [NotificationActionReceiver]). */
    private fun dismissed(tag: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0, NotificationActionReceiver.dismissedIntent(context, tag, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** The app's message notifications on the screen now, without those just cancelled. */
    private fun active(): List<StatusBarNotification> =
        try {
            system.activeNotifications.filter { (it.tag == TAG_THREAD || it.tag == TAG_UNSAVED) && key(it.tag, it.id) !in cancelled }
        } catch (_: RuntimeException) {
            emptyList()
        }

    private fun ownCount(n: Notification): Int = n.extras.getInt(EXTRA_OWN, n.number)

    private fun StatusBarNotification.shown() = BadgePlan.Shown(key(tag, id), ownCount(notification), notification.`when`)

    private fun key(tag: String?, id: Int) = "$tag:$id"

    /** [unreadCount] 0 (after a reply) shows no count (the number is set by [notifyCounted]); [accent] is the skin's (see [Skin.notificationArgb]). */
    private fun baseBuilder(channel: String, unreadCount: Int, accent: Int): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(accent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            // The colourful app icon as the big picture; the status-bar icon above stays monochrome
            .setLargeIcon(AppIconBitmap.bitmap(context))
            // Hidden on the lock screen when the phone hides sensitive content; the hidden-text channel forces it
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(channel, unreadCount, accent))

    /** What a locked screen shows instead: the app and a count, nothing more. */
    private fun publicVersion(channel: String, unreadCount: Int, accent: Int) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(accent)
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
