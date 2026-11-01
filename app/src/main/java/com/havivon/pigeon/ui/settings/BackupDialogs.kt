package com.havivon.pigeon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.backup.BackupSummary
import com.havivon.pigeon.ui.theme.HebTheme
import java.text.DateFormat
import java.util.Date

/** The confirmation before an import: what the file holds, and that importing replaces the current settings. */
@Composable
fun ImportConfirmDialog(summary: BackupSummary, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) { ImportConfirmContent(summary, onConfirm, onDismiss) }
}

/** The dialog's card, apart from its window so a screenshot test can draw it in place. */
@Composable
internal fun ImportConfirmContent(summary: BackupSummary, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = HebTheme.colors
    val rows = listOf(
        R.string.backup_item_settings to summary.settings,
        R.string.settings_color_rules to summary.rules,
        R.string.settings_folders to summary.categories,
        R.string.backup_item_moves to summary.moves,
        R.string.backup_item_pinned to summary.pinned,
        R.string.backup_item_locked to summary.locked,
        R.string.backup_item_not_spam to summary.notSpam,
        R.string.settings_blocked_words to summary.blockedWords,
        R.string.backup_item_reminders to summary.reminders,
        R.string.backup_item_favorites to summary.favorites,
    ).filter { it.second > 0 }
    val locale = LocalConfiguration.current.locales[0]
    val savedOn = remember(summary.exportedAtMillis, locale) {
        if (summary.exportedAtMillis > 0) DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(summary.exportedAtMillis)) else null
    }

    Surface(shape = RoundedCornerShape(28.dp), color = c.surface) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
            Text(stringResource(R.string.backup_import_title), color = c.onSurface, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (savedOn != null) stringResource(R.string.backup_import_saved_on, savedOn) else stringResource(R.string.backup_import_saved_unknown),
                color = c.onSurfaceMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.backup_import_contains), color = c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (rows.isEmpty()) {
                Text(stringResource(R.string.backup_import_nothing), color = c.onSurfaceMuted, fontSize = 14.sp)
            }
            rows.forEachIndexed { index, (label, count) ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(label), color = c.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(count.toString(), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.backup_import_replaces), color = c.onSurfaceMuted, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = c.accent) }
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_import_confirm), color = c.accent, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
