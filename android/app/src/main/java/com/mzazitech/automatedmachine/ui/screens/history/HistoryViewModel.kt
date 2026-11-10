package com.mzazitech.automatedmachine.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.domain.model.Network
import com.mzazitech.automatedmachine.domain.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class HistoryFilter { ALL, SUCCESS, FAILED, PENDING }

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val networkFilter = MutableStateFlow<Network?>(null)
    private val query = MutableStateFlow("")

    data class UiState(
        val transactions: List<Transaction> = emptyList(),
        val filter: HistoryFilter = HistoryFilter.ALL,
        val network: Network? = null,
        val query: String = ""
    ) {
        val totalCount: Int get() = transactions.size
    }

    val state: StateFlow<UiState> = combine(
        container.transactions.observeAll(),
        filter,
        networkFilter,
        query
    ) { all, activeFilter, network, search ->
        val filtered = all.filter { txn ->
            val matchesFilter = when (activeFilter) {
                HistoryFilter.ALL -> true
                HistoryFilter.SUCCESS -> txn.purchaseStatus == com.mzazitech.automatedmachine.domain.model.PurchaseStatus.SUCCESS
                HistoryFilter.FAILED -> txn.purchaseStatus == com.mzazitech.automatedmachine.domain.model.PurchaseStatus.FAILED
                HistoryFilter.PENDING -> txn.purchaseStatus == com.mzazitech.automatedmachine.domain.model.PurchaseStatus.QUEUED ||
                    txn.purchaseStatus == com.mzazitech.automatedmachine.domain.model.PurchaseStatus.RUNNING ||
                    txn.purchaseStatus == com.mzazitech.automatedmachine.domain.model.PurchaseStatus.AWAITING_MANUAL_CONFIRMATION ||
                    txn.paymentStatus == com.mzazitech.automatedmachine.domain.model.PaymentStatus.PENDING
            }
            val matchesNetwork = network == null || txn.network == network
            val matchesQuery = search.isBlank() ||
                txn.customerMsisdn.contains(search.trim()) ||
                txn.reference.contains(search.trim(), ignoreCase = true) ||
                txn.bundleName.contains(search.trim(), ignoreCase = true)
            matchesFilter && matchesNetwork && matchesQuery
        }
        UiState(filtered, activeFilter, network, search)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    fun setNetwork(value: Network?) {
        networkFilter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }
}
