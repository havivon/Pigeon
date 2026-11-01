package com.havivon.pigeon.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.data.backup.BackupJson
import com.havivon.pigeon.domain.backup.BackupError
import com.havivon.pigeon.domain.backup.BackupParse
import com.havivon.pigeon.domain.backup.RestoreReport
import com.havivon.pigeon.domain.backup.SettingsBackup
import com.havivon.pigeon.domain.backup.SettingsBackupManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** What the Backup section tells the user, as a notice. */
sealed interface BackupEvent {
    data object Exported : BackupEvent
    data object ExportFailed : BackupEvent

    /** The picked file cannot be imported. */
    data class Refused(val error: BackupError) : BackupEvent

    data class Imported(val report: RestoreReport) : BackupEvent
    data object ImportFailed : BackupEvent
}

/**
 * Export and import of the settings file. The file itself is read and written by the screen (the system's
 * file picker hands it a Uri), which passes the reading and writing in as functions. An import is two
 * steps: [load] reads and checks the file and keeps it in [pending] for the confirmation dialog;
 * [confirmImport] applies it. Nothing here is logged: a file may name senders and keywords.
 */
class BackupViewModel(private val backup: SettingsBackupManager) : ViewModel() {

    private val _pending = MutableStateFlow<SettingsBackup?>(null)

    /** The file waiting for the user's confirmation, or null. */
    val pending: StateFlow<SettingsBackup?> = _pending.asStateFlow()

    private val events = Channel<BackupEvent>(Channel.BUFFERED)
    val eventFlow: Flow<BackupEvent> = events.receiveAsFlow()

    /** Writes the settings with [write] (true when the file was saved). */
    fun export(appVersion: String, write: suspend (String) -> Boolean) {
        viewModelScope.launch {
            val saved = try {
                write(BackupJson.encode(backup.export(appVersion)))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            events.trySend(if (saved) BackupEvent.Exported else BackupEvent.ExportFailed)
        }
    }

    /** Reads the picked file with [read] (null when it cannot be opened) and, if it is a Pigeon settings file, waits for the confirmation. */
    fun load(read: suspend () -> ByteArray?) {
        viewModelScope.launch {
            val bytes = try {
                read()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            when (val result = bytes?.let { BackupJson.parse(it) } ?: BackupParse.Failed(BackupError.Unreadable)) {
                is BackupParse.Ok -> _pending.value = result.backup
                is BackupParse.Failed -> events.trySend(BackupEvent.Refused(result.error))
            }
        }
    }

    fun cancelImport() {
        _pending.value = null
    }

    /** Applies the file waiting in [pending]; [canLock]: the phone has a screen lock or biometrics (app lock needs one). */
    fun confirmImport(canLock: Boolean) {
        val file = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            val event = try {
                BackupEvent.Imported(backup.restore(file, canLock))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BackupEvent.ImportFailed
            }
            events.trySend(event)
        }
    }
}
