package com.havivon.pigeon.ui.crash

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.havivon.pigeon.R
import com.havivon.pigeon.data.crash.CrashHandler
import com.havivon.pigeon.domain.crash.CrashPrompt
import com.havivon.pigeon.ui.search.shareText
import com.havivon.pigeon.ui.theme.HebTheme

/** At launch, when the last run left a crash report: offers to share it (nothing leaves the phone otherwise). */
@Composable
fun CrashReportPrompt() {
    val context = LocalContext.current
    val prompt = remember { CrashPrompt(CrashHandler.store(context)) }
    val report by prompt.report.collectAsState()
    report?.let { text ->
        CrashReportDialog(
            onSend = {
                shareText(context, text)
                prompt.sent()
            },
            onNotNow = prompt::dismiss,
        )
    }
}

@Composable
internal fun CrashReportDialog(onSend: () -> Unit, onNotNow: () -> Unit) {
    val c = HebTheme.colors
    AlertDialog(
        onDismissRequest = onNotNow,
        containerColor = c.surface,
        title = { Text(stringResource(R.string.crash_title), color = c.onSurface, fontWeight = FontWeight.ExtraBold) },
        text = { Text(stringResource(R.string.crash_body), color = c.onSurfaceMuted) },
        confirmButton = { TextButton(onClick = onSend) { Text(stringResource(R.string.crash_send), color = c.accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onNotNow) { Text(stringResource(R.string.crash_not_now), color = c.accent) } },
    )
}
