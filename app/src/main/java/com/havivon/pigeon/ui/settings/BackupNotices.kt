package com.havivon.pigeon.ui.settings

import android.content.Context
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.backup.BackupError
import com.havivon.pigeon.domain.backup.RestoreReport

/** The notice that follows an export or an import: what happened, and what from the file this phone could not use. */
internal fun backupNotice(context: Context, event: BackupEvent): String = when (event) {
    BackupEvent.Exported -> context.getString(R.string.backup_exported)
    BackupEvent.ExportFailed -> context.getString(R.string.backup_export_failed)
    BackupEvent.ImportFailed -> context.getString(R.string.backup_import_failed)
    is BackupEvent.Refused -> context.getString(
        when (event.error) {
            BackupError.NotJson, BackupError.WrongFormat -> R.string.backup_refused_not_settings
            BackupError.TooNew -> R.string.backup_refused_too_new
            BackupError.TooLarge -> R.string.backup_refused_too_large
            BackupError.Unreadable -> R.string.backup_refused_unreadable
        },
    )
    is BackupEvent.Imported -> importedNotice(context, event.report)
}

private fun importedNotice(context: Context, r: RestoreReport): String {
    val parts = mutableListOf(context.getString(R.string.backup_imported))
    if (r.messagesUnavailable) {
        parts += context.getString(R.string.backup_note_no_messages)
    } else {
        if (r.favoritesFound < r.favoritesTotal) parts += context.getString(R.string.backup_note_favorites, r.favoritesFound, r.favoritesTotal)
        if (r.remindersRestored < r.remindersTotal) parts += context.getString(R.string.backup_note_reminders, r.remindersRestored, r.remindersTotal)
        val chats = r.pinsTotal + r.locksTotal
        val chatsFound = r.pinsFound + r.locksFound
        if (chatsFound < chats) parts += context.getString(R.string.backup_note_chats, chatsFound, chats)
    }
    if (r.appLockKeptOff) parts += context.getString(R.string.backup_note_app_lock)
    return parts.joinToString(". ")
}
