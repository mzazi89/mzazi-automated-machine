package com.mzazitech.automatedmachine.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.core.Money
import com.mzazitech.automatedmachine.core.Time
import com.mzazitech.automatedmachine.data.local.AuditLogEntity
import com.mzazitech.automatedmachine.domain.model.Bundle
import com.mzazitech.automatedmachine.domain.model.Network
import com.mzazitech.automatedmachine.domain.model.UssdStep
import com.mzazitech.automatedmachine.domain.model.UssdTemplate
import com.mzazitech.automatedmachine.domain.model.UssdWorkflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminUiState(
    val network: Network = Network.SAFARICOM,
    val bundles: List<Bundle> = emptyList(),
    val workflows: List<UssdWorkflow> = emptyList(),
    val audit: List<AuditLogEntity> = emptyList(),
    val message: String? = null,
    val busy: Boolean = false
)

class AdminViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.bundles.observeAll().collect { list ->
                _state.value = _state.value.copy(bundles = list)
            }
        }
        viewModelScope.launch {
            container.workflows.observeAll().collect { list ->
                _state.value = _state.value.copy(workflows = list)
            }
        }
        viewModelScope.launch {
            container.audit.observeRecent(60).collect { logs ->
                _state.value = _state.value.copy(audit = logs)
            }
        }
    }

    fun selectNetwork(network: Network) {
        _state.value = _state.value.copy(network = network)
    }

    fun createBundle(
        network: Network,
        name: String,
        sizeMbText: String,
        priceText: String,
        costText: String,
        validityText: String,
        workflowId: String?
    ) {
        val sizeMb = sizeMbText.trim().toIntOrNull()
        val price = Money.parse(priceText)
        val cost = Money.parse(costText) ?: Money.ZERO
        val validity = validityText.trim().toIntOrNull() ?: 30

        val problem = when {
            name.isBlank() -> "Give the bundle a name."
            sizeMb == null || sizeMb <= 0 -> "Enter the bundle size in MB."
            price == null || price.cents <= 0 -> "Enter a valid selling price."
            workflowId.isNullOrBlank() -> "Select the verified USSD workflow for this product."
            else -> null
        }
        if (problem != null) {
            _state.value = _state.value.copy(message = problem)
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val bundle = container.bundles.create(
                network = network,
                name = name,
                dataSizeMb = sizeMb!!,
                sellingPrice = price!!,
                costPrice = cost,
                validityDays = validity,
                workflowId = workflowId
            )
            val pushed = container.sync.pushBundle(bundle)
            container.audit.record(
                container.session.session.value?.email ?: "admin",
                "bundle.create",
                bundle.id,
                "${bundle.network.code} ${bundle.name} ${bundle.sellingPrice.format()}"
            )
            _state.value = _state.value.copy(
                busy = false,
                message = pushed.exceptionOrNull()?.let {
                    "Saved locally; backend sync failed: ${it.message}"
                } ?: "Bundle saved."
            )
        }
    }

    fun toggleBundle(bundle: Bundle) {
        viewModelScope.launch {
            container.bundles.setEnabled(bundle.id, !bundle.enabled)
            container.audit.record(
                container.session.session.value?.email ?: "admin",
                if (bundle.enabled) "bundle.disable" else "bundle.enable",
                bundle.id,
                bundle.name
            )
        }
    }

    fun deleteBundle(bundle: Bundle) {
        viewModelScope.launch {
            container.bundles.delete(bundle.id)
            container.audit.record(
                container.session.session.value?.email ?: "admin",
                "bundle.delete",
                bundle.id,
                bundle.name
            )
            _state.value = _state.value.copy(message = "${bundle.name} removed.")
        }
    }

    /**
     * Registers an operator workflow. Codes must come from documentation verified with
     * the carrier; the UI refuses anything that is not a well-formed USSD string.
     */
    fun saveWorkflow(
        network: Network,
        name: String,
        codesText: String,
        successPattern: String,
        failurePattern: String,
        verifiedBy: String
    ) {
        val codes = codesText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (codes.isEmpty()) {
            _state.value = _state.value.copy(message = "Add at least one USSD step.")
            return
        }
        val problems = codes.mapIndexedNotNull { index, code ->
            UssdTemplate.validate(code)?.let { "Step ${index + 1}: $it" }
        }
        if (problems.isNotEmpty()) {
            _state.value = _state.value.copy(message = problems.first())
            return
        }
        if (successPattern.isBlank()) {
            _state.value = _state.value.copy(
                message = "A success receipt pattern is required so deliveries are not guessed."
            )
            return
        }
        if (verifiedBy.isBlank()) {
            _state.value = _state.value.copy(
                message = "Record who verified these codes with the operator."
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val steps = codes.mapIndexed { index, code ->
                UssdStep(
                    index = index,
                    label = "Step ${index + 1}",
                    requestTemplate = code,
                    expectedPattern = null,
                    sendAsSessionInput = false,
                    timeoutMs = 45_000
                )
            }
            val workflow = container.workflows.create(
                network = network,
                name = name.ifBlank { "${network.shortName} verified workflow" },
                steps = steps,
                successPattern = successPattern,
                failurePattern = failurePattern.ifBlank { null },
                verifiedBy = verifiedBy
            )
            val pushed = container.sync.pushWorkflow(workflow)
            container.audit.record(
                container.session.session.value?.email ?: "admin",
                "workflow.create",
                workflow.id,
                "${network.code} ${steps.size} step(s) verified by $verifiedBy"
            )
            _state.value = _state.value.copy(
                busy = false,
                message = pushed.exceptionOrNull()?.let {
                    "Workflow saved locally; backend sync failed: ${it.message}"
                } ?: "Workflow saved and enabled."
            )
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun workflowsFor(network: Network): List<UssdWorkflow> =
        _state.value.workflows.filter { it.network == network }

    fun bundlesFor(network: Network): List<Bundle> =
        _state.value.bundles.filter { it.network == network }

    fun stamp(): Long = Time.nowMillis()
}
