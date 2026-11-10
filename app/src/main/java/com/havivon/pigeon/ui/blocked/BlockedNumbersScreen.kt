package com.havivon.pigeon.ui.blocked

import com.havivon.pigeon.ui.components.HeaderTitle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.common.ltr
import com.havivon.pigeon.ui.components.FixedHeaderLayout
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.LabeledTextField
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.components.SectionLabel
import com.havivon.pigeon.ui.components.listBottomPadding
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * The phone's blocked numbers: type one and block it; each number on the list has an unblock button. Without the
 * default SMS app role Android does not show the list, and the screen says so.
 */
@Composable
fun BlockedNumbersScreen(
    state: BlockedNumbersState,
    onDraft: (String) -> Unit = {},
    onAdd: () -> Unit = {},
    onUnblock: (BlockedEntry) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val c = HebTheme.colors
    FixedHeaderLayout(
        Modifier.fillMaxSize().background(c.background),
        header = {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
                    HeaderTitle(stringResource(R.string.blocked_title), stringResource(R.string.blocked_subtitle), Modifier.weight(1f))
                }
            }
        },
    ) { top ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = top, bottom = listBottomPadding(24.dp)),
        ) {
            if (!state.available) {
                item { UnavailableCard() }
                return@LazyColumn
            }
            item { AddNumberCard(state.draft, state.problem, onDraft, onAdd) }
            item {
                SectionLabel(
                    if (state.entries.isEmpty()) stringResource(R.string.blocked_section_empty)
                    else pluralStringResource(R.plurals.blocked_section, state.entries.size, state.entries.size),
                )
            }
            if (state.entries.isEmpty() && state.loaded) {
                item {
                    Text(
                        stringResource(R.string.blocked_none),
                        color = c.onSurfaceMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
            }
            items(state.entries, key = { it.number }) { NumberRow(it) { onUnblock(it) } }
        }
    }
}

@Composable
private fun cardModifier(): Modifier {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(22.dp)
    return Modifier
        .padding(horizontal = 12.dp, vertical = 14.dp)
        .fillMaxWidth()
        .shadow(if (c.isDark) 0.dp else 3.dp, shape, spotColor = c.accent.copy(alpha = 0.3f))
        .clip(shape)
        .background(c.surface)
        .padding(14.dp)
}

@Composable
private fun AddNumberCard(draft: String, problem: BlockProblem?, onDraft: (String) -> Unit, onAdd: () -> Unit) {
    val c = HebTheme.colors
    Column(cardModifier()) {
        Text(stringResource(R.string.blocked_info), color = c.onSurfaceMuted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.size(12.dp))
        LabeledTextField(
            value = draft,
            onValue = onDraft,
            label = stringResource(R.string.blocked_field),
            hint = stringResource(R.string.blocked_hint),
            emphasized = true,
            onDone = onAdd,
            keyboardType = KeyboardType.Phone,
        )
        val message = when (problem) {
            BlockProblem.Invalid -> stringResource(R.string.blocked_invalid)
            BlockProblem.Duplicate -> stringResource(R.string.blocked_duplicate)
            BlockProblem.Failed -> stringResource(R.string.blocked_failed)
            null -> null
        }
        if (message != null) {
            Text(message, color = c.spam, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        }
        Spacer(Modifier.size(12.dp))
        PillButton(
            stringResource(R.string.blocked_add),
            Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Block,
            filled = true,
            onClick = onAdd,
        )
    }
}

@Composable
private fun UnavailableCard() {
    val c = HebTheme.colors
    Row(cardModifier(), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.blocked_unavailable_title), color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.blocked_unavailable_body), color = c.onSurfaceMuted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun NumberRow(entry: BlockedEntry, onUnblock: () -> Unit) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val shown = entry.name ?: ltr(entry.number)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 2.dp, shape, spotColor = c.accent.copy(alpha = 0.25f))
            .clip(shape)
            .background(c.surface)
            .border(2.dp, c.spam.copy(alpha = 0.55f), shape)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.spam.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PhoneDisabled, contentDescription = null, tint = c.spam, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(shown, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.name != null) Text(ltr(entry.number), color = c.onSurfaceMuted, fontSize = 13.sp, maxLines = 1)
        }
        IconButton(onClick = onUnblock) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.blocked_unblock, shown), tint = c.onSurfaceMuted)
        }
    }
}
