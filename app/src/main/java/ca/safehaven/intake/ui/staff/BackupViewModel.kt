package ca.safehaven.intake.ui.staff

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.safehaven.intake.AppContainer
import ca.safehaven.intake.staff.BackupResult
import ca.safehaven.intake.staff.BackupService
import ca.safehaven.intake.staff.BackupStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BackupMessage {
    data class Finished(val result: BackupResult) : BackupMessage
    /** [BackupService.setFolder] failed. */
    data object FolderFailed : BackupMessage
    /** No app on the tablet handles the folder picker intent. */
    data object NoPicker : BackupMessage
}

/** Staff > Backup. */
class BackupViewModel(private val service: BackupService) : ViewModel() {

    /** Null until the service reports its first status. */
    val status: StateFlow<BackupStatus?> = service.observeStatus()
        .catch { /* Leave the status unknown; Back up now stays disabled. */ }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isWorking = MutableStateFlow(false)
    /** True from the tap on "Back up now" until the result is in (covers the gap before isRunning). */
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _message = MutableStateFlow<BackupMessage?>(null)
    val message: StateFlow<BackupMessage?> = _message.asStateFlow()

    fun folderPickerIntent(): Intent = service.folderPickerIntent()

    fun onPickerUnavailable() {
        _message.value = BackupMessage.NoPicker
    }

    fun onFolderPicked(treeUri: Uri) {
        _message.value = null
        viewModelScope.launch {
            try {
                service.setFolder(treeUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = BackupMessage.FolderFailed
            }
        }
    }

    fun backUpNow() {
        if (_isWorking.value || status.value?.isRunning == true) return
        _isWorking.value = true
        _message.value = null
        viewModelScope.launch {
            val result = try {
                service.backUpNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupResult.Failed(copied = 0, cause = e)
            }
            _message.value = BackupMessage.Finished(result)
            _isWorking.value = false
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BackupViewModel(container.backupService) }
        }
    }
}
