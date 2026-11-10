package com.mzazitech.automatedmachine.ui.screens.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.mzazitech.automatedmachine.ui.SimpleViewModelFactory
import com.mzazitech.automatedmachine.ui.components.KeyValueRow
import com.mzazitech.automatedmachine.ui.components.MzaziCard
import com.mzazitech.automatedmachine.ui.components.PrimaryButton
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

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenAdmin: () -> Unit,
    onSignedOut: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = remember { SimpleViewModelFactory { SettingsViewModel(container) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showNameDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var deviceIdDraft by remember { mutableStateOf("") }
    var deviceKeyDraft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Terminal configuration, security and connectivity.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(Modifier.height(18.dp))

        SectionHeader(title = "Account")
        MzaziCard {
            KeyValueRow("Signed in", state.adminEmail ?: "Test mode (not signed in)")
            KeyValueRow("Role", if (state.adminEmail != null) "Administrator" else "Local operator")
            Spacer(Modifier.height(12.dp))
            SecondaryButton(text = "Admin management", onClick = onOpenAdmin)
            Spacer(Modifier.height(8.dp))
            if (state.adminEmail != null) {
                SecondaryButton(
                    text = "Sign out",
                    onClick = {
                        viewModel.logout()
                        onSignedOut()
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionHeader(title = "Modes")
        MzaziCard {
            ToggleRow(
                title = "Test mode",
                subtitle = "Simulated payments. No real money or bundle moves.",
                checked = state.testMode,
                onCheckedChange = viewModel::setTestMode
            )
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "USSD simulation",
                subtitle = "Keeps USSD requests on the device. Turn off only with verified workflows.",
                checked = state.simulation,
                onCheckedChange = viewModel::setSimulation
            )
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "Auto-retry transport errors",
                subtitle = "Retries only when the carrier never received the request.",
                checked = state.autoRetry,
                onCheckedChange = viewModel::setAutoRetry
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionHeader(title = "Terminal")
        MzaziCard {
            KeyValueRow("Operator label", state.operatorName)
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Change operator label",
                onClick = {
                    nameDraft = state.operatorName
                    showNameDialog = true
                }
            )
            Spacer(Modifier.height(14.dp))
            Text("Preferred SIM", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.sims) { sim ->
                    Pill(
                        label = sim.label,
                        selected = state.simSlot == sim.slotIndex,
                        onClick = { viewModel.setSimSlot(sim.slotIndex) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            SecondaryButton(text = "Refresh SIM list", onClick = viewModel::refreshSims)
        }

        Spacer(Modifier.height(16.dp))

        SectionHeader(title = "Backend")
        MzaziCard {
            KeyValueRow("API base URL", state.apiBaseUrl)
            KeyValueRow("Device ID", state.deviceId ?: "Not provisioned")
            KeyValueRow(
                "Device key",
                if (state.deviceConfigured) "Stored in Keystore" else "Missing",
                if (state.deviceConfigured) SuccessGreen else DangerRed
            )
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = "Configure device credentials",
                onClick = {
                    deviceIdDraft = state.deviceId.orEmpty()
                    deviceKeyDraft = ""
                    showDeviceDialog = true
                }
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = if (state.syncing) "Syncing…" else "Sync catalogue now",
                onClick = viewModel::syncNow,
                loading = state.syncing,
                enabled = state.adminEmail != null
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionHeader(title = "Audit trail")
        MzaziCard {
            if (state.audit.isEmpty()) {
                Text(
                    "No audit events recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                state.audit.take(12).forEach { log ->
                    Column(modifier = Modifier.padding(vertical = 5.dp)) {
                        Text(
                            log.action,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
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

        Spacer(Modifier.height(16.dp))

        SectionHeader(title = "About")
        MzaziCard {
            KeyValueRow("Application", "MZAZI TECH AUTOMATED MACHINE")
            KeyValueRow("Version", state.versionName)
            KeyValueRow("Package", "com.mzazitech.automatedmachine")
            KeyValueRow("Company", "MZAZI TECH INC")
            Spacer(Modifier.height(10.dp))
            Text(
                "USSD codes are operator-specific and are never shipped with the app. " +
                    "Only administrator-verified workflows can execute on a live SIM.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(30.dp))
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            containerColor = MzaziSurfaceElevated,
            title = { Text("Operator label", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(60) },
                    singleLine = true,
                    colors = dialogFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setOperatorName(nameDraft)
                    showNameDialog = false
                }) { Text("Save", color = ElectricBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }

    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            containerColor = MzaziSurfaceElevated,
            title = { Text("Device credentials", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Issued once by an administrator from the backend admin endpoint. " +
                            "Stored encrypted on this device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = deviceIdDraft,
                        onValueChange = { deviceIdDraft = it },
                        label = { Text("Device ID") },
                        singleLine = true,
                        colors = dialogFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deviceKeyDraft,
                        onValueChange = { deviceKeyDraft = it },
                        label = { Text("Device key") },
                        singleLine = true,
                        colors = dialogFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveDeviceCredentials(deviceIdDraft, deviceKeyDraft)
                    showDeviceDialog = false
                }) { Text("Save", color = ElectricBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showDeviceDialog = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }

    if (state.message != null) {
        AlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            containerColor = MzaziSurfaceElevated,
            title = { Text("Notice", color = TextPrimary) },
            text = { Text(state.message.orEmpty(), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = viewModel::consumeMessage) { Text("OK", color = ElectricBlue) }
            }
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ElectricBlue,
                checkedTrackColor = ElectricBlue.copy(alpha = 0.30f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = MzaziSurfaceElevated
            )
        )
    }
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

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ElectricBlue,
    unfocusedBorderColor = MzaziBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = ElectricBlue,
    unfocusedLabelColor = TextMuted,
    cursorColor = ElectricBlue
)
