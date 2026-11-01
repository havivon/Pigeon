package com.havivon.pigeon.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * The reminder while Pigeon only views: a slim line at the top of the inbox instead of a dialog
 * on every open. "Make default" asks for the role; the cross hides it until the app is opened again.
 */
@Composable
fun ViewerBanner(onMakeDefault: () -> Unit, onHide: () -> Unit, modifier: Modifier = Modifier) {
    val c = HebTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceVariant)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Visibility, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.viewer_banner),
            color = c.onSurface,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onMakeDefault)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.viewer_banner_make_default), color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onHide),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.viewer_banner_hide), tint = c.onSurfaceMuted, modifier = Modifier.size(18.dp))
        }
    }
}
