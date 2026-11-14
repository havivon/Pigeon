package com.havivon.pigeon.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * The question before any messages or conversations are deleted: [title] says what ("Delete 3 conversations?"), the body
 * that they go to the recycle bin for 30 days. Nothing is deleted until "Delete" is tapped; [onDismiss] (Cancel, Back, a tap
 * outside) leaves everything as it was. The undo after the deletion stays as well.
 */
@Composable
fun ConfirmDeleteDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = HebTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, color = c.onSurface, fontWeight = FontWeight.ExtraBold) },
        text = { Text(stringResource(R.string.delete_confirm_body), color = c.onSurfaceMuted) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.common_delete), color = c.spam, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = c.accent) }
        },
    )
}
