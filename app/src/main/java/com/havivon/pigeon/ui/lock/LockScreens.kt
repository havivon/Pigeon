package com.havivon.pigeon.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * Covers the whole app while it waits for the fingerprint or screen lock: with app lock on when it
 * comes back, and over a locked conversation. Nothing of the messages shows through it.
 */
@Composable
fun LockCover(onUnlock: () -> Unit) {
    val c = HebTheme.colors
    // Swallows every touch, so nothing behind the cover can be reached
    Box(
        Modifier
            .fillMaxSize()
            .background(c.header)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.08f), radius = size.width * 0.8f, center = Offset(size.width * 0.05f, size.height * 0.02f))
                drawCircle(Color.White.copy(alpha = 0.06f), radius = size.width * 0.55f, center = Offset(size.width * 0.95f, size.height * 0.92f))
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.lock_cover_title), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.lock_cover_body), color = Color.White.copy(alpha = 0.88f), fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            val onWhite = if (c.isDark) Color(0xFF3A2596) else c.accent
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable(onClick = onUnlock)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.lock_unlock), color = onWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
