package com.havivon.pigeon.data.notify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.havivon.pigeon.R
import com.havivon.pigeon.data.AppIconSwitcher
import com.havivon.pigeon.domain.model.AppIcon

/**
 * The whole launcher icon, in colour, as the big round picture of a notification: the sender's picture
 * when there is no contact photo (instead of the system's grey silhouette), and the picture of reminders.
 * Drawn from the adaptive icon's two layers (the background, and the foreground of the icon the user
 * chose), masked to a circle like a launcher draws it, and kept per icon. The status-bar icon stays the
 * monochrome [R.drawable.ic_notification], as Android requires.
 */
object AppIconBitmap {

    /** The adaptive layers are 108dp, of which the launcher shows the middle 72dp. */
    private const val LAYER_DP = 108f
    private const val VISIBLE_DP = 72f

    private var cached: Pair<AppIcon, Bitmap>? = null

    @Synchronized
    fun bitmap(context: Context, icon: AppIcon = AppIconSwitcher.current(context)): Bitmap {
        cached?.takeIf { it.first == icon }?.let { return it.second }
        val size = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width).coerceAtLeast(MIN_SIZE)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val half = size / 2f
        canvas.clipPath(Path().apply { addCircle(half, half, half, Path.Direction.CW) })
        // The layer drawn larger than the bitmap by the part the launcher hides, centred
        val overhang = (size * (LAYER_DP - VISIBLE_DP) / 2f / VISIBLE_DP).toInt()
        for (res in listOf(R.drawable.ic_launcher_background, icon.foregroundRes)) {
            val layer = ContextCompat.getDrawable(context, res) ?: continue
            layer.setBounds(-overhang, -overhang, size + overhang, size + overhang)
            layer.draw(canvas)
        }
        cached = icon to bitmap
        return bitmap
    }

    fun icon(context: Context): IconCompat = IconCompat.createWithBitmap(bitmap(context))

    /** Small screens report a tiny large-icon size; the picture is still drawn sharp enough to scale down. */
    private const val MIN_SIZE = 96
}
