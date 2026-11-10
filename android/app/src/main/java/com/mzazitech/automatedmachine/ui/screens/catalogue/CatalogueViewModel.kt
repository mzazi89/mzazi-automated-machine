package com.mzazitech.automatedmachine.ui.screens.catalogue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.domain.model.Bundle
import com.mzazitech.automatedmachine.domain.model.Network
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CatalogueViewModel(
    private val container: AppContainer,
    initialNetwork: Network?
) : ViewModel() {

    private val selected = MutableStateFlow(initialNetwork)

    data class UiState(
        val network: Network? = null,
        val bundles: List<Bundle> = emptyList(),
        val countsByNetwork: Map<Network, Int> = emptyMap()
    )

    val state: StateFlow<UiState> = combine(
        container.bundles.observeAll(),
        selected
    ) { all, network ->
        val visible = all
            .filter { it.enabled }
            .filter { network == null || it.network == network }
            .sortedWith(compareBy({ it.network.ordinal }, { it.sortOrder }))
        UiState(
            network = network,
            bundles = visible,
            countsByNetwork = Network.entries.associateWith { n ->
                all.count { it.network == n && it.enabled }
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(network = initialNetwork))

    fun selectNetwork(network: Network?) {
        selected.value = network
    }
}
