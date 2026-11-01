package com.havivon.pigeon.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * Once Pigeon is the messaging app: why it asks for notifications and contacts, before the system
 * asks. Both are optional; without them the app shows numbers instead of names, and no alerts.
 */
@Composable
fun PermissionsExplainer(askNotifications: Boolean, askContacts: Boolean, onContinue: () -> Unit, onNotNow: () -> Unit) {
    Dialog(onDismissRequest = onNotNow) {
        PermissionsCard(askNotifications, askContacts, onContinue, onNotNow, Modifier.fillMaxWidth())
    }
}

/** The explainer's card itself, apart from its window, so the design snapshots can draw it. */
@Composable
internal fun PermissionsCard(askNotifications: Boolean, askContacts: Boolean, onContinue: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    val c = HebTheme.colors
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = c.surface, shadowElevation = 8.dp) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.permissions_title),
                color = c.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (askNotifications) Reason(Icons.Rounded.Notifications, stringResource(R.string.permissions_notifications))
                if (askContacts) Reason(Icons.Rounded.Contacts, stringResource(R.string.permissions_contacts))
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.permissions_optional), color = c.onSurfaceMuted, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            PillButton(stringResource(R.string.permissions_continue), Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp), filled = true, onClick = onContinue)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).clickable(onClick = onNotNow).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.onboarding_not_now), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Reason(icon: ImageVector, text: String) {
    val c = HebTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surfaceVariant).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.header), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = c.onSurface, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}
