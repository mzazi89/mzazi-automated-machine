package com.mzazitech.automatedmachine.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.BuildConfig
import com.mzazitech.automatedmachine.data.local.AuditLogEntity
import com.mzazitech.automatedmachine.data.repository.SyncRepository
import com.mzazitech.automatedmachine.domain.ussd.SimSlotInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val testMode: Boolean = true,
    val simulation: Boolean = true,
    val autoRetry: Boolean = false,
    val simSlot: Int = 0,
    val operatorName: String = "MZAZI Operator",
    val adminEmail: String? = null,
    val deviceId: String? = null,
    val deviceConfigured: Boolean = false,
    val apiBaseUrl: String = BuildConfig.API_BASE_URL,
    val versionName: String = BuildConfig.VERSION_NAME,
    val sims: List<SimSlotInfo> = emptyList(),
    val audit: List<AuditLogEntity> = emptyList(),
    val syncing: Boolean = false,
    val message: String? = null
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            container.audit.observeRecent(40).collect { logs ->
                _state.value = _state.value.copy(audit = logs)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                testMode = container.settings.isTestMode(),
                simulation = container.settings.isSimulationMode(),
                autoRetry = container.settings.isAutoRetryEnabled(),
                simSlot = container.settings.simSlot(),
                adminEmail = container.session.session.value?.email,
                deviceId = container.session.deviceId.value,
                deviceConfigured = container.session.hasDeviceCredentials()
            )
            refreshSims()
        }
    }

    fun refreshSims() {
        viewModelScope.launch {
            val simulation = container.settings.isSimulationMode()
            val sims = runCatching {
                if (simulation) container.simulator.listSims() else container.telephony.listSims()
            }.getOrDefault(emptyList())
            _state.value = _state.value.copy(sims = sims)
        }
    }

    fun setTestMode(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setTestMode(enabled)
            container.audit.record(
                container.session.session.value?.email ?: "operator",
                "settings.test_mode",
                null,
                enabled.toString()
            )
            _state.value = _state.value.copy(
                testMode = enabled,
                message = if (enabled) {
                    "Test mode ON — payments are simulated and no real bundle is bought."
                } else {
                    "Test mode OFF — real payments require a signed-in backend session."
                }
            )
        }
    }

    fun setSimulation(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setSimulationMode(enabled)
            container.audit.record(
                container.session.session.value?.email ?: "operator",
                "settings.simulation",
                null,
                enabled.toString()
            )
            _state.value = _state.value.copy(
                simulation = enabled,
                message = if (enabled) "Simulation ON — USSD requests stay on the device."
                else "Simulation OFF — real USSD requires verified workflows and permissions."
            )
            refreshSims()
        }
    }

    fun setAutoRetry(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setAutoRetry(enabled)
            _state.value = _state.value.copy(autoRetry = enabled)
        }
    }

    fun setSimSlot(slot: Int) {
        viewModelScope.launch {
            container.settings.setPreferredSimSlot(slot)
            _state.value = _state.value.copy(simSlot = slot)
        }
    }

    fun setOperatorName(name: String) {
        viewModelScope.launch {
            container.settings.setOperatorName(name)
            _state.value = _state.value.copy(operatorName = name)
        }
    }

    fun saveDeviceCredentials(deviceId: String, deviceKey: String) {
        if (deviceId.isBlank() || deviceKey.isBlank()) {
            _state.value = _state.value.copy(message = "Both the device ID and device key are required.")
            return
        }
        container.session.saveDeviceCredentials(deviceId.trim(), deviceKey.trim())
        _state.value = _state.value.copy(
            deviceId = deviceId.trim(),
            deviceConfigured = true,
            message = "Device credentials stored in the Android Keystore."
        )
    }

    fun clearDeviceCredentials() {
        container.session.clearDeviceCredentials()
        _state.value = _state.value.copy(deviceId = null, deviceConfigured = false)
    }

    fun syncNow() {
        if (_state.value.syncing) return
        _state.value = _state.value.copy(syncing = true, message = null)
        viewModelScope.launch {
            val report: SyncRepository.SyncReport = container.sync.pullCatalogue()
            _state.value = _state.value.copy(
                syncing = false,
                message = if (report.succeeded) {
                    "Synced ${report.bundles} bundles and ${report.workflows} workflows."
                } else {
                    report.error
                }
            )
            load()
        }
    }

    fun logout() {
        viewModelScope.launch {
            container.auth.logout()
            _state.value = _state.value.copy(adminEmail = null, message = "Signed out.")
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
