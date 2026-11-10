package com.mzazitech.automatedmachine.ui.screens.admin

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.core.Time
import com.mzazitech.automatedmachine.domain.model.Bundle
import com.mzazitech.automatedmachine.domain.model.Network
import com.mzazitech.automatedmachine.domain.model.UssdWorkflow
import com.mzazitech.automatedmachine.ui.SimpleViewModelFactory
import com.mzazitech.automatedmachine.ui.components.MzaziCard
import com.mzazitech.automatedmachine.ui.components.SecondaryButton
import com.mzazitech.automatedmachine.ui.components.SectionHeader
import com.mzazitech.automatedmachine.ui.components.StatusChip
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
fun AdminScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: AdminViewModel = viewModel(
        factory = remember { SimpleViewModelFactory { AdminViewModel(container) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showBundleDialog by remember { mutableStateOf(false) }
    var showWorkflowDialog by remember { mutableStateOf(false) }

    val networkBundles = state.bundles.filter { it.network == state.network }
    val networkWorkflows = state.workflows.filter { it.network == state.network }

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
            Column {
                Text("Admin management", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text(
                    "Bundles, operator workflows and audit trail",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Network.entries.toList()) { network ->
                Pill(
                    label = network.shortName,
                    selected = state.network == network,
                    onClick = { viewModel.selectNetwork(network) }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionHeader(title = "Bundles · ${state.network.shortName}")
        if (networkBundles.isEmpty()) {
            MzaziCard {
                Text(
                    "No bundles for this network yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                networkBundles.forEach { bundle ->
                    BundleAdminRow(
                        bundle = bundle,
                        onToggle = { viewModel.toggleBundle(bundle) },
                        onDelete = { viewModel.deleteBundle(bundle) }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            text = "Add bundle",
            onClick = { showBundleDialog = true }
        )

        Spacer(Modifier.height(20.dp))

        SectionHeader(title = "USSD workflows · ${state.network.shortName}")
        if (networkWorkflows.isEmpty()) {
            MzaziCard {
                Text(
                    "No workflow configured. Deliveries cannot run until a verified flow exists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                networkWorkflows.forEach { workflow -> WorkflowRow(workflow) }
            }
        }

        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            text = "Add verified workflow",
            onClick = { showWorkflowDialog = true }
        )

        Spacer(Modifier.height(20.dp))

        SectionHeader(title = "Audit trail")
        MzaziCard {
            if (state.audit.isEmpty()) {
                Text("No events yet.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            } else {
                state.audit.take(15).forEach { log ->
                    Column(modifier = Modifier.padding(vertical = 5.dp)) {
                        Text(log.action, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        Text(
                            "${log.actor} · ${Time.formatDateTime(log.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        if (!log.detail.isNullOrBlank()) {
                            Text(
                                log.detail.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }

    if (showBundleDialog) {
        AddBundleDialog(
            network = state.network,
            workflows = networkWorkflows,
            busy = state.busy,
            onDismiss = { showBundleDialog = false },
            onSave = { name, size, price, cost, validity, workflowId ->
                viewModel.createBundle(state.network, name, size, price, cost, validity, workflowId)
                showBundleDialog = false
            }
        )
    }

    if (showWorkflowDialog) {
        AddWorkflowDialog(
            network = state.network,
            onDismiss = { showWorkflowDialog = false },
            onSave = { name, codes, success, failure, verifiedBy ->
                viewModel.saveWorkflow(state.network, name, codes, success, failure, verifiedBy)
                showWorkflowDialog = false
            }
        )
    }

    if (state.message != null) {
        AlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            containerColor = MzaziSurfaceElevated,
            title = { Text("Admin", color = TextPrimary) },
            text = { Text(state.message.orEmpty(), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = viewModel::consumeMessage) { Text("OK", color = ElectricBlue) }
            }
        )
    }
}

@Composable
private fun BundleAdminRow(bundle: Bundle, onToggle: () -> Unit, onDelete: () -> Unit) {
    MzaziCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(bundle.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${bundle.sizeLabel} · ${bundle.sellingPrice.format()} · ${bundle.validityDays}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            StatusChip(
                text = if (bundle.enabled) "On" else "Off",
                color = if (bundle.enabled) SuccessGreen else TextMuted
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = bundle.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ElectricBlue,
                    checkedTrackColor = ElectricBlue.copy(alpha = 0.30f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = MzaziSurfaceElevated
                )
            )
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Text(
                "Delete",
                style = MaterialTheme.typography.labelSmall,
                color = DangerRed,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDelete() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun WorkflowRow(workflow: UssdWorkflow) {
    val problems = workflow.validate()
    val verified = workflow.verifiedBy == "MZAZI-TEST-ONLY"

    MzaziCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(workflow.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${workflow.steps.size} step(s) · v${workflow.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            StatusChip(
                text = when {
                    verified -> "Test only"
                    !workflow.enabled -> "Disabled"
                    problems.isEmpty() -> "Verified"
                    else -> "Incomplete"
                },
                color = when {
                    verified -> WarningAmber
                    !workflow.enabled -> TextMuted
                    problems.isEmpty() -> SuccessGreen
                    else -> DangerRed
                }
            )
        }
        if (problems.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            problems.take(2).forEach { problem ->
                Text("• $problem", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
        if (workflow.verifiedBy != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Verified by: ${workflow.verifiedBy}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun AddBundleDialog(
    network: Network,
    workflows: List<UssdWorkflow>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, size: String, price: String, cost: String, validity: String, workflowId: String) -> Unit
) {
    var name by remember { mutableStateOf("${network.shortName} 1 GB") }
    var size by remember { mutableStateOf("1024") }
    var price by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var validity by remember { mutableStateOf("30") }
    val defaultWorkflow = workflows.firstOrNull { it.validate().isEmpty() }?.id
        ?: workflows.firstOrNull()?.id.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MzaziSurfaceElevated,
        title = { Text("Add bundle · ${network.shortName}", color = TextPrimary) },
        text = {
            Column {
                AdminField("Bundle name", name) { name = it }
                Spacer(Modifier.height(8.dp))
                AdminField("Size (MB)", size) { size = it }
                Spacer(Modifier.height(8.dp))
                AdminField("Selling price (KES)", price) { price = it }
                Spacer(Modifier.height(8.dp))
                AdminField("Cost price (KES, optional)", cost) { cost = it }
                Spacer(Modifier.height(8.dp))
                AdminField("Validity (days)", validity) { validity = it }
                if (workflows.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Workflow: ${workflows.firstOrNull { it.id == defaultWorkflow }?.name ?: "—"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                } else {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Create a verified workflow first, otherwise this bundle cannot deliver.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, size, price, cost, validity, defaultWorkflow) },
                enabled = !busy && defaultWorkflow.isNotBlank()
            ) { Text("Save", color = ElectricBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
private fun AddWorkflowDialog(
    network: Network,
    onDismiss: () -> Unit,
    onSave: (name: String, codes: String, success: String, failure: String, verifiedBy: String) -> Unit
) {
    var name by remember { mutableStateOf("${network.shortName} verified flow") }
    var codes by remember { mutableStateOf("") }
    var success by remember { mutableStateOf("(?i)(successful|has been activated|you have purchased)") }
    var failure by remember { mutableStateOf("(?i)(failed|unsuccessful|insufficient|declined)") }
    var verifiedBy by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MzaziSurfaceElevated,
        title = { Text("Verified USSD workflow", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Enter codes exactly as confirmed with ${network.displayName}. " +
                        "Placeholders: {msisdn}, {msisdnLocal}, {amount}, {bundleSizeMb}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                AdminField("Workflow name", name) { name = it }
                Spacer(Modifier.height(8.dp))
                AdminField("USSD step(s), one per line", codes, maxLines = 4) { codes = it }
                Spacer(Modifier.height(8.dp))
                AdminField("Success receipt regex", success) { success = it }
                Spacer(Modifier.height(8.dp))
                AdminField("Failure receipt regex", failure) { failure = it }
                Spacer(Modifier.height(8.dp))
                AdminField("Verified by (name / operator ref)", verifiedBy) { verifiedBy = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, codes, success, failure, verifiedBy) }) {
                Text("Save & enable", color = ElectricBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
private fun AdminField(
    label: String,
    value: String,
    maxLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = maxLines == 1,
        maxLines = maxLines,
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

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
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
