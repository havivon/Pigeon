package com.havivon.pigeon.ui.blocked

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.app.LocalNotices
import com.havivon.pigeon.ui.common.ltr
import com.havivon.pigeon.ui.navigation.pigeonViewModel
import com.havivon.pigeon.ui.theme.HebTheme

/** The phone's blocked numbers on real data; unblocking asks first, since it changes the whole phone. */
@Composable
fun BlockedNumbersRoute(onBack: () -> Unit) {
    val vm = pigeonViewModel { app -> BlockedNumbersViewModel(app.systemBlockList) { app.contacts.displayName(it) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val added = stringResource(R.string.blocked_added)
    val unblocked = stringResource(R.string.phone_unblocked)
    var unblocking by remember { mutableStateOf<BlockedEntry?>(null) }

    BlockedNumbersScreen(
        state = state,
        onDraft = vm::setDraft,
        onAdd = { vm.add { ok -> if (ok) notices.show(added) } },
        onUnblock = { unblocking = it },
        onBack = onBack,
    )

    unblocking?.let { entry ->
        val c = HebTheme.colors
        AlertDialog(
            onDismissRequest = { unblocking = null },
            containerColor = c.surface,
            title = { Text(stringResource(R.string.blocked_unblock_title, entry.name ?: ltr(entry.number)), color = c.onSurface, fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.blocked_unblock_body), color = c.onSurfaceMuted) },
            confirmButton = {
                TextButton(onClick = {
                    unblocking = null
                    vm.unblock(entry)
                    notices.show(unblocked)
                }) { Text(stringResource(R.string.phone_unblock_confirm), color = c.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { unblocking = null }) { Text(stringResource(R.string.common_cancel), color = c.accent) } },
        )
    }
}
