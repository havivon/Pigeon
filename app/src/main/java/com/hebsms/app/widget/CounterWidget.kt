package com.hebsms.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import com.hebsms.app.R
import com.hebsms.app.data.sample.SampleSet

/** A small tile with the number of unread messages, and a red pill when spam is waiting. */
class CounterWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sample = sampleFor(context)
        provideContent { CounterWidgetContent(sample) }
    }
}

class CounterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CounterWidget()
}

@Composable
fun CounterWidgetContent(sample: SampleSet) {
    val unread = sample.inbox.sumOf { it.unread }
    val spam = sample.spam.size
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(R.drawable.widget_bg_gradient))
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
