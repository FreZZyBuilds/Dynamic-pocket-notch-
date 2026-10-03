package com.frezzybuilds.devnotch.ui.clipboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frezzybuilds.devnotch.data.clipboard.ClipboardItem
import com.frezzybuilds.devnotch.data.clipboard.ClipboardRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClipboardViewModel(private val repository: ClipboardRepository) : ViewModel() {

    val history: StateFlow<List<ClipboardItem>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun copy(entry: ClipboardItem) = repository.copyToClipboard(entry)

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }
}
