package com.mzazitech.automatedmachine.data.repository

import com.mzazitech.automatedmachine.BuildConfig
import com.mzazitech.automatedmachine.data.local.SettingsDao
import com.mzazitech.automatedmachine.data.local.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Operator preferences. Stored in Room so they survive restarts but stay out of the
 * encrypted blob (they hold no secrets).
 */
class SettingsRepository(private val dao: SettingsDao) {

    val simulationMode: Flow<Boolean> = dao.observe(KEY_SIMULATION).map {
        it?.toBooleanStrictOrNull() ?: BuildConfig.DEFAULT_USSD_SIMULATION
    }

    val testMode: Flow<Boolean> = dao.observe(KEY_TEST_MODE).map {
        it?.toBooleanStrictOrNull() ?: BuildConfig.DEFAULT_USSD_SIMULATION
    }

    val preferredSimSlot: Flow<Int> = dao.observe(KEY_SIM_SLOT).map {
        it?.toIntOrNull() ?: 0
    }

    val operatorName: Flow<String> = dao.observe(KEY_OPERATOR).map {
        it?.takeIf(String::isNotBlank) ?: "MZAZI Operator"
    }

    val autoRetryEnabled: Flow<Boolean> = dao.observe(KEY_AUTO_RETRY).map {
        it?.toBooleanStrictOrNull() ?: false
    }

    suspend fun isSimulationMode(): Boolean =
        dao.get(KEY_SIMULATION)?.toBooleanStrictOrNull() ?: BuildConfig.DEFAULT_USSD_SIMULATION

    suspend fun isTestMode(): Boolean =
        dao.get(KEY_TEST_MODE)?.toBooleanStrictOrNull() ?: BuildConfig.DEFAULT_USSD_SIMULATION

    suspend fun simSlot(): Int = dao.get(KEY_SIM_SLOT)?.toIntOrNull() ?: 0

    suspend fun isAutoRetryEnabled(): Boolean =
        dao.get(KEY_AUTO_RETRY)?.toBooleanStrictOrNull() ?: false

    suspend fun setSimulationMode(enabled: Boolean) {
        dao.put(SettingEntity(KEY_SIMULATION, enabled.toString()))
    }

    suspend fun setTestMode(enabled: Boolean) {
        dao.put(SettingEntity(KEY_TEST_MODE, enabled.toString()))
    }

    suspend fun setPreferredSimSlot(slot: Int) {
        dao.put(SettingEntity(KEY_SIM_SLOT, slot.toString()))
    }

    suspend fun setOperatorName(name: String) {
        dao.put(SettingEntity(KEY_OPERATOR, name.trim().take(60)))
    }

    suspend fun setAutoRetry(enabled: Boolean) {
        dao.put(SettingEntity(KEY_AUTO_RETRY, enabled.toString()))
    }

    suspend fun getRaw(key: String): String? = dao.get(key)

    companion object {
        const val KEY_SIMULATION = "ussd_simulation_mode"
        const val KEY_TEST_MODE = "test_mode"
        const val KEY_SIM_SLOT = "preferred_sim_slot"
        const val KEY_OPERATOR = "operator_name"
        const val KEY_AUTO_RETRY = "auto_retry"
    }
}
