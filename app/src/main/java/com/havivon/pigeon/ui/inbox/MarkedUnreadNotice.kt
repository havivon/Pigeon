package com.havivon.pigeon.ui.inbox

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.app.LocalNotices

/** What "Mark as unread" tells the user when it is done, by how many messages it marked: none when there is no received message. */
@Composable
internal fun rememberMarkedUnreadNotice(): (Int) -> Unit {
    val notices = LocalNotices.current
    val done = stringResource(R.string.marked_unread)
    val none = stringResource(R.string.marked_unread_none)
    return { count -> notices.show(if (count > 0) done else none) }
}
