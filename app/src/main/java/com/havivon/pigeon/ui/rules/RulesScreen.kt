package com.havivon.pigeon.ui.rules

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.model.ColorRule
import com.havivon.pigeon.domain.model.RuleMatch
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.components.SectionLabel
import com.havivon.pigeon.ui.theme.HebTheme

val RuleSwatches = listOf(
    0xFF1E9E6A, 0xFF14A3B8, 0xFF2F80ED, 0xFF5B3FD9, 0xFFC2408F,
    0xFFE0559B, 0xFFF08A24, 0xFFE8B10C, 0xFF8D6E63,
)

/**
 * The colour rules: tap one to edit it, the switch turns it on or off, the handle drags it to a new
 * place (the top rule wins), and "Add rule" opens the editor. The editor itself is a sheet
 * ([RuleEditorSheetContent]), shown by the route.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RulesScreen(
    rules: List<ColorRule>,
    onBack: () -> Unit = {},
    onToggle: (ColorRule, Boolean) -> Unit = { _, _ -> },
    onEdit: (ColorRule) -> Unit = {},
    onAdd: () -> Unit = {},
    /** The rule ids in their new order, once a drag ends. */
    onReorder: (List<Int>) -> Unit = {},
    /** Draws this rule lifted, as while it is dragged, without a finger on the screen (for the design snapshot). */
    previewDragging: Int? = null,
) {
    val c = HebTheme.colors
    val listState = rememberLazyListState()
    val drag = remember { RuleDragState(listState) }
    // The order on screen while dragging; the repository's order again as soon as it changes
    var order by remember(rules) { mutableStateOf(rules) }
    val currentRules by rememberUpdatedState(rules)
    val currentOrder by rememberUpdatedState(order)
    val currentOnReorder by rememberUpdatedState(onReorder)

    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.rules_title), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(R.string.rules_subtitle), color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp)
                    }
                }
            }
        }
        item(key = "yours") { SectionLabel(stringResource(R.string.rules_section_yours)) }
        if (order.isEmpty()) item(key = "none") { NoRules() }
        items(order, key = { it.id }) { rule ->
            val lifted = drag.draggingId == rule.id || previewDragging == rule.id
            RuleCard(
                rule,
                lifted = lifted,
                onClick = { onEdit(rule) },
                onToggle = { on -> onToggle(rule, on) },
                modifier = if (drag.draggingId == rule.id) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = drag.translationOf(rule.id) }
                } else {
                    Modifier.animateItemPlacement()
                },
                handle = { grip ->
                    grip.pointerInput(rule.id) {
                        detectDragGestures(
                            onDragStart = { drag.begin(rule.id) },
                            onDrag = { change, amount ->
                                change.consume()
                                drag.dragBy(amount.y, currentOrder)?.let { order = it }
                            },
                            onDragEnd = {
                                drag.release()
                                val ids = currentOrder.map { it.id }
                                if (ids != currentRules.map { it.id }) currentOnReorder(ids)
                            },
                            onDragCancel = {
                                drag.release()
                                order = currentRules
                            },
                        )
                    }
                },
            )
        }
        item(key = "spam") { SpamRuleCard() }
        item(key = "add") {
            PillButton(
                stringResource(R.string.rules_add),
                Modifier.padding(horizontal = 12.dp, vertical = 16.dp).fillMaxWidth(),
                icon = Icons.Rounded.Add,
                filled = true,
                onClick = onAdd,
            )
        }
    }
}

/**
 * Drag-to-reorder for the rule list. The dragged card follows the finger by its offset from where it
 * would sit; when its middle crosses a neighbour the two swap places in the order (one swap at a
 * time, until the list has laid out again).
 */
@Stable
private class RuleDragState(private val list: LazyListState) {
    var draggingId by mutableStateOf<Int?>(null)
        private set
    private var distance by mutableFloatStateOf(0f)
    private var startOffset = 0f
    private var awaitingLayoutFrom: Int? = null

