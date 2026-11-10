package com.havivon.pigeon.ui.inbox

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * "Mark all as read?": says how many messages of [category] it will mark, and that it cannot be undone. Asked
 * before anything is written; [onDismiss] leaves everything as it was.
 */
@Composable
fun MarkAllReadDialog(count: Int, category: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) { MarkAllReadDialogContent(count, category, onConfirm, onDismiss) }
}

/** The question itself, apart from its window, for the design snapshots. */
@Composable
fun MarkAllReadDialogContent(count: Int, category: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val c = HebTheme.colors
    Surface(shape = RoundedCornerShape(28.dp), color = c.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
            Text(stringResource(R.string.mark_all_read_title), color = c.onSurface, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(14.dp))
            Text(
                pluralStringResource(R.plurals.mark_all_read_body, count, count, category),
                color = c.onSurfaceMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel), color = c.accent) }
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.mark_all_read_confirm), color = c.accent, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
