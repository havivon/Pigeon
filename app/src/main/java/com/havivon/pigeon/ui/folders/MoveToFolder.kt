package com.havivon.pigeon.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.folders.FolderKeys
import com.havivon.pigeon.domain.folders.FolderNameProblem
import com.havivon.pigeon.domain.folders.FolderNames
import com.havivon.pigeon.domain.model.Conversation
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.domain.model.FolderChoice
import com.havivon.pigeon.ui.common.currentFolder
import com.havivon.pigeon.ui.components.Avatar
import com.havivon.pigeon.ui.components.LabeledTextField
import com.havivon.pigeon.ui.components.SheetTitle
import com.havivon.pigeon.ui.components.SpamAvatar
import com.havivon.pigeon.ui.rules.SwatchRow
import com.havivon.pigeon.ui.theme.HebTheme

/** The built-in folders a conversation can be moved to, in the chips' order ("All" holds everything, Spam comes last). */
val MoveTargets = listOf(Folder.Personal, Folder.Banks, Folder.Insurance, Folder.Codes, Folder.Deliveries)

/** The icon of a built-in folder in the "Move to category" sheet. */
fun folderIcon(folder: Folder): ImageVector = when (folder) {
    Folder.All -> Icons.Rounded.Forum
    Folder.Personal -> Icons.Rounded.Person
    Folder.Banks -> Icons.Rounded.AccountBalance
    Folder.Insurance -> Icons.Rounded.HealthAndSafety
    Folder.Codes -> Icons.Rounded.Key
    Folder.Deliveries -> Icons.Rounded.LocalShipping
    Folder.Spam -> Icons.Rounded.Report
}

/** The name of a chip or a move target, as the user sees it. */
@Composable
fun folderLabel(choice: FolderChoice?, customFolders: List<CustomFolder>): String = when (choice) {
    null -> stringResource(R.string.move_folder_automatic)
    is FolderChoice.BuiltIn -> stringResource(choice.folder.labelRes)
    is FolderChoice.Custom -> customFolders.firstOrNull { it.id == choice.id }?.name.orEmpty()
}

/**
 * "Move to category": the sheet and, for "New category…", the name dialog. [onMove] gets the chosen place
 * (null for "Automatic") and [onCreate] the new category's name; either closes it all ([onDismiss]).
 * Choosing where the conversation already is by hand changes nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToFolderFlow(
    conversation: Conversation,
    customFolders: List<CustomFolder>,
    problemOf: (String) -> FolderNameProblem?,
    onMove: (FolderChoice?) -> Unit,
    onCreate: (name: String, argb: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    if (naming) {
        FolderNameDialog(
            title = stringResource(R.string.folder_new_title),
            confirm = stringResource(R.string.folder_create),
            initial = "",
            problemOf = problemOf,
            withColor = true,
            onConfirm = { name, argb ->
                onDismiss()
                onCreate(name, argb)
            },
            onDismiss = onDismiss,
        )
        return
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = HebTheme.colors.surface) {
        MoveToFolderContent(
            conversation,
            customFolders,
            onChoose = { target ->
                onDismiss()
                val unchanged = target == currentFolder(conversation) && (conversation.folderOverridden || conversation.isSpam)
                if (!unchanged) onMove(target)
            },
            onNew = { naming = true },
        )
    }
}

/** The sheet's content: apart from its window, so the design snapshots can draw it. */
@Composable
fun MoveToFolderContent(
    conversation: Conversation,
    customFolders: List<CustomFolder>,
    /** The chosen place; null is "Automatic". */
    onChoose: (FolderChoice?) -> Unit,
    onNew: () -> Unit,
) {
    val current = currentFolder(conversation)
    val subtitle = if (FolderKeys.bySender(conversation.addresses)) {
        stringResource(R.string.move_folder_sender, conversation.name)
    } else {
        stringResource(R.string.move_folder_group)
    }
    Column(
        Modifier
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 16.dp),
    ) {
        SheetTitle(stringResource(R.string.action_move_to_folder), subtitle) {
            if (conversation.isSpam) SpamAvatar(size = 42.dp) else Avatar(conversation.name, conversation.avatarArgb, size = 42.dp)
        }
        Spacer(Modifier.height(6.dp))
        for (folder in MoveTargets) {
            val choice = FolderChoice.BuiltIn(folder)
            TargetRow(folderIcon(folder), stringResource(folder.labelRes), checked = current == choice) { onChoose(choice) }
        }
        if (customFolders.isNotEmpty()) {
            RowsDivider()
            for (f in customFolders) {
                val choice = FolderChoice.Custom(f.id)
                TargetRow(Icons.AutoMirrored.Rounded.Label, f.name, checked = current == choice) { onChoose(choice) }
            }
        }
        RowsDivider()
        val spam = FolderChoice.BuiltIn(Folder.Spam)
        TargetRow(folderIcon(Folder.Spam), stringResource(Folder.Spam.labelRes), checked = current == spam, danger = true) { onChoose(spam) }
        if (conversation.folderOverridden) {
            TargetRow(Icons.Rounded.AutoAwesome, stringResource(R.string.move_folder_automatic), checked = false) { onChoose(null) }
        }
        TargetRow(Icons.Rounded.Add, stringResource(R.string.move_folder_new), checked = false, onClick = onNew)
    }
}

