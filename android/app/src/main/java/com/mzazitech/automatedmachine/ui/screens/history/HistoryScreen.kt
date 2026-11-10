package com.mzazitech.automatedmachine.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.core.Msisdn
import com.mzazitech.automatedmachine.core.Time
import com.mzazitech.automatedmachine.domain.model.Network
import com.mzazitech.automatedmachine.domain.model.Transaction
import com.mzazitech.automatedmachine.ui.SimpleViewModelFactory
import com.mzazitech.automatedmachine.ui.components.EmptyState
import com.mzazitech.automatedmachine.ui.components.MzaziCard
import com.mzazitech.automatedmachine.ui.components.PaymentStatusChip
import com.mzazitech.automatedmachine.ui.components.PurchaseStatusChip
import com.mzazitech.automatedmachine.ui.theme.ElectricBlue
import com.mzazitech.automatedmachine.ui.theme.MzaziBorder
import com.mzazitech.automatedmachine.ui.theme.MzaziSurfaceElevated
import com.mzazitech.automatedmachine.ui.theme.TextMuted
import com.mzazitech.automatedmachine.ui.theme.TextPrimary
import com.mzazitech.automatedmachine.ui.theme.TextSecondary
import com.mzazitech.automatedmachine.ui.theme.brandColor

@Composable
fun HistoryScreen(
    container: AppContainer,
    onOpenTransaction: (String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = remember { SimpleViewModelFactory { HistoryViewModel(container) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Transaction history",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        "${state.totalCount} record${if (state.totalCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search number, reference or bundle") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MzaziSurfaceElevated,
                    unfocusedContainerColor = MzaziSurfaceElevated,
                    focusedBorderColor = ElectricBlue,
                    unfocusedBorderColor = MzaziBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = ElectricBlue
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HistoryFilter.entries.toList()) { filter ->
                    FilterPill(
                        label = filter.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) }
                    )
                }
                items(Network.entries.toList()) { network ->
                    FilterPill(
                        label = network.shortName,
                        selected = state.network == network,
                        onClick = {
                            viewModel.setNetwork(if (state.network == network) null else network)
                        }
                    )
                }
            }
        }

        if (state.transactions.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing here yet",
                    subtitle = "Completed and pending sales will appear in this list."
                )
            }
        } else {
            items(state.transactions, key = { it.id }) { txn ->
                HistoryRow(txn = txn, onClick = { onOpenTransaction(txn.id) })
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ElectricBlue.copy(alpha = 0.16f) else MzaziSurfaceElevated)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) ElectricBlue else TextSecondary
        )
    }
}

@Composable
private fun HistoryRow(txn: Transaction, onClick: () -> Unit) {
    MzaziCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(txn.network.brandColor().copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    txn.network.shortName.take(1),
                    style = MaterialTheme.typography.titleSmall,
                    color = txn.network.brandColor()
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(txn.bundleName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${Msisdn.mask(txn.customerMsisdn)} · ${Time.formatDateTime(txn.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PurchaseStatusChip(txn.purchaseStatus)
                    PaymentStatusChip(txn.paymentStatus)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(txn.amount.format(), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(
                    txn.reference,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}
