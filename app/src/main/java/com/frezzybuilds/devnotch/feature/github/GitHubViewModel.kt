package com.frezzybuilds.devnotch.feature.github

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    data class Success(val profile: GitHubProfile) : GitHubUiState
}

class GitHubViewModel(
    private val service: GitHubService,
    private val settings: GitHubSettings
) : ViewModel() {

    private val _state = MutableStateFlow<GitHubUiState>(GitHubUiState.Loading)
    val state: StateFlow<GitHubUiState> = _state.asStateFlow()

    private var lastSuccessAt = 0L
    private var loadedUsername: String? = null
    private var loading = false

    /**
     * Beim Öffnen des Tabs: neu laden, wenn nichts da ist, die Daten älter als 10 min sind
     * oder in den Einstellungen inzwischen ein anderer Benutzer eingetragen wurde.
     */
    fun refreshIfStale() {
        val fresh = _state.value is GitHubUiState.Success &&
            loadedUsername == settings.username &&
            SystemClock.elapsedRealtime() - lastSuccessAt < STALE_AFTER_MILLIS
        if (!fresh) refresh()
    }

    fun refresh() {
        if (loading) return
        loading = true
        val username = settings.username
        // Beim Wechsel des Benutzers nicht kurz das alte Profil zeigen.
        if (_state.value !is GitHubUiState.Success || username != loadedUsername) {
            _state.value = GitHubUiState.Loading
        }
        viewModelScope.launch {
            _state.value = try {
                GitHubUiState.Success(service.fetchProfile(username)).also {
                    lastSuccessAt = SystemClock.elapsedRealtime()
                    loadedUsername = username
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
