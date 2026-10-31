package com.havivon.pigeon.widget

import android.content.Context
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
import com.havivon.pigeon.MainActivity
import com.havivon.pigeon.R
import com.havivon.pigeon.data.sample.SampleData
import com.havivon.pigeon.data.sample.SampleSet
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

/** Every tap target opens the app for now. Later each one gets a deep link to its conversation (or message). */
internal fun openApp(): Action = actionStartActivity<MainActivity>()

/** The widgets show sample content until SMS access exists. */
internal fun sampleFor(context: Context): SampleSet =
    SampleData.forLanguage(context.resources.configuration.locales[0].language)

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
