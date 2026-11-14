package ca.safehaven.intake.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.safehaven.intake.AppContainer
import ca.safehaven.intake.data.db.SubmissionDao
import ca.safehaven.intake.data.db.SubmissionEntity
import ca.safehaven.intake.data.db.escapeLike
import ca.safehaven.intake.data.files.IntakeFileStore
import java.io.File
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Typing pauses this long before the search runs. */
const val SEARCH_DEBOUNCE_MILLIS = 300L

/**
 * The phone part of a staff search: the query with everything but ASCII digits removed. Empty
 * when the query contains a letter: "Smith 3" or "john2@x.com" is not a phone search, and a
 * lone digit would match almost every phone number.
 */
fun searchDigits(query: String): String =
    if (query.any { it.isLetter() }) "" else query.filter { it in '0'..'9' }

sealed interface RecordsMessage {
    data class Deleted(val recordCode: String) : RecordsMessage
    data object DeleteFailed : RecordsMessage
}

/**
 * Staff > Records and the PDF viewer. Lives only while the staff area is open (its
 * ViewModelStore is cleared on lock), so no visitor data outlives the staff session.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class RecordsViewModel(
    private val dao: SubmissionDao,
    private val fileStore: IntakeFileStore,
    private val clock: Clock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    /** The search box text, updated on every keystroke. */
    val query: StateFlow<String> = _query.asStateFlow()

    /** Search results, newest first; null until the first result arrives. */
    val records: StateFlow<List<SubmissionEntity>?> = _query
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MILLIS }
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q -> dao.search(escapeLike(q), searchDigits(q)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** All saved waivers, for Maintenance > About. */
    val totalCount: StateFlow<Int?> = dao.observeCount()
        .map<Int, Int?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val selectedId = MutableStateFlow<String?>(null)

    /** The record open in the viewer. Follows the database, so it disappears once deleted. */
    val selected: StateFlow<SubmissionEntity?> = combine(records, selectedId) { list, id ->
        if (id == null) null else list?.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _confirmingDelete = MutableStateFlow(false)
    val confirmingDelete: StateFlow<Boolean> = _confirmingDelete.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _message = MutableStateFlow<RecordsMessage?>(null)
    val message: StateFlow<RecordsMessage?> = _message.asStateFlow()

    fun setQuery(text: String) {
        _query.value = text
        _message.value = null
    }

    fun open(id: String) {
        selectedId.value = id
        _message.value = null
    }

    fun closeViewer() {
        selectedId.value = null
        _confirmingDelete.value = false
        _message.value = null
    }

    /** The PDF on disk, or null if it's missing. */
    fun pdfFile(record: SubmissionEntity): File? {
        val file = try {
            fileStore.resolve(record.pdfPath)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return file.takeIf { it.isFile }
    }

    /** The share sheet opened for [id]. */
    fun onShared(id: String) {
        viewModelScope.launch { dao.markExported(id, clock.instant()) }
    }

    fun requestDelete() {
        if (selected.value != null && !_isDeleting.value) _confirmingDelete.value = true
    }

    fun cancelDelete() {
        _confirmingDelete.value = false
    }

    /** Deletes the open record's PDF, then its row. The record number is never reused. */
    fun confirmDelete() {
        val record = selected.value ?: return
        if (_isDeleting.value) return
        _confirmingDelete.value = false
        _isDeleting.value = true
        viewModelScope.launch {
            val deleted = try {
                val fileGone = withContext(io) {
                    val file = fileStore.resolve(record.pdfPath)
                    fileStore.delete(record.pdfPath) || !file.exists()
                }
                // A missing file still lets the row go; a file that won't delete keeps the row.
                if (fileGone) dao.deleteById(record.id)
                fileGone
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _isDeleting.value = false
            if (deleted) {
                selectedId.value = null
                _message.value = RecordsMessage.Deleted(record.recordCode)
            } else {
                _message.value = RecordsMessage.DeleteFailed
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RecordsViewModel(container.database.submissionDao(), container.fileStore, container.clock)
            }
        }
    }
}
