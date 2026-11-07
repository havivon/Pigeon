package com.havivon.pigeon.ui.blocked

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.blocking.PhoneAwareSpamRepository
import com.havivon.pigeon.ui.app.Notices
import com.havivon.pigeon.ui.common.ltr
import com.havivon.pigeon.ui.theme.HebTheme
import kotlinx.coroutines.launch

/**
 * "Not spam" on a number that is blocked on the phone: asks whether to unblock it there too, which changes the
 * whole phone (it can call and text again, in every app). Shown by the shell, for every way to say "Not spam".
 */
@Composable
fun PhoneUnblockOffer(spam: PhoneAwareSpamRepository, notices: Notices) {
    val offers by spam.unblockOffers.collectAsState()
    if (offers.isEmpty()) return
    val scope = rememberCoroutineScope()
    val unblocked = stringResource(R.string.phone_unblocked)
    Dialog(onDismissRequest = spam::keepBlocked) {
        PhoneUnblockContent(
            offers,
            onUnblock = {
                scope.launch { spam.confirmUnblock() }
                notices.show(unblocked)
            },
            onKeep = spam::keepBlocked,
        )
    }
}

/** The question itself, apart from its window, for the design snapshots. */
@Composable
fun PhoneUnblockContent(numbers: List<String>, onUnblock: () -> Unit, onKeep: () -> Unit) {
    val c = HebTheme.colors
    Surface(shape = RoundedCornerShape(28.dp), color = c.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
            Text(stringResource(R.string.phone_unblock_title), color = c.onSurface, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(14.dp))
            Text(
                if (numbers.size == 1) stringResource(R.string.phone_unblock_body_one, ltr(numbers.single()))
                else pluralStringResource(R.plurals.phone_unblock_body_many, numbers.size, numbers.size),
                color = c.onSurfaceMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onKeep) { Text(stringResource(R.string.phone_unblock_keep), color = c.accent) }
                TextButton(onClick = onUnblock) { Text(stringResource(R.string.phone_unblock_confirm), color = c.accent, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
