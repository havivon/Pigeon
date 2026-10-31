package com.havivon.pigeon

import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.NightMode
import com.havivon.pigeon.data.sample.SampleData
import com.havivon.pigeon.widget.CodeWidgetContent
import com.havivon.pigeon.widget.ContactsWidgetContent
import com.havivon.pigeon.widget.CounterWidgetContent
import com.havivon.pigeon.widget.FavoritesWidgetContent
import com.havivon.pigeon.widget.UnreadWidgetContent
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

// Approximate sizes of a launcher's cells, in dp
private const val W4 = 340
private const val W2 = 170
private const val H1 = 90
private const val H2 = 170

/**
 * Renders a widget the way a launcher would: Glance builds the RemoteViews, they are inflated, and the
 * result sits on a soft home-screen background with 16dp around it so the rounded corners show.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
private fun Paparazzi.widgetShot(
    name: String, locale: String, dark: Boolean, widthDp: Int, heightDp: Int, content: @Composable () -> Unit,
) {
    val density = context.resources.displayMetrics.density
    val remoteViews = runBlocking {
        GlanceRemoteViews().compose(context, DpSize(widthDp.dp, heightDp.dp)) { content() }.remoteViews
    }
    val host = FrameLayout(context).apply {
        val pad = (16 * density).toInt()
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (dark) intArrayOf(0xFF2B3442.toInt(), 0xFF1A202B.toInt()) else intArrayOf(0xFFB4C3D4.toInt(), 0xFF8497AD.toInt()),
        )
        // The layout follows the language, like the real home screen
        layoutDirection = TextUtils.getLayoutDirectionFromLocale(Locale.forLanguageTag(locale))
            .let { if (it == View.LAYOUT_DIRECTION_RTL) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR }
    }
    host.addView(
        remoteViews.apply(context, host),
        FrameLayout.LayoutParams((widthDp * density).toInt(), (heightDp * density).toInt()),
    )
    snapshot(host, name)
}

private fun config(locale: String, dark: Boolean) =
    DeviceConfig.PIXEL_6.copy(locale = locale, nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT)

/** The widgets in Hebrew, right to left. Run: gradlew :app:recordPaparazziDebug */
class WidgetSnapshots {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = config("iw", dark = false),
        theme = "android:Theme.Material.Light.NoActionBar",
        renderingMode = SessionParams.RenderingMode.SHRINK,
    )

    private val sample = SampleData.hebrew

    private fun shot(name: String, w: Int, h: Int, content: @Composable () -> Unit) =
        paparazzi.widgetShot(name, "iw", dark = false, w, h, content)

    @Test
    fun widgets() {
        shot("13_widget_unread", W4, H2) { UnreadWidgetContent(sample) }
        shot("14_widget_unread_counts_only", W4, H2) { UnreadWidgetContent(sample, showContent = false) }
        shot("15_widget_code", W2, H2) { CodeWidgetContent(sample) }
        shot("16_widget_contacts", W4, H1) { ContactsWidgetContent(sample) }
        shot("17_widget_counter", W2, H1) { CounterWidgetContent(sample) }
        shot("18_widget_favorites", W4, H2) { FavoritesWidgetContent(sample) }
    }
}

/** The same widgets in the dark theme. */
class WidgetSnapshotsDark {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = config("iw", dark = true),
        theme = "android:Theme.Material.NoActionBar",
        renderingMode = SessionParams.RenderingMode.SHRINK,
    )

    private val sample = SampleData.hebrew

    private fun shot(name: String, w: Int, h: Int, content: @Composable () -> Unit) =
        paparazzi.widgetShot(name, "iw", dark = true, w, h, content)

    @Test
    fun widgets() {
        shot("19_widget_unread_dark", W4, H2) { UnreadWidgetContent(sample) }
        shot("20_widget_code_dark", W2, H2) { CodeWidgetContent(sample) }
    }
}

/** The widgets in English, left to right. */
class WidgetSnapshotsEnglish {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = config("en", dark = false),
        theme = "android:Theme.Material.Light.NoActionBar",
        renderingMode = SessionParams.RenderingMode.SHRINK,
    )

    private val sample = SampleData.english

    private fun shot(name: String, w: Int, h: Int, content: @Composable () -> Unit) =
        paparazzi.widgetShot(name, "en", dark = false, w, h, content)

    @Test
    fun widgets() {
        shot("21_en_widget_unread", W4, H2) { UnreadWidgetContent(sample) }
        shot("22_en_widget_code", W2, H2) { CodeWidgetContent(sample) }
    }
}
