package com.havivon.pigeon

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test

/**
 * The launcher-icon options, drawn the way a launcher draws an adaptive icon: both 108dp layers,
 * masked to the central 72dp, in the shapes launchers use, and small on a home-screen row.
 */
class IconSnapshots {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "iw"),
        theme = "android:Theme.Material.Light.NoActionBar",
        renderingMode = SessionParams.RenderingMode.SHRINK,
    )

    @Test
    fun options() {
        paparazzi.snapshot("23_icon_a_origami") { IconSheet(R.drawable.icon_option_origami, "א · יונת אוריגמי") }
        paparazzi.snapshot("24_icon_b_bubble") { IconSheet(R.drawable.icon_option_bubble, "ב · יונה בבועת שיחה") }
        paparazzi.snapshot("25_icon_c_envelope") { IconSheet(R.drawable.icon_option_envelope, "ג · מעטפה עם כנפיים") }
    }
}

private val LauncherShapes: List<Shape> = listOf(
    CircleShape,
    RoundedCornerShape(30),
    RoundedCornerShape(18),
    RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 50, bottomStartPercent = 14),
)

@Composable
private fun IconSheet(@DrawableRes foreground: Int, title: String) {
    Column(
        Modifier
            .width(420.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF26324A), Color(0xFF46597A))))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LauncherShapes.forEach { AdaptiveIcon(foreground, 76.dp, it) }
        }
        Spacer(Modifier.height(28.dp))
        // As it sits on a home screen, at real size, next to neutral placeholders
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            HomeSlot("טלפון") { Placeholder() }
            HomeSlot("Pigeon") { AdaptiveIcon(foreground, 52.dp, CircleShape) }
            HomeSlot("מצלמה") { Placeholder() }
            HomeSlot("גלריה") { Placeholder() }
        }
    }
}

@Composable
private fun AdaptiveIcon(@DrawableRes foreground: Int, size: Dp, shape: Shape) {
    // The layers are 108dp for a 72dp visible icon, so they are drawn 1.5× and clipped
    Box(Modifier.size(size).shadow(6.dp, shape).clip(shape), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
        Image(painterResource(foreground), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
    }
}

@Composable
private fun HomeSlot(label: String, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun Placeholder() {
    Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
}
