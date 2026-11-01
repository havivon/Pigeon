package com.havivon.pigeon.ui.conversation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Warning
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
import com.havivon.pigeon.domain.analysis.LinkInfo
import com.havivon.pigeon.ui.common.ltr
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * What a tapped link shows before anything opens: the real host it goes to, a red warning when it
 * imitates a known site or hides its destination behind a shortener, and Open / Copy. Opening hands
 * the link to the browser; Pigeon itself has no internet access.
 */
@Composable
internal fun LinkDetails(link: LinkInfo, onOpen: () -> Unit, onCopy: () -> Unit) {
    val c = HebTheme.colors
    Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Link, contentDescription = null, tint = c.accent)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.link_goes_to), color = c.onSurfaceMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(ltr(link.host), color = c.onSurface, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(2.dp))
        Text(ltr(link.text), color = c.onSurfaceMuted, fontSize = 13.sp, maxLines = 2)
        val warning = when {
            link.isLookalike -> if (link.lookalikeOf != null) stringResource(R.string.link_warning_lookalike, link.lookalikeOf) else stringResource(R.string.link_warning_disguised)
            link.isShortener -> stringResource(R.string.link_warning_shortener)
            else -> null
        }
        if (warning != null) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceVariant)
                    .border(1.5.dp, c.spam, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = c.spam)
                Spacer(Modifier.width(10.dp))
                Text(warning, color = c.onSurface, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                stringResource(R.string.link_open),
                Modifier.weight(1f),
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                filled = warning == null,
                color = if (warning == null) c.accent else c.spam,
                onClick = onOpen,
            )
            PillButton(stringResource(R.string.common_copy), Modifier.weight(1f), icon = Icons.Rounded.ContentCopy, onClick = onCopy)
        }
    }
}

/** The link as the browser should get it: only web links, and a bare "example.com/x" as https. */
fun browsableUri(link: LinkInfo): Uri {
    val text = link.text
    val scheme = text.substringBefore("://", "").lowercase()
    return Uri.parse(if (scheme == "http" || scheme == "https") text else "https://" + text.substringAfter("://"))
}

fun openInBrowser(context: Context, link: LinkInfo): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, browsableUri(link)).addCategory(Intent.CATEGORY_BROWSABLE))
    true
} catch (_: ActivityNotFoundException) {
    false
}
