package com.havivon.pigeon.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.havivon.pigeon.data.AppContainer

/**
 * The base of the five widget receivers: when the first widget is placed the app starts keeping the
 * widgets up to date, and when the last one is removed it stops ([WidgetRefresher]).
 */
abstract class PigeonWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AppContainer.from(context).widgets.ensureRunning()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        AppContainer.from(context).widgets.stopIfNoneLeft()
    }
}
