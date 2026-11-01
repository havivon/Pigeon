package com.havivon.pigeon

import android.app.Application
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.notify.NotificationChannels
import com.havivon.pigeon.data.reminders.AlarmReminderScheduler
import com.havivon.pigeon.data.work.BinPurgeWorker
import com.havivon.pigeon.data.work.CodeCleanupWorker
import com.havivon.pigeon.domain.analysis.AnalysisSeams
import com.havivon.pigeon.domain.analysis.SpamFilterHook
import kotlinx.coroutines.launch

/** How far back a conversation is read for a message the user wrote (which makes the sender known). */
private const val KNOWN_SENDER_DEPTH = 50

class PigeonApp : Application() {

    /** The platform with the analysis plugged in: codes, spam on arrival, folders. See [AppContainer]. */
    val container: AppContainer by lazy {
        AppContainer(
            this,
            codeFinder = AnalysisSeams.codeFinder,
            folderClassifier = AnalysisSeams.folderClassifier,
            incomingHook = { app ->
                SpamFilterHook(app.contacts, app.spam, app.blockedKeywords) { threadId ->
                    app.messages.recentMessages(threadId, KNOWN_SENDER_DEPTH).any { it.type.outgoing }
                }
            },
            reminderScheduler = AlarmReminderScheduler(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        CodeCleanupWorker.schedule(this)
        BinPurgeWorker.schedule(this)
        // Off the main thread: the widgets only need keeping current if one is placed
        container.appScope.launch { container.widgets.ensureRunning() }
    }
}
