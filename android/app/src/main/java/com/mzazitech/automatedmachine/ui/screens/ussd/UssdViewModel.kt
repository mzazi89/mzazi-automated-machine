package com.mzazitech.automatedmachine.ui.screens.ussd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.data.local.toDomain
import com.mzazitech.automatedmachine.domain.model.PurchaseStatus
import com.mzazitech.automatedmachine.domain.model.Transaction
import com.mzazitech.automatedmachine.domain.model.UssdJob
import com.mzazitech.automatedmachine.domain.model.UssdRunResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UssdUiState(
    val transaction: Transaction? = null,
    val job: UssdJob? = null,
    val running: Boolean = false,
    val message: String? = null,
    val progress: List<String> = emptyList(),
    val finished: Boolean = false,
    val simulation: Boolean = true
)

class UssdViewModel(
    private val container: AppContainer,
    private val transactionId: String
) : ViewModel() {

    private val _state = MutableStateFlow(UssdUiState())
    val state: StateFlow<UssdUiState> = _state.asStateFlow()

    init {
        observe()
        poll()
        viewModelScope.launch {
            _state.value = _state.value.copy(simulation = container.settings.isSimulationMode())
        }
    }

    private fun observe() {
        viewModelScope.launch {
            container.transactions.observeById(transactionId).collect { txn ->
                _state.value = _state.value.copy(
                    transaction = txn,
                    finished = txn?.purchaseStatus?.isTerminal == true
                )
            }
        }
        viewModelScope.launch {
            container.audit.observeRecent(200).collect { logs ->
                _state.value = _state.value.copy(
                    progress = logs
                        .filter { it.target == transactionId }
                        .sortedBy { it.createdAt }
                        .map { "${it.action} · ${it.detail.orEmpty()}" }
                        .takeLast(12)
                )
            }
        }
    }

    private fun poll() {
        viewModelScope.launch {
            while (isActive) {
                val job = container.database.ussdJobDao().byTransaction(transactionId)
                _state.value = _state.value.copy(job = job?.toDomain())
                delay(2_000)
            }
        }
    }

    /** Executes the delivery now instead of waiting for the background scheduler. */
    fun runNow() {
        val job = _state.value.job ?: run {
            _state.value = _state.value.copy(
                message = "No delivery job exists yet. Confirm the payment first."
            )
            return
        }
        if (_state.value.running) return
        _state.value = _state.value.copy(running = true, message = null)

        viewModelScope.launch {
            val result = runCatching { container.jobRunner.execute(job.id) }.getOrNull()
            val message = when (result) {
                is UssdRunResult.Success -> "Carrier accepted the request."
                is UssdRunResult.NeedsManualConfirmation -> result.reason
                is UssdRunResult.Failed -> result.reason
                null -> "The job did not start. Check the delivery queue."
            }
            _state.value = _state.value.copy(running = false, message = message)
        }
    }

    fun confirmManually(delivered: Boolean, note: String) {
        viewModelScope.launch {
            container.transactions.markManualOutcome(transactionId, delivered, note.ifBlank { null })
            container.audit.record(
                container.session.session.value?.email ?: "operator",
                if (delivered) "ussd.manual_success" else "ussd.manual_failure",
                transactionId,
                note.ifBlank { "Operator confirmed the carrier outcome." }
            )
            _state.value = _state.value.copy(
                message = if (delivered) "Delivery recorded as successful." else "Delivery recorded as failed.",
                finished = true
            )
        }
    }

    fun cancelDelivery() {
        viewModelScope.launch {
            container.transactions.markManualOutcome(transactionId, false, "Cancelled by operator")
            _state.value = _state.value.copy(
                transaction = _state.value.transaction?.copy(
                    purchaseStatus = PurchaseStatus.CANCELLED
                ),
                finished = true
            )
        }
    }
}
