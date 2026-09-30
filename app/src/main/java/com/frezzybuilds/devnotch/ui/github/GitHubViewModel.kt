package com.frezzybuilds.devnotch.ui.github

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frezzybuilds.devnotch.data.github.GitHubDashboard
import com.frezzybuilds.devnotch.data.github.GitHubRepository
import com.frezzybuilds.devnotch.data.github.MissingTokenException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface GitHubUiState {
    data object Loading : GitHubUiState
    data object NoToken : GitHubUiState
    data class Error(val message: String) : GitHubUiState
    data class Success(val dashboard: GitHubDashboard) : GitHubUiState
}

class GitHubViewModel(private val repository: GitHubRepository) : ViewModel() {

    private val _state = MutableStateFlow<GitHubUiState>(GitHubUiState.Loading)
    val state: StateFlow<GitHubUiState> = _state.asStateFlow()

    private var lastSuccessAt = 0L
    private var loading = false

    /** Beim Öffnen des Tabs: nur neu laden, wenn nichts da ist oder die Daten älter als 10 min sind. */
    fun refreshIfStale() {
        val fresh = _state.value is GitHubUiState.Success &&
            SystemClock.elapsedRealtime() - lastSuccessAt < STALE_AFTER_MILLIS
        if (!fresh) refresh()
    }

    fun refresh() {
        if (loading) return
        loading = true
        // Vorhandene Daten beim Aktualisieren stehen lassen statt zu flackern.
        if (_state.value !is GitHubUiState.Success) _state.value = GitHubUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                GitHubUiState.Success(repository.fetchDashboard()).also {
                    lastSuccessAt = SystemClock.elapsedRealtime()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingTokenException) {
                GitHubUiState.NoToken
            } catch (e: IOException) {
                GitHubUiState.Error("Keine Verbindung zu GitHub")
            } catch (e: Exception) {
                GitHubUiState.Error(e.message ?: "Unbekannter Fehler")
            } finally {
                loading = false
            }
        }
    }

    private companion object {
        const val STALE_AFTER_MILLIS = 10 * 60_000L
    }
}
