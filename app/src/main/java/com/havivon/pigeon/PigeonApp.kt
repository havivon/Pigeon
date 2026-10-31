package com.havivon.pigeon

import android.app.Application
import com.havivon.pigeon.data.AppContainer
import com.havivon.pigeon.data.notify.NotificationChannels
import com.havivon.pigeon.data.work.CodeCleanupWorker

class PigeonApp : Application() {

    /** The analysis (spam, codes, folders) is passed in here once it exists; see [AppContainer]. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        CodeCleanupWorker.schedule(this)
    }
}
