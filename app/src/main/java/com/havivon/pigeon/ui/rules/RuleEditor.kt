package com.havivon.pigeon.ui.rules

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.components.LabeledTextField
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.components.RuleTag
import com.havivon.pigeon.ui.components.SheetTitle
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * The add/edit rule sheet's content, apart from its window so the design snapshots can draw it:
 * sender or word, the text, the tag, the colour, and how it will look. [onDelete] is null for a new rule.
 */
@Composable
fun RuleEditorSheetContent(
    state: RuleEditorState,
    onKind: (RuleKind) -> Unit,
    onText: (String) -> Unit,
    onLabel: (String) -> Unit,
    onColor: (Long) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = HebTheme.colors
    val color = Color(state.argb)
    val keyword = state.kind == RuleKind.Keyword
    Column(
        Modifier
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        SheetTitle(stringResource(if (state.editingId == null) R.string.rules_new_rule else R.string.rules_edit_rule))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.surfaceVariant).padding(3.dp)) {
            listOf(RuleKind.Sender to R.string.rules_kind_sender, RuleKind.Keyword to R.string.rules_kind_keyword).forEach { (kind, label) ->
                val on = state.kind == kind
                Text(
                    stringResource(label),
                    color = if (on) Color.White else c.onSurfaceMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .then(if (on) Modifier.background(c.header) else Modifier)
                        .clickable { onKind(kind) }
                        .padding(vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LabeledTextField(
            value = state.text,
            onValue = onText,
            label = stringResource(if (keyword) R.string.rules_field_word else R.string.rules_field_sender),
            hint = stringResource(if (keyword) R.string.rules_hint_word else R.string.rules_hint_sender),
            emphasized = true,
        )
        Spacer(Modifier.height(10.dp))
        LabeledTextField(
            value = state.label,
            onValue = onLabel,
            label = stringResource(R.string.rules_field_label),
            hint = state.text.trim().take(MAX_RULE_LABEL).ifEmpty { stringResource(R.string.rules_hint_label) },
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RuleSwatches.forEach { argb ->
                val selected = argb == state.argb
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .then(if (selected) Modifier.border(3.dp, c.onSurface, CircleShape) else Modifier)
                        .clickable { onColor(argb) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.rules_swatch_selected), tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.rules_preview_label), color = c.onSurfaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        val previewTag = state.tag.ifEmpty { stringResource(R.string.rules_preview_sample) }
        val previewWord = state.text.trim().ifEmpty { stringResource(R.string.rules_preview_sample) }
        val bubble = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (keyword) stringResource(R.string.rules_preview_message, previewWord) else stringResource(R.string.rules_preview_sender_message, previewWord),
                color = c.onSurface,
                fontSize = 14.sp,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(bubble)
                    .background(c.background)
                    .border(2.dp, color, bubble)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(Modifier.width(8.dp))
            RuleTag(previewTag, color)
        }
        Spacer(Modifier.height(16.dp))
        PillButton(
            stringResource(R.string.rules_save),
            Modifier.fillMaxWidth().alpha(if (state.canSave) 1f else 0.4f),
            filled = true,
            onClick = { if (state.canSave) onSave() },
        )
        if (onDelete != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).clickable(onClick = onDelete).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = c.spam, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.rules_delete), color = c.spam, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
