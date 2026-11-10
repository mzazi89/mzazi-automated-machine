package com.mzazitech.automatedmachine.ui.screens.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.core.Msisdn
import com.mzazitech.automatedmachine.core.Time
import com.mzazitech.automatedmachine.domain.model.Transaction
import com.mzazitech.automatedmachine.ui.SimpleViewModelFactory
import com.mzazitech.automatedmachine.ui.components.EmptyState
import com.mzazitech.automatedmachine.ui.components.MzaziCard
import com.mzazitech.automatedmachine.ui.components.PrimaryButton
import com.mzazitech.automatedmachine.ui.components.PurchaseStatusChip
import com.mzazitech.automatedmachine.ui.components.SectionHeader
import com.mzazitech.automatedmachine.ui.components.StatTile
import com.mzazitech.automatedmachine.ui.theme.DangerRed
import com.mzazitech.automatedmachine.ui.theme.ElectricBlue
import com.mzazitech.automatedmachine.ui.theme.ElectricCyan
import com.mzazitech.automatedmachine.ui.theme.brandColor
import com.mzazitech.automatedmachine.ui.theme.SuccessGreen
import com.mzazitech.automatedmachine.ui.theme.TextMuted
import com.mzazitech.automatedmachine.ui.theme.TextPrimary
import com.mzazitech.automatedmachine.ui.theme.TextSecondary
import com.mzazitech.automatedmachine.ui.theme.WarningAmber

@Composable
fun DashboardScreen(
    container: AppContainer,
    onNewSale: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    val viewModel: DashboardViewModel = viewModel(
        factory = remember { SimpleViewModelFactory { DashboardViewModel(container) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            HeaderRow(
                operator = container.session.session.value?.name ?: "Operator",
                onAdmin = onOpenAdmin
            )
        }

        if (state.testMode || state.simulation) {
            item {
                ModeBanner(testMode = state.testMode, simulation = state.simulation)
            }
        }

        item {
            RevenueHero(
                today = state.todayRevenue.format(),
                lifetime = state.totalSales.format(),
                successRate = state.successRate
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Successful",
                    value = state.successful.toString(),
                    accent = SuccessGreen,
                    icon = Icons.Filled.CheckCircle,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Failed",
                    value = state.failed.toString(),
                    accent = DangerRed,
                    icon = Icons.Filled.Warning,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Pending",
                    value = state.pending.toString(),
                    accent = WarningAmber,
                    icon = Icons.Filled.Info,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "SIM cards",
                    value = state.availableSims.toString(),
                    caption = if (state.simulation) "Simulated" else "Detected",
                    accent = ElectricCyan,
                    icon = Icons.Filled.Phone,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            PrimaryButton(
                text = "New sale",
                onClick = onNewSale,
                icon = Icons.Filled.ShoppingCart
            )
        }

        item {
            SectionHeader(
                title = "Recent transactions",
                actionLabel = "View all",
                onAction = onOpenHistory
            )
        }

        if (state.recent.isEmpty()) {
            item {
                EmptyState(
                    title = "No sales yet",
                    subtitle = "Start a sale to see live transaction activity here."
                )
            }
        } else {
            items(state.recent, key = { it.id }) { transaction ->
                TransactionRow(transaction = transaction, onClick = { onOpenTransaction(transaction.id) })
            }
        }

        item {
            SectionHeader(title = "Machine status")
            MzaziCard {
                StatusLine("Active delivery jobs", state.activeJobs.toString())
                StatusLine("Total transactions", state.total.toString())
                StatusLine(
                    "SIM profile",
                    state.sims.joinToString(", ") { it.label }.ifBlank { "No SIM detected" }
                )
                StatusLine(
                    "USSD transport",
                    if (state.simulation) "Simulation (no carrier traffic)" else "Live telephony"
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricBlue.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Pull to refresh SIM state from Settings.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HeaderRow(operator: String, onAdmin: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                "MZAZI AUTOMATED MACHINE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = TextMuted
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Hello, $operator",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ElectricBlue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                operator.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ElectricBlue,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
private fun ModeBanner(testMode: Boolean, simulation: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WarningAmber.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = WarningAmber,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = buildString {
                append("TEST MODE")
                if (testMode) append(" · payments simulated")
                if (simulation) append(" · USSD simulated")
                append(". No real money or bundle moves.")
            },
            style = MaterialTheme.typography.labelSmall,
            color = WarningAmber
        )
    }
}

@Composable
private fun RevenueHero(today: String, lifetime: String, successRate: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        ElectricBlue.copy(alpha = 0.28f),
                        ElectricBlue.copy(alpha = 0.06f),
                        Color.Transparent
                    )
                )
            )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "TODAY'S REVENUE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                today,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp),
                color = TextPrimary
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Lifetime sales", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(lifetime, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Success rate", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        "${(successRate * 100).toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        color = SuccessGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: Transaction, onClick: () -> Unit) {
    MzaziCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(transaction.network.brandColor().copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    transaction.network.shortName.take(1),
                    style = MaterialTheme.typography.titleSmall,
                    color = transaction.network.brandColor()
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.bundleName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${Msisdn.mask(transaction.customerMsisdn)} · ${Time.formatRelative(transaction.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    transaction.amount.format(),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                PurchaseStatusChip(transaction.purchaseStatus)
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

