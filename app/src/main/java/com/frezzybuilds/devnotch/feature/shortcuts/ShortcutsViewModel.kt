package com.frezzybuilds.devnotch.feature.shortcuts

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShortcutsViewModel(
    private val app: Application,
    private val repository: ShortcutsRepository
) : ViewModel() {

    val shortcuts: StateFlow<List<ProjectShortcut>> = repository.shortcuts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _apps = MutableStateFlow<List<LaunchableApp>?>(null)

    /** Installierte Apps für die Auswahl; null = wird noch geladen. */
    val apps: StateFlow<List<LaunchableApp>?> = _apps.asStateFlow()

    init {
        viewModelScope.launch { repository.seedDefaultsOnce() }
    }

    fun loadApps() {
        if (_apps.value != null) return
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { ShortcutLauncher.launchableApps(app) }
        }
    }

    fun addApp(app: LaunchableApp) {
        viewModelScope.launch { repository.addApp(app.label, app.packageName) }
    }

    fun addUrl(title: String, url: String) {
        viewModelScope.launch { repository.addUrl(title, url) }
    }

    fun delete(shortcut: ProjectShortcut) {
        viewModelScope.launch { repository.delete(shortcut) }
    }

    fun move(shortcut: ProjectShortcut, delta: Int) {
        viewModelScope.launch { repository.move(shortcut, delta) }
    }
}
