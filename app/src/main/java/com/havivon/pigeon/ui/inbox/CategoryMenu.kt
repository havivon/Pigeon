package com.havivon.pigeon.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.model.CustomFolder
import com.havivon.pigeon.domain.model.Folder
import com.havivon.pigeon.ui.theme.HebTheme

/** One line of the category menu under the inbox header. */
sealed interface CategoryEntry {
    /** A built-in folder (never Spam, which has its own entry). */
    data class BuiltIn(val folder: Folder, val count: Int, val argb: Long?, val selected: Boolean) : CategoryEntry

    /** One of the user's own categories. */
    data class Custom(val folder: CustomFolder, val count: Int, val selected: Boolean) : CategoryEntry

    /** Spam, after a divider: opens the spam screen. [count] is the number of spam conversations. */
    data class Spam(val count: Int) : CategoryEntry

    /** "Manage categories…": opens the categories screen. */
    data object Manage : CategoryEntry
}

/** What a tap on a [CategoryEntry] does. */
sealed interface CategoryAction {
    data class Select(val folder: Folder) : CategoryAction
    data class SelectCustom(val folder: CustomFolder) : CategoryAction
    data object OpenSpam : CategoryAction
    data object ManageCategories : CategoryAction
}

/**
 * The menu's lines, in order: the built-in folders (All, Personal, Banks, Insurance, Codes, Deliveries), the user's
 * categories in their order, then Spam and "Manage categories…". The chosen one is [selectedCustom] when it is one of
 * [custom], else [selected] (a deleted category of the user's falls back to All).
 */
fun categoryMenu(
    counts: Map<Folder, Int>,
    custom: List<CustomFolder>,
    customCounts: Map<Long, Int>,
    selected: Folder,
    selectedCustom: Long?,
    builtInColors: Map<Folder, Long> = emptyMap(),
): List<CategoryEntry> {
    val customChosen = selectedCustom != null && custom.any { it.id == selectedCustom }
    val builtIn = if (customChosen) null else selected.takeUnless { it == Folder.Spam } ?: Folder.All
    return buildList {
        Folder.entries.filter { it != Folder.Spam }.forEach { f ->
            add(CategoryEntry.BuiltIn(f, counts[f] ?: 0, builtInColors[f], selected = f == builtIn))
        }
        custom.forEach { f -> add(CategoryEntry.Custom(f, customCounts[f.id] ?: 0, selected = customChosen && f.id == selectedCustom)) }
        add(CategoryEntry.Spam(counts[Folder.Spam] ?: 0))
        add(CategoryEntry.Manage)
    }
}

/** The entry the dropdown button shows: the chosen one (All when, oddly, none is). */
fun List<CategoryEntry>.current(): CategoryEntry = firstOrNull {
    (it is CategoryEntry.BuiltIn && it.selected) || (it is CategoryEntry.Custom && it.selected)
} ?: first { it is CategoryEntry.BuiltIn && it.folder == Folder.All }

fun CategoryEntry.action(): CategoryAction = when (this) {
    is CategoryEntry.BuiltIn -> CategoryAction.Select(folder)
    is CategoryEntry.Custom -> CategoryAction.SelectCustom(folder)
    is CategoryEntry.Spam -> CategoryAction.OpenSpam
    CategoryEntry.Manage -> CategoryAction.ManageCategories
}

@Composable
private fun CategoryEntry.label(): String = when (this) {
    is CategoryEntry.BuiltIn -> stringResource(folder.labelRes)
    is CategoryEntry.Custom -> folder.name
    is CategoryEntry.Spam -> stringResource(R.string.common_folder_spam)
    CategoryEntry.Manage -> stringResource(R.string.inbox_manage_categories)
}

private val CategoryEntry.count: Int
    get() = when (this) {
        is CategoryEntry.BuiltIn -> count
        is CategoryEntry.Custom -> count
        is CategoryEntry.Spam -> count
        CategoryEntry.Manage -> 0
    }

