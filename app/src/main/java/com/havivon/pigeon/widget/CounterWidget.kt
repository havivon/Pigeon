package com.havivon.pigeon.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.theme.Skin

/** A small tile with the number of unread messages, and a red pill when spam is waiting. */
class CounterWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val source = WidgetSource(context)
        val data = source.counter()
        val skin = source.skin()
        provideContent { CounterWidgetContent(data, skin) }
    }
}

class CounterWidgetReceiver : PigeonWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CounterWidget()
}

/** The widget in [skin] (the app's setting; Sunset in the design shots unless they say otherwise). */
@Composable
fun CounterWidgetContent(data: CounterWidgetData, skin: Skin = Skin.Sunset) {
    WidgetSkinned(skin) { CounterWidgetBody(data) }
}

@Composable
private fun CounterWidgetBody(data: CounterWidgetData) {
    val unread = data.unread
    val spam = data.spam
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(widgetDrawables.gradient))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(openApp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier.size(32.dp).background(ImageProvider(R.drawable.widget_circle_glass)),
                contentAlignment = Alignment.Center,
            ) {
                WidgetIcon(R.drawable.ic_widget_chat, 16.dp)
            }
            Spacer(GlanceModifier.width(12.dp))
            Text(
                text = unread.toString(),
                style = TextStyle(color = WidgetColors.onGradient, fontSize = 34.sp, fontWeight = FontWeight.Bold),
            )
        }
        if (spam > 0) {
            Spacer(GlanceModifier.height(4.dp))
            WidgetPill(widgetString(R.string.widget_counter_spam, spam), R.drawable.widget_pill_spam)
        }
    }
}
