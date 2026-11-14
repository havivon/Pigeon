package com.havivon.pigeon.ui.folders

import com.havivon.pigeon.ui.components.HeaderTitle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.folders.CategoryOrder
import com.havivon.pigeon.domain.folders.CategorySlot
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.ui.components.FixedHeaderLayout
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.inbox.CustomCategoryIcon
import com.havivon.pigeon.ui.inbox.categoryIcon
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.components.ReorderDragState
import com.havivon.pigeon.ui.components.listBottomPadding
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * All the categories in one list (Settings → Messages → Categories), the built-in ones and the user's together: the handle drags
 * any of them to a new place (the dropdown under the inbox header follows this order), the dot gives it a colour, and "Reset to
 * default order" puts them back. The user's own also have the pencil (rename) and the bin (delete: its conversations go back to
 * the automatic sorting); the built-in ones cannot be renamed or deleted. "New category" makes one (a name and an optional colour);
 * new ones are also made from "Move to category". "All categories" and Spam are not in the list: they are always first and last.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(
    folders: List<CustomFolder>,
    onBack: () -> Unit = {},
    onRename: (CustomFolder) -> Unit = {},
    onDelete: (CustomFolder) -> Unit = {},
    /** "New category": opens the name dialog. */
    onNew: () -> Unit = {},
    /** The colours the user gave to built-in folders. */
    builtInColors: Map<Folder, Long> = emptyMap(),
    /** The colour dot of a row was tapped: opens the picker for that built-in folder or category. */
    onColor: (FolderChoice) -> Unit = {},
    /** The saved order of all the categories (tokens of `CategoryOrder`); empty is the default order. */
    order: List<String> = emptyList(),
    /** The tokens in their new order, once a drag ends. */
    onReorder: (List<String>) -> Unit = {},
    /** "Reset to default order". */
    onResetOrder: () -> Unit = {},
    /** Draws this category of the user's lifted, as while it is dragged, without a finger on the screen (for the design snapshot). */
    previewDragging: Long? = null,
) {
    val c = HebTheme.colors
    val listState = rememberLazyListState()
    val drag = remember { ReorderDragState(listState) { it is String && (it.startsWith("b:") || it.startsWith("c:")) } }
    val slots = remember(order, folders) { CategoryOrder.resolve(order, folders) }
    // The order on screen while dragging; the repository's order again as soon as it changes
    var arranged by remember(slots) { mutableStateOf(slots) }
    val currentSlots by rememberUpdatedState(slots)
    val currentArranged by rememberUpdatedState(arranged)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val previewToken = previewDragging?.let { CategoryOrder.customToken(it) }

    FixedHeaderLayout(
        Modifier.fillMaxSize().background(c.background),
        header = {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
                    HeaderTitle(stringResource(R.string.folders_title), stringResource(R.string.folders_subtitle), Modifier.weight(1f))
                }
            }
        },
    ) { top ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = top, bottom = listBottomPadding(24.dp)),
        ) {
            item(key = "new") {
                PillButton(
                    stringResource(R.string.folders_add),
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
                    icon = Icons.Rounded.Add,
                    filled = true,
                    onClick = onNew,
                )
            }
            item(key = "reset") {
                PillButton(
                    stringResource(R.string.folders_reset_order),
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
                    icon = Icons.Rounded.RestartAlt,
                    onClick = onResetOrder,
                )
            }
            items(arranged, key = { it.token }) { slot ->
                val token = slot.token
                val lifted = drag.draggingKey == token || previewToken == token
                val cardModifier = if (drag.draggingKey == token) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = drag.translationOf(token) }
                } else {
                    Modifier.animateItemPlacement()
                }
                val handle: (Modifier) -> Modifier = { grip ->
                    grip.pointerInput(token) {
                        detectDragGestures(
                            onDragStart = { drag.begin(token) },
                            onDrag = { change, amount ->
                                change.consume()
                                drag.dragBy(amount.y, currentArranged) { it.token }?.let { arranged = it }
                            },
                            onDragEnd = {
                                drag.release()
                                val tokens = currentArranged.map { it.token }
                                if (tokens != currentSlots.map { it.token }) currentOnReorder(tokens)
                            },
                            onDragCancel = {
                                drag.release()
                                arranged = currentSlots
                            },
                        )
                    }
                }
                when (slot) {
                    is CategorySlot.BuiltIn -> {
                        val folder = slot.folder
                        val name = stringResource(folder.labelRes)
                        CategoryCard(
                            icon = categoryIcon(folder),
                            name = name,
                            argb = builtInColors[folder],
                            colorDescription = stringResource(R.string.folders_color, name),
                            lifted = lifted,
                            modifier = cardModifier,
                            handle = handle,
                            onColor = { onColor(FolderChoice.BuiltIn(folder)) },
                        )
                    }
                    is CategorySlot.Custom -> {
                        val folder = slot.folder
                        CategoryCard(
                            icon = CustomCategoryIcon,
                            name = folder.name,
                            argb = folder.argb,
                            colorDescription = stringResource(R.string.folders_color, folder.name),
                            lifted = lifted,
                            modifier = cardModifier,
                            handle = handle,
                            onColor = { onColor(FolderChoice.Custom(folder.id)) },
                        ) {
                            IconButton(onClick = { onRename(folder) }) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.folders_rename, folder.name), tint = c.onSurfaceMuted)
                            }
                            IconButton(onClick = { onDelete(folder) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.folders_delete, folder.name), tint = c.spam)
                            }
                        }
                    }
                }
            }
            if (folders.isEmpty()) {
                item(key = "none") {
                    Text(
                        stringResource(R.string.folders_none),
                        color = c.onSurfaceMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * One category of the list: the drag handle, its icon (in its colour, if it has one), its name, the colour dot, and [actions]
 * after the dot (the user's own categories: rename and delete). [handle] adds the drag gestures to the handle's modifier.
 */
@Composable
private fun CategoryCard(
    icon: ImageVector,
    name: String,
    argb: Long?,
    colorDescription: String,
    lifted: Boolean,
    modifier: Modifier,
    handle: (Modifier) -> Modifier,
    onColor: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
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
            .then(if (lifted) Modifier.border(3.dp, c.accent, shape) else Modifier)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A wide, tall target for the finger; the glyph itself is 24dp
        Box(handle(Modifier.size(width = 46.dp, height = 60.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.DragIndicator, contentDescription = stringResource(R.string.rules_drag), tint = c.onSurfaceMuted)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = argb?.let { Color(it) } ?: c.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            color = c.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ColorDot(argb, colorDescription, onColor)
        actions()
    }
}

/** The category's colour as a dot that opens the picker: filled with the colour, or an empty ring for none. */
@Composable
private fun ColorDot(argb: Long?, description: String, onClick: () -> Unit) {
    val c = HebTheme.colors
    IconButton(onClick = onClick) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .then(if (argb != null) Modifier.background(Color(argb)) else Modifier.border(2.dp, c.onSurfaceMuted, CircleShape))
                .semantics { contentDescription = description },
        )
    }
}

/** The colour picker of a category: the rules' swatches and "No colour"; a tap on either applies it at once. Apart from its window, for the snapshots. */
@Composable
fun CategoryColorDialogContent(name: String, selected: Long?, onPick: (Long?) -> Unit) {
    val c = HebTheme.colors
    androidx.compose.material3.Surface(shape = RoundedCornerShape(28.dp), color = c.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp)) {
            Text(stringResource(R.string.folder_color_title, name), color = c.onSurface, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            ColorChoice(selected, onPick)
        }
    }
}