private val CategoryEntry.argb: Long?
    get() = when (this) {
        is CategoryEntry.BuiltIn -> argb
        is CategoryEntry.Custom -> folder.argb
        else -> null
    }

/**
 * Hands [this] to the inbox's callbacks: a folder or a category of the user's is chosen in place; Spam goes through
 * [onFolder] as [Folder.Spam], which opens the spam screen (as its chip did); "Manage categories…" calls [onManage].
 */
fun CategoryAction.dispatch(onFolder: (Folder) -> Unit, onCustomFolder: (CustomFolder) -> Unit, onManage: () -> Unit) = when (this) {
    is CategoryAction.Select -> onFolder(folder)
    is CategoryAction.SelectCustom -> onCustomFolder(folder)
    CategoryAction.OpenSpam -> onFolder(Folder.Spam)
    CategoryAction.ManageCategories -> onManage()
}

/**
 * The chosen category as a white pill on the header (its colour dot, its name, its number and ▾); a tap opens the
 * menu of [entries] right under it.
 */
@Composable
internal fun CategoryDropdown(entries: List<CategoryEntry>, onAction: (CategoryAction) -> Unit) {
    val c = HebTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val current = entries.current()
    val onWhite = c.onWhite
    val description = stringResource(R.string.inbox_category_button_description, current.label(), current.count)
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .clickable { open = true }
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }
                .padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dot = current.argb
            if (dot != null) {
                ColourDot(dot)
                Spacer(Modifier.width(7.dp))
            }
            // The name is cut with "…" first: the number and the chevron always show
            Text(
                current.label(),
                color = onWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (current.count > 0) {
                Spacer(Modifier.width(6.dp))
                CountDot(current.count, selected = true)
            }
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = onWhite, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(c.surface)) {
            CategoryMenuItems(entries) {
                open = false
                onAction(it)
            }
        }
    }
}

/** The menu's lines: the categories, a divider, Spam and "Manage categories…". Apart from the popup, for the design snapshots. */
@Composable
internal fun CategoryMenuItems(entries: List<CategoryEntry>, onAction: (CategoryAction) -> Unit) {
    entries.forEach { entry ->
        if (entry is CategoryEntry.Spam) HorizontalDivider(color = HebTheme.colors.divider)
        CategoryMenuItem(entry) { onAction(entry.action()) }
    }
}

@Composable
private fun CategoryMenuItem(entry: CategoryEntry, onClick: () -> Unit) {
    val c = HebTheme.colors
    val chosen = (entry as? CategoryEntry.BuiltIn)?.selected == true || (entry as? CategoryEntry.Custom)?.selected == true
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                entry.argb?.let {
                    ColourDot(it)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    entry.label(),
                    color = if (entry is CategoryEntry.Spam) c.spam else c.onSurface,
                    fontSize = 15.sp,
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingIcon = {
            when {
                chosen -> Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.inbox_category_chosen), tint = c.accent)
                entry is CategoryEntry.Spam -> Icon(Icons.Rounded.Warning, contentDescription = null, tint = c.spam)
                entry is CategoryEntry.Manage -> Icon(Icons.Rounded.Tune, contentDescription = null, tint = c.accent)
                else -> Spacer(Modifier.size(24.dp))
            }
        },
        trailingIcon = if (entry.count > 0) ({ MenuCount(entry.count) }) else null,
        onClick = onClick,
    )
}

/** A category's colour, ringed in white so it shows on white and on the header alike. */
@Composable
private fun ColourDot(argb: Long) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(argb)).border(1.5.dp, Color.White, CircleShape))
}

/** The number at the end of a menu line, as a soft pill that grows with the font. */
@Composable
private fun MenuCount(count: Int) {
    val c = HebTheme.colors
    Box(
        Modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(CircleShape)
            .background(c.accent.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
