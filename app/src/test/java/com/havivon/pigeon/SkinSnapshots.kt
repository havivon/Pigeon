package com.havivon.pigeon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.havivon.pigeon.data.sample.SampleData
import com.havivon.pigeon.ui.conversation.ConversationScreen
import com.havivon.pigeon.ui.inbox.InboxScreen
import com.havivon.pigeon.ui.theme.HebSmsTheme
import com.havivon.pigeon.ui.theme.HebTheme
import com.havivon.pigeon.ui.theme.Skin
import org.junit.Rule
import org.junit.Test

/** The skins' names for the sample sheets (Hebrew · English). The settings picker will take them from the resources. */
private val SkinNames = mapOf(
    Skin.Sunset to ("שקיעה" to "Sunset"),
    Skin.Ocean to ("ים" to "Ocean"),
    Skin.Forest to ("יער" to "Forest"),
    Skin.Midnight to ("לילה" to "Midnight"),
    Skin.Pomegranate to ("רימון" to "Pomegranate"),
    Skin.Desert to ("מדבר" to "Desert"),
    Skin.Pastel to ("פסטל" to "Pastel"),
    Skin.Graphite to ("גרפיט" to "Graphite"),
)

private val SheetBackground = Color(0xFFE4E4EA)
private val SheetInk = Color(0xFF2A2A33)

/** Each phone on a sheet: 360dp wide, as a small phone. */
private val PhoneWidth = 360.dp
private val PhoneHeight = 740.dp

/**
 * The skins to choose from, before the settings picker exists: one sheet per skin with the real inbox and conversation
 * screens in Hebrew, light above and dark below, and one overview of all eight. Run:
 * gradlew :app:recordPaparazziDebug --tests "*SkinSnapshots*"
 */
class SkinSnapshots {

    @get:Rule
    val paparazzi = Paparazzi(
        // A tall xhdpi canvas; SHRINK crops it to the sheet
        deviceConfig = DeviceConfig.PIXEL_6.copy(
            locale = "iw",
            density = Density.XHIGH,
            xdpi = 320,
            ydpi = 320,
            screenWidth = 1640,
            screenHeight = 3600,
        ),
        theme = "android:Theme.Material.Light.NoActionBar",
        renderingMode = SessionParams.RenderingMode.SHRINK,
        // Full size: the default 1000px thumbnail would make two phones side by side unreadable
        useDeviceResolution = true,
    )

    private val sample = SampleData.hebrew
    private val danny = sample.inbox.first { it.id == 2L }

    @Test
    fun sheets() {
        Skin.entries.forEachIndexed { i, skin ->
            paparazzi.snapshot("skin_${i + 1}_${skin.id}") { SkinSheet(skin) }
        }
    }

    @Test
    fun overview() {
        paparazzi.snapshot("skins_overview") { Overview() }
    }

    @Composable
    private fun SkinSheet(skin: Skin) {
        val (he, en) = SkinNames.getValue(skin)
        Column(
            Modifier.background(SheetBackground).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier
                    .width(PhoneWidth * 2 + 16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(skin.light.header)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(he, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(12.dp))
                Text(en, color = Color.White.copy(alpha = 0.9f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${skin.ordinal + 1} / ${Skin.entries.size}", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
            }
            ModeRow("בהיר · Light", skin, dark = false)
            ModeRow("כהה · Dark", skin, dark = true)
        }
    }

    @Composable
    private fun ModeRow(label: String, skin: Skin, dark: Boolean) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = SheetInk, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Phone(skin, dark) {
                    InboxScreen(sample.inbox, sample.rules, sample.folderCounts, sample.dateLine, unreadCount = 4)
                }
                Phone(skin, dark) {
                    ConversationScreen(danny, sample.dannyThread, rule = null, draft = "אני בדרך, אגיע בעוד עשר דקות", subtitle = "052-555-0123")
                }
            }
        }
    }

    @Composable
    private fun Phone(skin: Skin, dark: Boolean, screen: @Composable () -> Unit) {
        val shape = RoundedCornerShape(22.dp)
        Box(
            Modifier
                .size(PhoneWidth, PhoneHeight)
                .clip(shape)
                .border(1.dp, Color.Black.copy(alpha = 0.12f), shape),
        ) {
            HebSmsTheme(darkTheme = dark, skin = skin) { screen() }
        }
    }

    @Composable
    private fun Overview() {
        Column(
            Modifier.background(SheetBackground).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Skin.entries.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { SwatchCard(it) }
                }
            }
        }
    }

    /** A skin in small: its header strip with the name, then a bubble pair and the accent on its light and its dark page. */
    @Composable
    private fun SwatchCard(skin: Skin) {
        val (he, en) = SkinNames.getValue(skin)
        Column(Modifier.width(180.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(18.dp))) {
            Column(Modifier.fillMaxWidth().background(skin.light.header).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(he, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(en, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            HebSmsTheme(darkTheme = false, skin = skin) { SwatchBody() }
            Box(Modifier.fillMaxWidth().height(6.dp).background(skin.dark.header))
            HebSmsTheme(darkTheme = true, skin = skin) { SwatchBody() }
        }
    }

    @Composable
    private fun SwatchBody() {
        val c = HebTheme.colors
        Column(
            Modifier.fillMaxWidth().background(c.background).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(c.accent))
                Spacer(Modifier.width(6.dp))
                Text("דני כהן", color = c.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text("10:24", color = c.onSurfaceMuted, fontSize = 11.sp)
            }
            Text(
                "מה שלומך?",
                color = c.onSurface,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
                    .background(c.incoming)
                    .border(1.dp, c.divider, RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    "מצוין, תודה!",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .widthIn(max = 150.dp)
                        .clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                        .background(c.outgoing)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
