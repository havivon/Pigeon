package com.havivon.pigeon.ui.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
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
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.ui.components.FixedHeaderLayout
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.ReorderDragState
import com.havivon.pigeon.ui.components.SectionLabel
import com.havivon.pigeon.ui.components.listBottomPadding
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * The user's own categories (Settings → Messages → Categories): the handle drags one to a new place (the
 * chips follow this order), the pencil renames it and the bin deletes it (its conversations go back to the
 * automatic sorting). New ones are made from "Move to category".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(
    folders: List<CustomFolder>,
    onBack: () -> Unit = {},
    onRename: (CustomFolder) -> Unit = {},
    onDelete: (CustomFolder) -> Unit = {},
    /** The colours the user gave to built-in folders (the rows above their own categories). */
    builtInColors: Map<Folder, Long> = emptyMap(),
    /** The colour dot of a row was tapped: opens the picker for that built-in folder or category. */
    onColor: (FolderChoice) -> Unit = {},
    /** The ids in their new order, once a drag ends. */
    onReorder: (List<Long>) -> Unit = {},
    /** Draws this category lifted, as while it is dragged, without a finger on the screen (for the design snapshot). */
    previewDragging: Long? = null,
) {
    val c = HebTheme.colors
    val listState = rememberLazyListState()
    val drag = remember { ReorderDragState(listState) { it is Long } }
    // The order on screen while dragging; the repository's order again as soon as it changes
    var order by remember(folders) { mutableStateOf(folders) }
    val currentFolders by rememberUpdatedState(folders)
    val currentOrder by rememberUpdatedState(order)
    val currentOnReorder by rememberUpdatedState(onReorder)

    FixedHeaderLayout(
        Modifier.fillMaxSize().background(c.background),
        header = {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.folders_title), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(R.string.folders_subtitle), color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp)
                    }
                }
            }
        },
    ) { top ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = top, bottom = listBottomPadding(24.dp)),
        ) {
            item(key = "builtin") { SectionLabel(stringResource(R.string.folders_builtin_section)) }
            // The built-in categories: not renamed, deleted or moved, but they can have a colour
            items(MoveTargets, key = { "builtin:" + it.name }) { folder ->
                BuiltInCard(folder, builtInColors[folder], onColor = { onColor(FolderChoice.BuiltIn(folder)) })
            }
            item(key = "yours") { SectionLabel(stringResource(R.string.folders_section)) }
            if (order.isEmpty()) {
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
            items(order, key = { it.id }) { folder ->
                FolderCard(
                    folder,
                    lifted = drag.draggingKey == folder.id || previewDragging == folder.id,
                    onRename = { onRename(folder) },
                    onColor = { onColor(FolderChoice.Custom(folder.id)) },
                    onDelete = { onDelete(folder) },
                    modifier = if (drag.draggingKey == folder.id) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = drag.translationOf(folder.id) }
                    } else {
                        Modifier.animateItemPlacement()
                    },
                    handle = { grip ->
                        grip.pointerInput(folder.id) {
                            detectDragGestures(
                                onDragStart = { drag.begin(folder.id) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag.dragBy(amount.y, currentOrder) { it.id }?.let { order = it }
                                },
                                onDragEnd = {
                                    drag.release()
                                    val ids = currentOrder.map { it.id }
                                    if (ids != currentFolders.map { it.id }) currentOnReorder(ids)
                                },
                                onDragCancel = {
                                    drag.release()
                                    order = currentFolders
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: CustomFolder,
    lifted: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onColor: () -> Unit,
    modifier: Modifier,
    /** Adds the drag gestures to the handle's modifier. */
    handle: (Modifier) -> Modifier,
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
            Icon(Icons.AutoMirrored.Rounded.Label, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            folder.name,
            color = c.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ColorDot(folder.argb, stringResource(R.string.folders_color, folder.name), onColor)
        IconButton(onClick = onRename) {
            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.folders_rename, folder.name), tint = c.onSurfaceMuted)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.folders_delete, folder.name), tint = c.spam)
        }
    }
}

/** A built-in category (Personal, Banks, …): its name and its colour dot; it cannot be renamed, deleted or moved. */
@Composable
private fun BuiltInCard(folder: Folder, argb: Long?, onColor: () -> Unit) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val name = stringResource(folder.labelRes)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 2.dp, shape, spotColor = c.accent.copy(alpha = 0.25f))
            .clip(shape)
            .background(c.surface)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Icon(folderIcon(folder), contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(name, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        ColorDot(argb, stringResource(R.string.folders_color, name), onColor)
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
