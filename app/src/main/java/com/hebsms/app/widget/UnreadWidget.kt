package com.hebsms.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.hebsms.app.R
import com.hebsms.app.data.sample.SampleSet
import com.hebsms.app.domain.model.Conversation

private val RowHeight = 40.dp

/** The unread conversations: a gradient header with the total, then as many rows as fit. */
class UnreadWidget : GlanceAppWidget() {
    // The number of rows depends on the height, so the widget needs the exact size
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sample = sampleFor(context)
        val showContent = WidgetSettings.showContent(context)
        provideContent { UnreadWidgetContent(sample, showContent) }
    }
}

class UnreadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UnreadWidget()
}

@Composable
fun UnreadWidgetContent(sample: SampleSet, showContent: Boolean = true) {
    val unread = sample.inbox.filter { it.unread > 0 }
    val fitting = ((LocalSize.current.height - HeaderHeight) / RowHeight).toInt()
    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(R.drawable.widget_bg_surface)),
    ) {
        WidgetHeaderBar(openApp()) {
            Text(
                text = widgetString(R.string.widget_unread_title),
                style = TextStyle(color = WidgetColors.onGradient, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetPill(unread.sumOf { it.unread }.toString(), R.drawable.widget_pill_glass)
            Spacer(GlanceModifier.defaultWeight())
            // The new-message button; it will open the compose screen once there is one
            Box(
                modifier = GlanceModifier.size(30.dp).background(ImageProvider(R.drawable.widget_circle_glass)).clickable(openApp()),
                contentAlignment = Alignment.Center,
            ) {
                WidgetIcon(R.drawable.ic_widget_edit, 16.dp, widgetString(R.string.inbox_new_message))
            }
        }
        if (unread.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = widgetString(R.string.widget_unread_empty),
                    style = TextStyle(color = WidgetColors.muted, fontSize = 14.sp),
                )
            }
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                unread.take(fitting).forEach { conversation ->
                    val rule = sample.rules.firstOrNull { it.enabled && it.id == conversation.ruleId }
                    UnreadRow(conversation, rule?.argb, showContent)
                }
            }
        }
    }
}

@Composable
private fun UnreadRow(conversation: Conversation, ruleArgb: Long?, showContent: Boolean) {
    Row(
        // Later: a deep link to this conversation (conversation.id) instead of just opening the app
        modifier = GlanceModifier.fillMaxWidth().height(RowHeight).clickable(openApp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The colour rule's strip sits on the start edge; rows without a rule keep the space
        if (ruleArgb != null) {
            Box(modifier = GlanceModifier.width(4.dp).height(26.dp).background(ColorProvider(Color(ruleArgb)))) {}
        } else {
            Spacer(GlanceModifier.width(4.dp))
        }
        Spacer(GlanceModifier.width(8.dp))
        WidgetAvatar(conversation.name, conversation.avatarArgb, 30.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = conversation.name,
                style = TextStyle(color = WidgetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = if (showContent) conversation.snippet else widgetPlural(R.plurals.widget_unread_new_messages, conversation.unread),
                style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(text = conversation.time, style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1)
    }
}
