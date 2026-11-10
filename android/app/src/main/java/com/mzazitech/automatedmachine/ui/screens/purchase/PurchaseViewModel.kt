package com.mzazitech.automatedmachine.ui.screens.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.core.Msisdn
import com.mzazitech.automatedmachine.data.repository.PurchaseResult
import com.mzazitech.automatedmachine.domain.model.Bundle
import com.mzazitech.automatedmachine.domain.model.Network
import com.mzazitech.automatedmachine.domain.model.PaymentStatus
import com.mzazitech.automatedmachine.domain.model.Transaction
import com.mzazitech.automatedmachine.domain.ussd.SimSlotInfo
import com.mzazitech.automatedmachine.work.JobScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PurchaseUiState(
    val network: Network = Network.SAFARICOM,
    val bundles: List<Bundle> = emptyList(),
    val selected: Bundle? = null,
    val phone: String = "",
    val phoneError: String? = null,
    val sims: List<SimSlotInfo> = emptyList(),
    val simSlot: Int = 0,
    val testMode: Boolean = true,
    val simulation: Boolean = true,
    val submitting: Boolean = false,
    val stage: String? = null,
    val error: String? = null,
    val created: Transaction? = null,
    val duplicate: Transaction? = null
) {
    val canSubmit: Boolean
        get() = !submitting && selected != null && phone.isNotBlank()
}

class PurchaseViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(PurchaseUiState())
    val state: StateFlow<PurchaseUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                testMode = container.settings.isTestMode(),
                simulation = container.settings.isSimulationMode(),
                simSlot = container.settings.simSlot()
            )
            loadSims()
        }
        observeBundles(_state.value.network)
    }

    fun preselect(bundleId: String) {
        if (bundleId.isBlank()) return
        viewModelScope.launch {
            val bundle = container.bundles.byId(bundleId) ?: return@launch
            _state.value = _state.value.copy(network = bundle.network, selected = bundle)
            observeBundles(bundle.network)
        }
    }

    fun selectNetwork(network: Network) {
        if (_state.value.network == network) return
        _state.value = _state.value.copy(network = network, selected = null, error = null)
        observeBundles(network)
    }

    fun selectBundle(bundle: Bundle) {
        _state.value = _state.value.copy(selected = bundle, error = null)
    }

    fun onPhoneChange(value: String) {
        _state.value = _state.value.copy(
            phone = value.take(15),
            phoneError = null,
            error = null
        )
    }

    fun selectSim(slot: Int) {
        _state.value = _state.value.copy(simSlot = slot)
        viewModelScope.launch { container.settings.setPreferredSimSlot(slot) }
    }

    fun loadSims() {
        viewModelScope.launch {
            val simulation = container.settings.isSimulationMode()
            val sims = runCatching {
                if (simulation) container.simulator.listSims() else container.telephony.listSims()
            }.getOrDefault(emptyList())
            _state.value = _state.value.copy(sims = sims)
        }
    }

    fun submit() {
        val current = _state.value
        val bundle = current.selected ?: return
        if (current.submitting) return

        val normalized = Msisdn.normalize(current.phone)
        if (normalized == null) {
            _state.value = current.copy(phoneError = "Enter a valid Kenyan number, e.g. 0712345678")
            return
        }

        _state.value = current.copy(
            submitting = true,
            stage = "Creating the purchase…",
            error = null,
            phoneError = null,
            duplicate = null
        )

        viewModelScope.launch {
            when (val result = container.transactions.createPurchase(bundle, normalized, current.simSlot)) {
                is PurchaseResult.Invalid -> _state.value = _state.value.copy(
                    submitting = false,
                    stage = null,
                    error = result.message
                )

                is PurchaseResult.Error -> _state.value = _state.value.copy(
                    submitting = false,
                    stage = null,
                    error = result.message
                )

                is PurchaseResult.Duplicate -> _state.value = _state.value.copy(
                    submitting = false,
                    stage = null,
                    duplicate = result.existing,
                    error = "This number already has an active purchase for ${result.existing.bundleName}."
                )

                is PurchaseResult.Created -> handleCreated(result.transaction, result.online)
            }
        }
    }

    private suspend fun handleCreated(transaction: Transaction, online: Boolean) {
        _state.value = _state.value.copy(created = transaction, stage = "Waiting for M-Pesa confirmation…")

        val settled = if (online && transaction.paymentStatus == PaymentStatus.PENDING) {
            container.transactions.awaitPayment(transaction.id)
        } else {
            transaction
        }

        val finalTxn = settled ?: transaction
        _state.value = _state.value.copy(
            submitting = false,
            stage = null,
            created = finalTxn,
            error = when (finalTxn.paymentStatus) {
                PaymentStatus.FAILED, PaymentStatus.CANCELLED ->
                    finalTxn.failureReason ?: "Payment was not completed."
                else -> null
            }
        )

        if (finalTxn.paymentStatus == PaymentStatus.PAID) {
            JobScheduler.enqueueUssdDrainNow(container.appContext)
        }
    }

    fun clearResult() {
        _state.value = _state.value.copy(created = null, error = null, duplicate = null, stage = null)
    }

    private fun observeBundles(network: Network) {
        viewModelScope.launch {
            container.bundles.observeByNetwork(network).collect { list ->
                val stillSelected = list.firstOrNull { it.id == _state.value.selected?.id }
                _state.value = _state.value.copy(
                    bundles = list,
                    selected = stillSelected ?: list.firstOrNull()
                )
            }
        }
    }
}
