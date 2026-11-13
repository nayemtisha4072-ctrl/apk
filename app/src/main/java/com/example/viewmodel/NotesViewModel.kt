package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.NoteEntity
import com.example.data.repository.MultiCalculatorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NoteSaveStatus(val label: String) {
    SAVED("Saved"),
    SAVING("Saving..."),
    UNSAVED("Unsaved changes")
}

data class NoteEditorState(
    val id: String? = null,
    val title: String = "",
    val content: String = "",
    val isPinned: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val saveStatus: NoteSaveStatus = NoteSaveStatus.SAVED,
    val isLoaded: Boolean = false
)

sealed interface NotesEvent {
    data class ShowMessage(val message: String) : NotesEvent
    data object NoteDeletedAndClosed : NotesEvent
}

class NotesViewModel(
    private val repository: MultiCalculatorRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _editorState = MutableStateFlow(NoteEditorState())
    val editorState: StateFlow<NoteEditorState> = _editorState.asStateFlow()

    private val _events = MutableSharedFlow<NotesEvent>()
    val events: SharedFlow<NotesEvent> = _events.asSharedFlow()

    private var autoSaveJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<NoteEntity>> = _searchQuery
        .flatMapLatest { query -> repository.observeNotes(query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun togglePinned(note: NoteEntity) {
        viewModelScope.launch {
            repository.toggleNotePinned(note.id, note.isPinned)
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            repository.deleteNote(id)
            _events.emit(NotesEvent.ShowMessage("Note deleted"))
        }
    }

    fun openNote(noteId: String?) {
        autoSaveJob?.cancel()
        if (noteId.isNullOrBlank()) {
            val now = System.currentTimeMillis()
            _editorState.value = NoteEditorState(
                id = null,
                title = "",
                content = "",
                isPinned = false,
                createdAtMillis = now,
                updatedAtMillis = now,
                saveStatus = NoteSaveStatus.SAVED,
                isLoaded = true
            )
        } else {
            _editorState.value = NoteEditorState(isLoaded = false)
            viewModelScope.launch {
                val loaded = repository.getNoteById(noteId)
                if (loaded != null) {
                    _editorState.value = NoteEditorState(
                        id = loaded.id,
                        title = loaded.title,
                        content = loaded.content,
                        isPinned = loaded.isPinned,
                        createdAtMillis = loaded.createdAtMillis,
                        updatedAtMillis = loaded.updatedAtMillis,
                        saveStatus = NoteSaveStatus.SAVED,
                        isLoaded = true
                    )
                } else {
                    val now = System.currentTimeMillis()
                    _editorState.value = NoteEditorState(
                        createdAtMillis = now,
                        updatedAtMillis = now,
                        isLoaded = true
                    )
                }
            }
        }
    }

    fun updateTitle(newTitle: String) {
        _editorState.update {
            it.copy(title = newTitle, saveStatus = NoteSaveStatus.UNSAVED)
        }
        scheduleAutoSave()
    }

    fun updateContent(newContent: String) {
        _editorState.update {
            it.copy(content = newContent, saveStatus = NoteSaveStatus.UNSAVED)
        }
        scheduleAutoSave()
    }

    fun toggleEditorPinned() {
        _editorState.update {
            it.copy(isPinned = !it.isPinned, saveStatus = NoteSaveStatus.UNSAVED)
        }
        saveNoteNow(showConfirmation = false)
    }

    fun appendCalculationToEditor(expression: String, result: String) {
        _editorState.update { current ->
            val snippet = "$expression = $result"
            val nextContent = if (current.content.isBlank()) {
                snippet
            } else {
                "${current.content.trimEnd()}\n$snippet"
            }
            current.copy(
                content = nextContent,
                saveStatus = NoteSaveStatus.UNSAVED
            )
        }
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(450L)
            persistEditorInternal(explicitSave = false)
        }
    }

    fun saveNoteNow(showConfirmation: Boolean = true) {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            persistEditorInternal(explicitSave = showConfirmation)
        }
    }

    fun flushBeforeExit() {
        autoSaveJob?.cancel()
        val state = _editorState.value
        if (!state.isLoaded) return
        if (state.title.isBlank() && state.content.isBlank()) return
        if (state.saveStatus == NoteSaveStatus.SAVED) return

        viewModelScope.launch {
            withContext(NonCancellable) {
                persistEditorInternal(explicitSave = false)
            }
        }
    }

    fun deleteActiveNote() {
        autoSaveJob?.cancel()
        val id = _editorState.value.id
        viewModelScope.launch {
            if (!id.isNullOrBlank()) {
                repository.deleteNote(id)
            }
            _events.emit(NotesEvent.NoteDeletedAndClosed)
        }
    }

    private suspend fun persistEditorInternal(explicitSave: Boolean) {
        val state = _editorState.value
        if (!state.isLoaded) return
        if (state.title.isBlank() && state.content.isBlank()) {
            if (explicitSave) {
                _events.emit(NotesEvent.ShowMessage("Add a title or note content before saving"))
            }
            return
        }

        _editorState.update { it.copy(saveStatus = NoteSaveStatus.SAVING) }
        val effectiveTitle = state.title.trim().ifBlank { "Untitled Note" }
        val saved = repository.upsertNote(
            id = state.id,
            title = effectiveTitle,
            content = state.content,
            isPinned = state.isPinned
        )
        _editorState.update { current ->
            // Keep whatever the user may have typed while saving, only update ID/timestamps if still aligned
            current.copy(
                id = saved.id,
                createdAtMillis = saved.createdAtMillis,
                updatedAtMillis = saved.updatedAtMillis,
                saveStatus = if (current.content == state.content &&
                    (current.title == state.title || current.title == effectiveTitle)
                ) {
                    NoteSaveStatus.SAVED
                } else {
                    NoteSaveStatus.UNSAVED
                }
            )
        }
        if (explicitSave) {
            _events.emit(NotesEvent.ShowMessage("Note saved"))
        }
    }
}
