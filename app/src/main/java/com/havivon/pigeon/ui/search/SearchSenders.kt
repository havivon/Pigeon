package com.havivon.pigeon.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.components.Avatar
import com.havivon.pigeon.ui.components.EmptyState
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.SectionLabel
import com.havivon.pigeon.ui.components.listBottomPadding
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * The "Sender" chip's search, over the whole search screen: a field under the status bar ("Type a name or number", focused
 * at once) and, under it, the senders it means, drawn from everyone who ever wrote or was written to. While the field is
 * empty these are the newest senders. A tap on one chooses it ([onPick]); Back or the arrow leaves ([onClose]).
 */
@Composable
fun SenderSearchContent(
    query: String,
    suggestions: List<SenderSuggestion>,
    onQuery: (String) -> Unit,
    onPick: (SenderSuggestion) -> Unit,
    onClose: () -> Unit,
    /** False in the design snapshots, where there is no keyboard to open. */
    autoFocus: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val c = HebTheme.colors
    Column(modifier.fillMaxSize().background(c.background).imePadding()) {
        GradientHeader {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.search_sender_close), onClose)
                SenderField(query, onQuery, autoFocus, Modifier.weight(1f))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = listBottomPadding(24.dp))) {
            if (suggestions.isEmpty()) {
                item {
                    EmptyState(Icons.Rounded.PersonSearch, stringResource(R.string.search_sender_none_title), stringResource(R.string.search_sender_none_body))
                }
            } else {
                item {
                    SectionLabel(
                        stringResource(if (query.isBlank()) R.string.search_sender_recent else R.string.search_sender_matches),
                        Icons.Rounded.Person,
                    )
                }
                items(suggestions, key = { it.sender.key }) { SenderRow(it, onClick = { onPick(it) }) }
            }
        }
    }
}

/** The glassy rounded field, as the search bar draws it; its X empties the text. */
@Composable
private fun SenderField(query: String, onQuery: (String) -> Unit, autoFocus: Boolean, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    val hint = stringResource(R.string.search_sender_hint)
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text(hint, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                },
            )
        }
        if (query.isNotEmpty()) HeaderIcon(Icons.Rounded.Close, stringResource(R.string.search_sender_clear_text)) { onQuery("") }
    }
}

/** A sender: the avatar, the name (or the number), then the number under a name, how many conversations and when the last message was. */
@Composable
private fun SenderRow(s: SenderSuggestion, onClick: () -> Unit) {
    val c = HebTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(s.title, s.avatarArgb, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val count = if (s.conversations > 1) pluralStringResource(R.plurals.search_sender_conversations, s.conversations, s.conversations) else null
            val details = listOfNotNull(s.address, count, s.time).joinToString(" · ")
            Text(details, color = c.onSurfaceMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
