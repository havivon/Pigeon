package com.havivon.pigeon.ui.keywords

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.model.BlockedKeyword
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.LabeledTextField
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.components.SectionLabel
import com.havivon.pigeon.ui.theme.HebTheme

/** The blocked words: type one and add it; each word on the list has a remove button. */
@Composable
fun BlockedKeywordsScreen(
    keywords: List<BlockedKeyword>,
    draft: String,
    duplicate: Boolean,
    onDraft: (String) -> Unit = {},
    onAdd: () -> Unit = {},
    onDelete: (BlockedKeyword) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val c = HebTheme.colors
    LazyColumn(Modifier.fillMaxSize().background(c.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.keywords_title), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(R.string.keywords_subtitle), color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp)
                    }
                }
            }
        }
        item { AddWordCard(draft, duplicate, onDraft, onAdd) }
        item {
            SectionLabel(
                if (keywords.isEmpty()) stringResource(R.string.keywords_section_empty)
                else pluralStringResource(R.plurals.keywords_section, keywords.size, keywords.size),
            )
        }
        if (keywords.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.keywords_none),
                    color = c.onSurfaceMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
        items(keywords, key = { it.id }) { WordRow(it) { onDelete(it) } }
    }
}

@Composable
private fun AddWordCard(draft: String, duplicate: Boolean, onDraft: (String) -> Unit, onAdd: () -> Unit) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 3.dp, shape, spotColor = c.accent.copy(alpha = 0.3f))
            .clip(shape)
            .background(c.surface)
            .padding(14.dp),
    ) {
        Text(stringResource(R.string.keywords_info), color = c.onSurfaceMuted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.size(12.dp))
        LabeledTextField(
            value = draft,
            onValue = onDraft,
            label = stringResource(R.string.keywords_field),
            hint = stringResource(R.string.keywords_hint),
            emphasized = true,
            onDone = onAdd,
        )
        if (duplicate) {
            Text(stringResource(R.string.keywords_duplicate), color = c.spam, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        }
        Spacer(Modifier.size(12.dp))
        PillButton(
            stringResource(R.string.keywords_add),
            Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Add,
            filled = true,
            onClick = onAdd,
        )
    }
}

@Composable
private fun WordRow(keyword: BlockedKeyword, onDelete: () -> Unit) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(20.dp)
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
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.spam.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Block, contentDescription = null, tint = c.spam, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(keyword.word, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.keywords_remove, keyword.word), tint = c.onSurfaceMuted)
        }
    }
}