    fun begin(id: Int) {
        val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        draggingId = id
        startOffset = info.offset.toFloat()
        distance = 0f
        awaitingLayoutFrom = null
    }

    /** How far the dragged card is from its place in the list right now. */
    fun translationOf(id: Int): Float {
        val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return 0f
        return startOffset + distance - info.offset
    }

    /** The order after moving the finger by [delta] pixels, or null when no neighbour was crossed. */
    fun dragBy(delta: Float, order: List<ColorRule>): List<ColorRule>? {
        val id = draggingId ?: return null
        distance += delta
        val visible = list.layoutInfo.visibleItemsInfo
        val me = visible.firstOrNull { it.key == id } ?: return null
        if (awaitingLayoutFrom == me.index) return null
        awaitingLayoutFrom = null
        val middle = startOffset + distance + me.size / 2f
        val target = visible.firstOrNull { it.key is Int && it.key != id && middle >= it.offset && middle <= it.offset + it.size } ?: return null
        val from = order.indexOfFirst { it.id == id }
        val to = order.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0 || from == to) return null
        awaitingLayoutFrom = me.index
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun release() {
        draggingId = null
        distance = 0f
        awaitingLayoutFrom = null
    }
}

@Composable
private fun NoRules() {
    val c = HebTheme.colors
    Text(
        stringResource(R.string.rules_none),
        color = c.onSurfaceMuted,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun RuleCard(
    rule: ColorRule,
    lifted: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier,
    /** Adds the drag gestures to the handle's modifier. */
    handle: (Modifier) -> Modifier,
) {
    val color = Color(rule.argb)
    val (icon, subtitle) = when (val m = rule.match) {
        is RuleMatch.Sender -> Icons.Rounded.Person to stringResource(R.string.rules_sender_subtitle, m.name)
        is RuleMatch.Keyword -> Icons.Rounded.Tag to stringResource(R.string.rules_keyword_subtitle, m.word)
    }
    RuleRow(
        icon, rule.label, subtitle, if (rule.enabled) color else HebTheme.colors.divider, color, rule.enabled,
        lifted = lifted, handle = handle, onClick = onClick, onToggle = onToggle, modifier = modifier,
    )
}

/** The built-in spam rule: shown so the user knows what red means, but spam is always on, so it has no switch. */
@Composable
private fun SpamRuleCard() {
    val spam = HebTheme.colors.spam
    RuleRow(
        Icons.Rounded.Warning,
        stringResource(R.string.rules_spam_title),
        stringResource(R.string.rules_spam_subtitle),
        spam,
        spam,
        enabled = true,
        lifted = false,
        handle = null,
        onClick = null,
        onToggle = null,
    )
}

@Composable
private fun RuleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    frame: Color,
    color: Color,
    enabled: Boolean,
    lifted: Boolean,
    /** Adds the drag gestures to the handle's modifier; null shows no handle. */
    handle: ((Modifier) -> Modifier)?,
    /** Null makes the row not tappable. */
    onClick: (() -> Unit)?,
    /** Null hides the switch. */
    onToggle: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .shadow(if (lifted) 14.dp else if (c.isDark) 0.dp else 2.dp, shape, spotColor = c.accent.copy(alpha = if (lifted) 0.6f else 0.25f))
            .clip(shape)
            .background(if (lifted) c.surfaceVariant else c.surface)
            .border(if (lifted) 3.dp else 2.dp, frame, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (handle != null) {
            // A wide, tall target for the finger; the glyph itself is 24dp
            Box(handle(Modifier.size(width = 46.dp, height = 56.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DragIndicator, contentDescription = stringResource(R.string.rules_drag), tint = c.onSurfaceMuted)
            }
        } else {
            Spacer(Modifier.width(46.dp))
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = if (enabled) 1f else 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(title, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = c.onSurfaceMuted, fontSize = 13.sp)
        }
        if (onToggle != null) Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = color, checkedThumbColor = Color.White),
        )
    }
}
