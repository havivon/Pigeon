package com.havivon.pigeon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.model.AppIcon
import com.havivon.pigeon.ui.components.LauncherIcon
import com.havivon.pigeon.ui.theme.HebTheme

/** The launcher icon choice: the three icons as a launcher draws them, the current one ringed. */
@Composable
fun AppIconPicker(current: AppIcon, onPick: (AppIcon) -> Unit) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 3.dp, shape, spotColor = c.accent.copy(alpha = 0.3f))
            .clip(shape)
            .background(c.surface)
            .padding(vertical = 16.dp, horizontal = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AppIcon.entries.forEach { icon ->
                val selected = icon == current
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { onPick(icon) }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(76.dp)
                            .then(if (selected) Modifier.border(3.dp, c.accent, CircleShape) else Modifier)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LauncherIcon(icon.foregroundRes, 64.dp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(icon.labelRes),
                        color = if (selected) c.accent else c.onSurfaceMuted,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.rules_app_icon_note),
            color = c.onSurfaceMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
