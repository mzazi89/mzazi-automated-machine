package com.mzazitech.automatedmachine.ui.screens.purchase

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.core.Msisdn
import com.mzazitech.automatedmachine.domain.model.Network
import com.mzazitech.automatedmachine.domain.model.PaymentStatus
import com.mzazitech.automatedmachine.ui.SimpleViewModelFactory
import com.mzazitech.automatedmachine.ui.components.KeyValueRow
import com.mzazitech.automatedmachine.ui.components.MzaziCard
import com.mzazitech.automatedmachine.ui.components.PaymentStatusChip
import com.mzazitech.automatedmachine.ui.components.PrimaryButton
import com.mzazitech.automatedmachine.ui.components.ProgressLine
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
import com.mzazitech.automatedmachine.ui.theme.brandColor

@Composable
fun PurchaseScreen(
    container: AppContainer,
    initialBundleId: String,
    onBack: () -> Unit,
    onTrackDelivery: (String) -> Unit
) {
    val viewModel: PurchaseViewModel = viewModel(
        factory = remember { SimpleViewModelFactory { PurchaseViewModel(container) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialBundleId) {
        viewModel.preselect(initialBundleId)
    }

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
            Text("New sale", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        }

        Spacer(Modifier.height(16.dp))

        SectionHeader(title = "Network")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Network.entries.toList()) { network ->
                NetworkPill(
                    network = network,
                    selected = state.network == network,
                    onClick = { viewModel.selectNetwork(network) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionHeader(title = "Bundle", actionLabel = null)
        if (state.bundles.isEmpty()) {
            MzaziCard {
                Text(
                    "No active bundles for ${state.network.shortName}. An administrator must add them first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.bundles.forEach { bundle ->
                    val selected = state.selected?.id == bundle.id
                    MzaziCard(
                        highlighted = selected,
                        onClick = { viewModel.selectBundle(bundle) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bundle.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${bundle.sizeLabel} · ${bundle.validityDays} days",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Text(
                                bundle.sellingPrice.format(),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) ElectricBlue else TextPrimary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionHeader(title = "Customer")
        OutlinedTextField(
            value = state.phone,
            onValueChange = viewModel::onPhoneChange,
            label = { Text("M-Pesa phone number") },
            placeholder = { Text("07XX XXX XXX") },
            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, tint = TextMuted) },
            isError = state.phoneError != null,
            supportingText = {
                val hint = state.phoneError
                    ?: Msisdn.guessNetwork(state.phone)?.let { "Likely $it — confirm with the customer." }
                    ?: "Digits only, Kenyan number."
                Text(hint, style = MaterialTheme.typography.labelSmall)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MzaziSurfaceElevated,
                unfocusedContainerColor = MzaziSurfaceElevated,
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

        Spacer(Modifier.height(12.dp))

        SectionHeader(title = "Sending SIM")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.sims) { sim ->
                SimPill(
                    label = sim.label,
                    selected = state.simSlot == sim.slotIndex,
                    onClick = { viewModel.selectSim(sim.slotIndex) }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionHeader(title = "Payment")
        MzaziCard {
            KeyValueRow("Bundle", state.selected?.name ?: "—")
            KeyValueRow("Amount", state.selected?.sellingPrice?.format() ?: "—", ElectricBlue)
            KeyValueRow("Method", "M-Pesa (STK push)")
            KeyValueRow("Payment proof", "Verified by backend callback")
            if (state.testMode) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "TEST MODE — the payment is simulated, no STK push is sent.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (state.stage != null) {
            Column {
                ProgressLine(progress = 0.6f)
                Spacer(Modifier.height(8.dp))
                Text(state.stage.orEmpty(), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
            }
        }

        if (state.error != null && state.created == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DangerRed.copy(alpha = 0.12f))
                    .padding(12.dp)
            ) {
                Text(state.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = DangerRed)
            }
            Spacer(Modifier.height(12.dp))
        }

        val created = state.created
        if (created != null) {
            MzaziCard(highlighted = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (created.paymentStatus == PaymentStatus.PAID) Icons.Filled.CheckCircle
                        else Icons.Filled.Info,
                        contentDescription = null,
                        tint = if (created.paymentStatus == PaymentStatus.PAID) SuccessGreen else WarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (created.paymentStatus == PaymentStatus.PAID) "Payment confirmed"
                        else "Payment ${created.paymentStatus.name.lowercase()}",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(10.dp))
                KeyValueRow("Reference", created.reference)
                KeyValueRow("Customer", Msisdn.mask(created.customerMsisdn))
                if (created.mpesaReceipt != null) {
                    KeyValueRow("M-Pesa receipt", created.mpesaReceipt, SuccessGreen)
                }
                Spacer(Modifier.height(6.dp))
                PaymentStatusChip(created.paymentStatus)
                Spacer(Modifier.height(14.dp))
                if (created.paymentStatus == PaymentStatus.PAID) {
                    PrimaryButton(
                        text = "Track delivery",
                        onClick = { onTrackDelivery(created.id) }
                    )
                } else {
                    SecondaryButton(
                        text = "Start another sale",
                        onClick = {
                            viewModel.clearResult()
                            onBack()
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (created == null) {
            PrimaryButton(
                text = if (state.testMode) "Confirm test sale" else "Pay with M-Pesa",
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                loading = state.submitting
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun NetworkPill(network: Network, selected: Boolean, onClick: () -> Unit) {
    val accent = network.brandColor()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else MzaziSurfaceElevated)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            network.shortName,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) accent else TextSecondary
        )
    }
}

@Composable
private fun SimPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ElectricBlue.copy(alpha = 0.16f) else MzaziSurfaceElevated)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) ElectricBlue else TextSecondary
        )
    }
}
