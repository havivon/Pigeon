package com.havivon.pigeon.ui.compose

import com.havivon.pigeon.ui.components.HeaderTitle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.PhoneNumbers
import com.havivon.pigeon.ui.common.avatarColor
import com.havivon.pigeon.ui.common.ltr
import com.havivon.pigeon.ui.components.Avatar
import com.havivon.pigeon.ui.components.EmptyState
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.conversation.Composer
import com.havivon.pigeon.ui.theme.HebTheme

/** A new message: recipients as chips (a contact or a typed number), the suggestions under them, and the composer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewMessageScreen(
    state: NewMessageUiState,
    onBack: () -> Unit = {},
    onQuery: (String) -> Unit = {},
    onCommitTyped: () -> Unit = {},
    onAdd: (Suggestion) -> Unit = {},
    onRemove: (Recipient) -> Unit = {},
    onBody: (String) -> Unit = {},
    onSend: () -> Unit = {},
    /** False in the design snapshots, where there is no keyboard to open. */
    autoFocus: Boolean = true,
) {
    val c = HebTheme.colors
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { if (state.recipients.isEmpty()) focus.requestFocus() }
    Column(Modifier.fillMaxSize().background(c.background)) {
        GradientHeader {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
                HeaderTitle(stringResource(R.string.inbox_new_message), subtitle = null, Modifier.weight(1f))
            }
        }
        val shape = RoundedCornerShape(20.dp)
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth()
                .shadow(if (c.isDark) 0.dp else 2.dp, shape, spotColor = c.accent.copy(alpha = 0.25f))
                .clip(shape)
                .background(c.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.compose_to), color = c.onSurfaceMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.recipients.forEach { RecipientChip(it, onRemove) }
                Box(Modifier.widthIn(min = 120.dp).padding(vertical = 6.dp)) {
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = TextStyle(color = c.onSurface, fontSize = 16.sp),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { onCommitTyped() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        decorationBox = { inner ->
                            if (state.query.isEmpty() && state.recipients.isEmpty()) {
                                Text(stringResource(R.string.compose_to_hint), color = c.onSurfaceMuted, fontSize = 16.sp, maxLines = 1)
                            }
                            inner()
                        },
                    )
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (state.query.isBlank() && state.recipients.isEmpty()) {
                item { EmptyState(Icons.Rounded.PersonSearch, stringResource(R.string.compose_start_title), stringResource(R.string.compose_start_body)) }
            }
            items(state.suggestions, key = { (it.name ?: "") + "|" + PhoneNumbers.normalize(it.number) }) { SuggestionRow(it) { onAdd(it) } }
        }
        Composer(state.body, onBody, onSend, canSend = state.canSend)
    }
}

@Composable
private fun RecipientChip(r: Recipient, onRemove: (Recipient) -> Unit) {
    val c = HebTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(c.accent.copy(alpha = 0.14f))
            .clickable { onRemove(r) }
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.name ?: ltr(r.address), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.compose_remove_recipient), tint = c.accent, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SuggestionRow(s: Suggestion, onClick: () -> Unit) {
    val c = HebTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (s.name != null) {
            Avatar(s.name, avatarColor(PhoneNumbers.normalize(s.number)), size = 42.dp)
        } else {
            Box(Modifier.size(42.dp).clip(CircleShape).background(c.header), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Dialpad, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.name ?: stringResource(R.string.compose_send_to_number),
                color = c.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(ltr(s.number), color = c.onSurfaceMuted, fontSize = 13.sp, maxLines = 1)
        }
    }
}
