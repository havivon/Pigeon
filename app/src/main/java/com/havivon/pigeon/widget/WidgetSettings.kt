package com.havivon.pigeon.widget

import android.content.Context
import com.havivon.pigeon.data.AppContainer
import kotlinx.coroutines.flow.first

/**
 * The widgets' view of the settings. The value lives in the app's settings (DataStore), which took
 * over the preview's SharedPreferences value on its first read.
 */
object WidgetSettings {

    /** When false the widgets show counts only ("2 new messages"), never the message text. */
    suspend fun showContent(context: Context): Boolean =
        AppContainer.from(context).settings.settings.first().widgetsShowText

    suspend fun setShowContent(context: Context, value: Boolean) {
        AppContainer.from(context).settings.setWidgetsShowText(value)
    }
}
