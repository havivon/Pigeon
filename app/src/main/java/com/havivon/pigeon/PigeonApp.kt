package com.havivon.pigeon

import android.app.Application
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.notify.NotificationChannels
import com.havivon.pigeon.data.reminders.AlarmReminderScheduler
import com.havivon.pigeon.data.work.CodeCleanupWorker
import com.havivon.pigeon.domain.analysis.AnalysisSeams
import com.havivon.pigeon.domain.analysis.SpamFilterHook

class PigeonApp : Application() {

    /** The platform with the analysis plugged in: codes, spam on arrival, folders. See [AppContainer]. */
    val container: AppContainer by lazy {
        AppContainer(
            this,
            codeFinder = AnalysisSeams.codeFinder,
            folderClassifier = AnalysisSeams.folderClassifier,
            incomingHook = { SpamFilterHook(it.contacts, it.spam, it.blockedKeywords) },
            reminderScheduler = AlarmReminderScheduler(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        CodeCleanupWorker.schedule(this)
    }
}
