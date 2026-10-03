package com.frezzybuilds.devnotch.feature.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaywallState(
    val loading: Boolean = true,
    val packages: List<PaywallPackage> = emptyList(),
    val selectedId: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val unlocked: Boolean = false
) {
    val selected: PaywallPackage? get() = packages.firstOrNull { it.id == selectedId }
}

class PaywallViewModel(
    private val billing: BillingClient,
    private val proAccess: ProAccess
) : ViewModel() {

    private val _state = MutableStateFlow(PaywallState(unlocked = proAccess.isPro.value))
    val state: StateFlow<PaywallState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val packages = try {
                billing.loadPackages()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _state.update {
                it.copy(
                    loading = false,
                    packages = packages,
                    selectedId = packages.firstOrNull()?.id,
                    message = if (packages.isEmpty()) "Angebote sind gerade nicht verfügbar." else null
                )
            }
        }
        viewModelScope.launch { proAccess.isPro.collect { pro -> _state.update { it.copy(unlocked = pro) } } }
    }

    fun select(id: String) = _state.update { it.copy(selectedId = id) }

    fun purchase(activity: Activity) {
        val pkg = _state.value.selected ?: return
        run { billing.purchase(activity, pkg) }
    }

    fun restore() = run { billing.restore() }

    private fun run(block: suspend () -> PurchaseOutcome) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val outcome = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PurchaseOutcome.Failed(e.message ?: "Fehler")
            }
            _state.update {
                it.copy(
                    busy = false,
                    message = when (outcome) {
                        PurchaseOutcome.Success -> null
                        PurchaseOutcome.Cancelled -> null
                        is PurchaseOutcome.Failed -> outcome.message
                    }
                )
            }
        }
    }
}
