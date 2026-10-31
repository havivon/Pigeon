package com.havivon.pigeon.ui.spam

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.SpamReason
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.components.SpamAvatar
import com.havivon.pigeon.ui.theme.HebTheme

private val LinkPattern = Regex("""\b[\w-]+\.(?:ly|co|com|net|info|xyz)(?:/\S*)?""")

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SpamScreen(
    items: List<Conversation>,
    onBack: () -> Unit = {},
    onNotSpam: (Conversation) -> Unit = {},
    onDelete: (Conversation) -> Unit = {},
) {
    val c = HebTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.spam_title), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            pluralStringResource(R.plurals.spam_subtitle, items.size, items.size),
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 13.sp,
                        )
                    }
                    HeaderIcon(Icons.Rounded.Delete, stringResource(R.string.spam_delete_all))
                }
            }
        }
        item {
            Row(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceVariant)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = c.accent)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.spam_info),
                    color = c.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }
        items(items, key = { it.id }) { SpamCard(it, onNotSpam, onDelete) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpamCard(conv: Conversation, onNotSpam: (Conversation) -> Unit, onDelete: (Conversation) -> Unit) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.spamTint)
            .border(2.dp, c.spam, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpamAvatar(size = 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(conv.name, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(conv.time, color = c.onSurfaceMuted, fontSize = 12.sp)
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(c.spam)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.common_suspected_spam), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            buildAnnotatedString {
                append(conv.snippet)
                LinkPattern.findAll(conv.snippet).forEach {
                    addStyle(
                        SpanStyle(color = c.spam, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                        it.range.first,
                        it.range.last + 1,
                    )
                }
            },
            color = c.onSurface,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            conv.spamReasons.forEach { reason ->
                Text(
                    reason.label(),
                    color = c.spam,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, c.spam.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(stringResource(R.string.spam_not_spam), onClick = { onNotSpam(conv) })
            PillButton(stringResource(R.string.spam_delete), icon = Icons.Rounded.Delete, color = c.spam, onClick = { onDelete(conv) })
        }
    }
}

@Composable
private fun SpamReason.label(): String = when (this) {
    SpamReason.UnknownSender -> stringResource(R.string.spam_reason_unknown_sender)
    SpamReason.ShortLink -> stringResource(R.string.spam_reason_short_link)
    SpamReason.LookalikeDomain -> stringResource(R.string.spam_reason_lookalike_domain)
    is SpamReason.Keyword -> word
}