@Composable
private fun RowsDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = HebTheme.colors.divider)
}

/** One place in the sheet, as a sheet action: an icon in a soft circle, the name, and a check on the current one. */
@Composable
private fun TargetRow(icon: ImageVector, label: String, checked: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val c = HebTheme.colors
    val color = if (danger) c.spam else c.accent
    val current = stringResource(R.string.move_folder_current)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) color.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = if (danger) c.spam else c.onSurface,
            fontSize = 16.sp,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (checked) Icon(Icons.Rounded.Check, contentDescription = current, tint = color, modifier = Modifier.size(22.dp))
    }
}

/**
 * The name of a category, new or renamed, in a dialog: [problemOf] says what is wrong with the typed name
 * (blank, taken), and [onConfirm] is offered only when nothing is.
 */
@Composable
fun FolderNameDialog(
    title: String,
    confirm: String,
    initial: String,
    problemOf: (String) -> FolderNameProblem?,
    onConfirm: (name: String, argb: Long?) -> Unit,
    onDismiss: () -> Unit,
    /** Offers the optional colour (a new category); a rename keeps the colour it has. */
    withColor: Boolean = false,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var argb by rememberSaveable { mutableStateOf<Long?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        FolderNameDialogContent(
            title, confirm, name, problemOf(name), onName = { name = it }, onConfirm = { onConfirm(name, argb) }, onDismiss = onDismiss, autoFocus = true,
            argb = argb, onColor = if (withColor) ({ argb = it }) else null,
        )
    }
}

/** The dialog's card: apart from its window, so the design snapshots can draw it. */
@Composable
fun FolderNameDialogContent(
    title: String,
    confirm: String,
    name: String,
    problem: FolderNameProblem?,
    onName: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    autoFocus: Boolean = false,
    /** The chosen colour of a new category (null for none) and its change; null [onColor] leaves the colour row out. */
    argb: Long? = null,
    onColor: ((Long?) -> Unit)? = null,
) {
    val c = HebTheme.colors
    Surface(shape = RoundedCornerShape(28.dp), color = c.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 10.dp)) {
            Text(title, color = c.onSurface, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(16.dp))
            LabeledTextField(
                value = name,
                onValue = { onName(it.take(FolderNames.MAX_LENGTH)) },
                label = stringResource(R.string.folder_name_label),
                hint = stringResource(R.string.folder_name_hint),
                emphasized = true,
                onDone = { if (problem == null) onConfirm() },
                autoFocus = autoFocus,
            )
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val message = when (problem) {
                    FolderNameProblem.Taken -> stringResource(R.string.folder_name_taken)
                    FolderNameProblem.TooLong -> pluralStringResource(R.plurals.folder_name_too_long, FolderNames.MAX_LENGTH, FolderNames.MAX_LENGTH)
                    FolderNameProblem.Blank, null -> null
                }
                Text(message.orEmpty(), color = c.spam, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${name.length}/${FolderNames.MAX_LENGTH}", color = c.onSurfaceMuted, fontSize = 12.sp)
            }
            if (onColor != null) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.folder_color_label), color = c.onSurfaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ColorChoice(argb, onColor)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = c.accent) }
                TextButton(onClick = onConfirm, enabled = problem == null) {
                    Text(confirm, color = if (problem == null) c.accent else c.onSurfaceMuted, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * The colour of a category: the rules' swatches, and "No colour" (the default). [selected] null means no colour.
 */
@Composable
fun ColorChoice(selected: Long?, onChange: (Long?) -> Unit) {
    val c = HebTheme.colors
    Column {
        SwatchRow(selected, onPick = { onChange(it) })
        Spacer(Modifier.height(10.dp))
        val none = selected == null
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (none) c.accent.copy(alpha = 0.13f) else c.surfaceVariant)
                .clickable { onChange(null) }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (none) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                stringResource(R.string.folder_color_none),
                color = if (none) c.accent else c.onSurface,
                fontSize = 14.sp,
                fontWeight = if (none) FontWeight.Bold else FontWeight.SemiBold,
            )
        }
    }
}
