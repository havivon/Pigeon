package com.hebsms.app.widget

import android.content.Context

/** Widget settings, kept in the app's private storage. The settings screen will edit them later. */
object WidgetSettings {
    private const val FILE = "widget_settings"
    private const val SHOW_CONTENT = "show_content"

    /** When false the widgets show counts only ("2 new messages"), never the message text. */
    fun showContent(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(SHOW_CONTENT, true)

    fun setShowContent(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(SHOW_CONTENT, value).apply()
    }
}
