package com.havivon.pigeon.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.havivon.pigeon.R
import com.havivon.pigeon.data.sample.SampleSet
import com.havivon.pigeon.domain.model.FavoriteMessage

private val RowHeight = 56.dp

/** The starred messages: a gradient header with a star, then as many rows as fit. */
class FavoritesWidget : GlanceAppWidget() {
    // The number of rows depends on the height, so the widget needs the exact size
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sample = sampleFor(context)
        val showContent = WidgetSettings.showContent(context)
        provideContent { FavoritesWidgetContent(sample, showContent) }
    }
}

class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}

@Composable
fun FavoritesWidgetContent(sample: SampleSet, showContent: Boolean = true) {
    val fitting = ((LocalSize.current.height - HeaderHeight) / RowHeight).toInt()
    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(R.drawable.widget_bg_surface)),
    ) {
        WidgetHeaderBar(openApp()) {
            WidgetIcon(R.drawable.ic_widget_star, 18.dp)
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = widgetString(R.string.widget_favorites_title),
                style = TextStyle(color = WidgetColors.onGradient, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
        }
        if (sample.favorites.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = widgetString(R.string.widget_favorites_empty),
                    style = TextStyle(color = WidgetColors.muted, fontSize = 14.sp),
                )
            }
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                sample.favorites.take(fitting).forEach { FavoriteRow(it, showContent) }
            }
        }
    }
}

@Composable
private fun FavoriteRow(favorite: FavoriteMessage, showContent: Boolean) {
    Row(
        // Later: a deep link to this message in its conversation (favorite.id) instead of just opening the app
        modifier = GlanceModifier.fillMaxWidth().height(RowHeight).clickable(openApp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetAvatar(favorite.sender, favorite.avatarArgb, 32.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = favorite.sender,
                    style = TextStyle(color = WidgetColors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(text = favorite.date, style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1)
            }
            // With "counts only" the starred text stays hidden
            if (showContent) {
                Text(
                    text = favorite.text,
                    style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp),
                    maxLines = 2,
                )
            }
        }
    }
}
