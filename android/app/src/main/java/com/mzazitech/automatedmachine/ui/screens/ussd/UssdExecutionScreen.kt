package com.mzazitech.automatedmachine.ui.screens.ussd

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.core.Msisdn
import com.mzazitech.automatedmachine.core.Time
import com.mzazitech.automatedmachine.domain.model.PurchaseStatus
import com.mzazitech.automatedmachine.ui.SimpleViewModelFactory
import com.mzazitech.automatedmachine.ui.components.KeyValueRow
import com.mzazitech.automatedmachine.ui.components.MzaziCard
import com.mzazitech.automatedmachine.ui.components.PaymentStatusChip
import com.mzazitech.automatedmachine.ui.components.PrimaryButton
import com.mzazitech.automatedmachine.ui.components.PurchaseStatusChip
import com.mzazitech.automatedmachine.ui.components.SecondaryButton
import com.mzazitech.automatedmachine.ui.components.SectionHeader
import com.mzazitech.automatedmachine.ui.theme.DangerRed
import com.mzazitech.automatedmachine.ui.theme.ElectricBlue
import com.mzazitech.automatedmachine.ui.theme.MzaziBorder
import com.mzazitech.automatedmachine.ui.theme.MzaziSurfaceElevated
import com.mzazitech.automatedmachine.ui.theme.SuccessGreen
import com.mzazitech.automatedmachine.ui.theme.TextMuted
import com.mzazitech.automatedmachine.ui.theme.TextPrimary
import com.mzazitech.automatedmachine.ui.theme.TextSecondary
import com.mzazitech.automatedmachine.ui.theme.WarningAmber

@Composable
fun UssdExecutionScreen(
    container: AppContainer,
    transactionId: String,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val viewModel: UssdViewModel = viewModel(
        key = "ussd-$transactionId",
        factory = remember { SimpleViewModelFactory { UssdViewModel(container, transactionId) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    val transaction = state.transaction

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(6.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("USSD execution", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        }

        Spacer(Modifier.height(16.dp))

        if (transaction == null) {
            MzaziCard {
                Text(
                    "This transaction is no longer available on the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        } else {
            MzaziCard(highlighted = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            transaction.bundleName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            transaction.reference,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    PurchaseStatusChip(transaction.purchaseStatus)
                }

                Spacer(Modifier.height(14.dp))
                KeyValueRow("Customer", Msisdn.mask(transaction.customerMsisdn))
                KeyValueRow("Network", transaction.network.displayName)
                KeyValueRow("Amount", transaction.amount.format(), ElectricBlue)
                KeyValueRow("SIM slot", "SIM ${(transaction.simSlot ?: 0) + 1}")
                KeyValueRow("Created", Time.formatDateTime(transaction.createdAt))
                Spacer(Modifier.height(8.dp))
                PaymentStatusChip(transaction.paymentStatus)
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MzaziCard(modifier = Modifier.weight(1f)) {
                    Text("JOB STATE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        (state.job?.status?.name ?: "NOT QUEUED"),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Attempts ${state.job?.attemptCount ?: 0}/${state.job?.maxAttempts ?: 0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                MzaziCard(modifier = Modifier.weight(1f)) {
                    Text("TRANSPORT", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (container.session.isLoggedIn()) "Backend linked" else "Local terminal",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Simulation: ${if (state.simulation) "yes" else "no"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (transaction.purchaseStatus == PurchaseStatus.AWAITING_MANUAL_CONFIRMATION) {
                MzaziCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Manual confirmation required",
                            style = MaterialTheme.typography.titleSmall,
                            color = WarningAmber
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Android cannot complete a live USSD menu on the app's behalf. " +
                            "Dial the code shown in the workflow on this device, finish the menu, " +
                            "then record the carrier's answer below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (state.job?.lastError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.job?.lastError.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            SectionHeader(title = "Execution log")
            if (state.progress.isEmpty()) {
                MzaziCard {
                    Text(
                        "No execution events yet. Use “Send now” to run the delivery immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            } else {
                MzaziCard {
                    state.progress.forEach { line ->
                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(ElectricBlue)
                                    .align(Alignment.CenterVertically)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                line,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            if (state.message != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ElectricBlue.copy(alpha = 0.10f))
                        .padding(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.message.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            if (!transaction.purchaseStatus.isTerminal) {
                PrimaryButton(
                    text = if (state.running) "Sending USSD…" else "Send now",
                    onClick = viewModel::runNow,
                    loading = state.running,
                    enabled = state.job != null,
                    icon = Icons.Filled.PlayArrow
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Record carrier outcome",
                    onClick = { showConfirm = true }
                )
            } else {
                PrimaryButton(text = "Back to dashboard", onClick = onDone)
            }

            Spacer(Modifier.height(10.dp))
            if (!transaction.purchaseStatus.isTerminal) {
                SecondaryButton(text = "Cancel this delivery", onClick = viewModel::cancelDelivery)
            }
        }

        Spacer(Modifier.height(30.dp))
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = MzaziSurfaceElevated,
            title = { Text("Record carrier outcome", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Only confirm after checking the carrier response or SMS receipt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (receipt or reason)") },
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = MzaziBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = ElectricBlue,
                            unfocusedLabelColor = TextMuted,
                            cursorColor = ElectricBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmManually(true, note)
                    showConfirm = false
                    note = ""
                }) { Text("Delivered", color = SuccessGreen) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.confirmManually(false, note)
                        showConfirm = false
                        note = ""
                    }) { Text("Not delivered", color = DangerRed) }
                    TextButton(onClick = { showConfirm = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            }
        )
    }
}
