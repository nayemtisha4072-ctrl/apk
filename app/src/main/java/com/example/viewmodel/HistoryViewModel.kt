package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.CalculationHistoryEntity
import com.example.data.repository.MultiCalculatorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HistoryEvent {
    data class ShowMessage(val message: String) : HistoryEvent
}

class HistoryViewModel(
    private val repository: MultiCalculatorRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _events = MutableSharedFlow<HistoryEvent>()
    val events: SharedFlow<HistoryEvent> = _events.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyItems: StateFlow<List<CalculationHistoryEntity>> = _searchQuery
        .flatMapLatest { query -> repository.observeHistory(query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            repository.deleteHistoryEntry(id)
            _events.emit(HistoryEvent.ShowMessage("History entry deleted"))
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _events.emit(HistoryEvent.ShowMessage("All calculation history cleared"))
        }
    }

    fun saveEntryToNote(entry: CalculationHistoryEntity) {
        viewModelScope.launch {
            repository.saveCalculationToNewNote(entry.expression, entry.result)
            _events.emit(HistoryEvent.ShowMessage("Saved calculation to Notes"))
        }
    }
}
