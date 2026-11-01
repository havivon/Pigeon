package com.havivon.pigeon.widget

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
import com.havivon.pigeon.R

private val RowHeight = 40.dp

/** The unread conversations: a gradient header with the total, then as many rows as fit. */
class UnreadWidget : GlanceAppWidget() {
    // The number of rows depends on the height, so the widget needs the exact size
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val source = WidgetSource(context)
        val data = source.unread()
        val showContent = source.showText()
        provideContent { UnreadWidgetContent(data, showContent) }
    }
}

class UnreadWidgetReceiver : PigeonWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UnreadWidget()
}

@Composable
fun UnreadWidgetContent(data: UnreadWidgetData, showContent: Boolean = true) {
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
            WidgetPill(data.totalUnread.toString(), R.drawable.widget_pill_glass)
            Spacer(GlanceModifier.defaultWeight())
            // The new-message button
            Box(
                modifier = GlanceModifier.size(30.dp).background(ImageProvider(R.drawable.widget_circle_glass)).clickable(openNewMessage()),
                contentAlignment = Alignment.Center,
            ) {
                WidgetIcon(R.drawable.ic_widget_edit, 16.dp, widgetString(R.string.inbox_new_message))
            }
        }
        if (data.rows.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = widgetString(R.string.widget_unread_empty),
                    style = TextStyle(color = WidgetColors.muted, fontSize = 14.sp),
                )
            }
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                data.rows.take(fitting).forEach { UnreadRow(it, showContent) }
            }
        }
    }
}

@Composable
private fun UnreadRow(row: WidgetUnreadRow, showContent: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(RowHeight).clickable(openThread(row.threadId)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The colour rule's strip sits on the start edge; rows without a rule keep the space
        if (row.ruleArgb != null) {
            Box(modifier = GlanceModifier.width(4.dp).height(26.dp).background(ColorProvider(Color(row.ruleArgb)))) {}
        } else {
            Spacer(GlanceModifier.width(4.dp))
        }
        Spacer(GlanceModifier.width(8.dp))
        WidgetAvatar(row.name, row.avatarArgb, 30.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = row.name,
                style = TextStyle(color = WidgetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = if (showContent && row.snippet.isNotEmpty()) row.snippet else widgetPlural(R.plurals.widget_unread_new_messages, row.unread),
                style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(text = row.time, style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1)
    }
}
