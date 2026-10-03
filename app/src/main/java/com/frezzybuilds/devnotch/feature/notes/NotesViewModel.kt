package com.frezzybuilds.devnotch.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(private val repository: NotesRepository) : ViewModel() {

    private val selectedId = MutableStateFlow<Long?>(null)

    val notes: StateFlow<List<QuickNote>> = repository.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ausgewählte Notiz; ohne Auswahl die oberste (angepinnt bzw. zuletzt bearbeitet). */
    val selected: StateFlow<QuickNote?> = combine(notes, selectedId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            repository.migrateLegacyScratchpad()
            // Leerer Start: direkt eine Notiz anlegen, damit man sofort tippen kann.
            if (repository.notesIsEmpty()) selectedId.value = repository.create()
            _ready.value = true
        }
    }

    fun select(id: Long) {
        selectedId.value = id
    }

    fun create() {
        viewModelScope.launch { selectedId.value = repository.create() }
    }

    fun save(id: Long, title: String, content: String) {
        viewModelScope.launch { repository.save(id, title, content) }
    }

    fun togglePin(id: Long) {
        viewModelScope.launch { repository.togglePin(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            selectedId.value = null
            if (repository.notesIsEmpty()) selectedId.value = repository.create()
        }
    }
}
