package com.havivon.pigeon.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.havivon.pigeon.R
import com.havivon.pigeon.data.LaunchRequests
import com.havivon.pigeon.ui.theme.DarkHebColors
import com.havivon.pigeon.ui.theme.LightHebColors

/** The widgets' colours: the app's palette, as a day/night pair. */
internal object WidgetColors {
    val text = ColorProvider(day = LightHebColors.onSurface, night = DarkHebColors.onSurface)
    val muted = ColorProvider(day = LightHebColors.onSurfaceMuted, night = DarkHebColors.onSurfaceMuted)
    val onGradient = ColorProvider(Color.White)
    val onGradientSoft = ColorProvider(Color.White.copy(alpha = 0.82f))

    /** Text on the white "Copy" pill: the app's violet in both themes, since the pill is white in both. */
    val onWhitePill = ColorProvider(Color(0xFF5B3FD9))
}

internal val HeaderHeight = 44.dp

/** The header and counters just open the app. */
internal fun openApp(): Action = actionStartActivity(ComponentName(MainActivityName.PACKAGE, MainActivityName.MAIN_ACTIVITY))

/**
 * Opens a conversation (a row, a contact, a code, a favourite). The intent is MainActivity's, with
 * the thread id the app already understands from notification taps ([LaunchRequests.EXTRA_THREAD_ID]);
 * the data makes each thread's intent distinct, since PendingIntents are told apart by data and not by extras.
 */
internal fun openThread(threadId: Long): Action = actionStartActivity(
    mainActivityIntent()
        .setData(Uri.parse("pigeon://thread/$threadId"))
        .putExtra(LaunchRequests.EXTRA_THREAD_ID, threadId),
)

/** The "+" button: the new-message screen. */
internal fun openNewMessage(): Action = actionStartActivity(
    mainActivityIntent()
        .setData(Uri.parse("pigeon://compose"))
        .putExtra(LaunchRequests.EXTRA_NEW_MESSAGE, true),
)

private fun mainActivityIntent(): Intent =
    Intent()
        .setComponent(ComponentName(MainActivityName.PACKAGE, MainActivityName.MAIN_ACTIVITY))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

/**
 * MainActivity by name, not by class: it is an AppCompatActivity, and AppCompat is kept off the
 * JVM test classpath (see app/build.gradle.kts), where the widget snapshots render.
 */
internal object MainActivityName {
    const val PACKAGE = "com.havivon.pigeon"
    const val MAIN_ACTIVITY = "com.havivon.pigeon.MainActivity"
}

@Composable
internal fun widgetString(@StringRes id: Int, vararg args: Any): String = LocalContext.current.getString(id, *args)

@Composable
internal fun widgetPlural(@PluralsRes id: Int, count: Int): String =
    LocalContext.current.resources.getQuantityString(id, count, count)

/** A glyph from the widget icon set; the vectors are white, so [tint] is only needed for another colour. */
@Composable
internal fun WidgetIcon(@DrawableRes res: Int, size: Dp, description: String? = null, tint: ColorProvider? = null) {
    Image(
        provider = ImageProvider(res),
        contentDescription = description,
        modifier = GlanceModifier.size(size),
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

/** A tinted oval with the contact's initial on top. */
@Composable
internal fun WidgetAvatar(name: String, argb: Long, size: Dp, textSize: TextUnit = 14.sp) {
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(R.drawable.widget_oval),
            contentDescription = null,
            modifier = GlanceModifier.size(size),
            colorFilter = ColorFilter.tint(ColorProvider(Color(argb))),
        )
        Text(
            text = (name.firstOrNull { it.isLetterOrDigit() } ?: '#').uppercaseChar().toString(),
            style = TextStyle(color = WidgetColors.onGradient, fontSize = textSize, fontWeight = FontWeight.Bold),
        )
    }
}

/** A small rounded label on a gradient: the unread count in the header, "3 spam" on the counter. */
@Composable
internal fun WidgetPill(text: String, @DrawableRes background: Int, color: ColorProvider = WidgetColors.onGradient) {
    Box(
        modifier = GlanceModifier.background(ImageProvider(background)).padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold))
    }
}

/** The gradient band at the top of the list widgets; the widget's own rounded corners are drawn by the shape. */
@Composable
internal fun WidgetHeaderBar(action: Action, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .background(ImageProvider(R.drawable.widget_bg_header))
            .padding(horizontal = 16.dp)
            .clickable(action),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** "2 min ago", "3 hours ago", "2 days ago": the age of a code in the largest whole unit. */
@Composable
internal fun widgetAgo(minutes: Long): String {
    val m = minutes.coerceAtLeast(1)
    return when {
        m < 60 -> widgetPlural(R.plurals.widget_ago_minutes, m.toInt())
        m < 24 * 60 -> widgetPlural(R.plurals.widget_ago_hours, (m / 60).toInt())
        else -> widgetPlural(R.plurals.widget_ago_days, (m / (24 * 60)).toInt())
    }
}
