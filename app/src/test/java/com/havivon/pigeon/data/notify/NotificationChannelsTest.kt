package com.havivon.pigeon.data.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationChannelsTest {

    @Test
    fun `each pair of settings has its own message channel`() {
        val all = listOf(true, false).flatMap { hidden -> listOf(true, false).map { badge -> NotificationChannels.messageChannel(hidden, badge) } }
        assertEquals(4, all.toSet().size)
        // The channels of earlier versions keep their ids: they are the ones with the badge
        assertEquals(NotificationChannels.MESSAGES, NotificationChannels.messageChannel(hidden = false, badge = true))
        assertEquals(NotificationChannels.MESSAGES_HIDDEN, NotificationChannels.messageChannel(hidden = true, badge = true))
    }

    @Test
    fun `switching the badge keeps the lock-screen choice`() {
        assertEquals(NotificationChannels.MESSAGES_HIDDEN_NO_BADGE, NotificationChannels.withBadge(NotificationChannels.MESSAGES_HIDDEN, badge = false))
        assertEquals(NotificationChannels.MESSAGES, NotificationChannels.withBadge(NotificationChannels.MESSAGES_NO_BADGE, badge = true))
        assertTrue(NotificationChannels.isHidden(NotificationChannels.MESSAGES_HIDDEN_NO_BADGE))
        assertFalse(NotificationChannels.isHidden(NotificationChannels.MESSAGES))
    }

    @Test
    fun `reminders are not message channels, so the badge never moves them`() {
        assertFalse(NotificationChannels.isMessages(NotificationChannels.REMINDERS))
        assertFalse(NotificationChannels.isMessages(NotificationChannels.REMINDERS_HIDDEN))
        assertTrue(NotificationChannels.isMessages(NotificationChannels.MESSAGES_NO_BADGE))
    }
}
