package com.mzazitech.automatedmachine.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.core.Money
import com.mzazitech.automatedmachine.core.Time
import com.mzazitech.automatedmachine.data.local.DashboardDao
import com.mzazitech.automatedmachine.data.local.toDomain
import com.mzazitech.automatedmachine.domain.model.Transaction
import com.mzazitech.automatedmachine.domain.ussd.SimSlotInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val totalSales: Money = Money.ZERO,
    val todayRevenue: Money = Money.ZERO,
    val successful: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0,
    val total: Int = 0,
    val availableSims: Int = 0,
    val activeJobs: Int = 0,
    val recent: List<Transaction> = emptyList(),
    val sims: List<SimSlotInfo> = emptyList(),
    val testMode: Boolean = true,
    val simulation: Boolean = true
) {
    val successRate: Float
        get() = if (total == 0) 0f else successful.toFloat() / total.toFloat()
}

class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    private val simsFlow = MutableStateFlow<List<SimSlotInfo>>(emptyList())

    private data class Counters(
        val counts: DashboardDao.Counts,
        val lifetimeCents: Long,
        val todayCents: Long,
        val recent: List<Transaction>,
        val activeJobs: Int
    )

    private val counters = combine(
        container.database.dashboardDao().observeCounts(),
        container.database.transactionDao().observeLifetimeSales(),
        container.database.transactionDao().observeRevenueSince(Time.startOfToday()),
        container.database.transactionDao().observeRecent(6),
        container.database.ussdJobDao().observeActiveCount()
    ) { counts, lifetime, today, recent, activeJobs ->
        Counters(
            counts = counts,
            lifetimeCents = lifetime,
            todayCents = today,
            recent = recent.map { it.toDomain() },
            activeJobs = activeJobs
        )
    }

    val state: StateFlow<DashboardUiState> = combine(
        counters,
        container.settings.testMode,
        container.settings.simulationMode,
        simsFlow
    ) { snapshot, testMode, simulation, sims ->
        DashboardUiState(
            totalSales = Money(snapshot.lifetimeCents),
            todayRevenue = Money(snapshot.todayCents),
            successful = snapshot.counts.successful,
            failed = snapshot.counts.failed,
            pending = snapshot.counts.pending,
            total = snapshot.counts.total,
            availableSims = sims.size,
            activeJobs = snapshot.activeJobs,
            recent = snapshot.recent,
            sims = sims,
            testMode = testMode,
            simulation = simulation
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        refreshSims()
    }

    fun refreshSims() {
        viewModelScope.launch {
            val simulation = container.settings.isSimulationMode()
            val sims = runCatching {
                if (simulation) container.simulator.listSims() else container.telephony.listSims()
            }.getOrDefault(emptyList())
            simsFlow.value = sims
        }
    }
}
