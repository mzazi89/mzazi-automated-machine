package com.mzazitech.automatedmachine.ui.screens.networks

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.domain.model.Network
import com.mzazitech.automatedmachine.ui.SimpleViewModelFactory
import com.mzazitech.automatedmachine.ui.components.MzaziCard
import com.mzazitech.automatedmachine.ui.components.MzaziBackground
import com.mzazitech.automatedmachine.ui.components.SecondaryButton
import com.mzazitech.automatedmachine.ui.screens.catalogue.CatalogueViewModel
import com.mzazitech.automatedmachine.ui.theme.ElectricBlue
import com.mzazitech.automatedmachine.ui.theme.TextMuted
import com.mzazitech.automatedmachine.ui.theme.TextPrimary
import com.mzazitech.automatedmachine.ui.theme.TextSecondary
import com.mzazitech.automatedmachine.ui.theme.brandColor

@Composable
fun NetworkSelectScreen(
    container: AppContainer,
    onNetworkSelected: (Network) -> Unit,
    onBrowseAll: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: CatalogueViewModel = viewModel(
        factory = remember { SimpleViewModelFactory { CatalogueViewModel(container, null) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    MzaziBackground {
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
                Text("Select network", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Choose the operator whose bundle the customer is buying.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(Modifier.height(20.dp))

            Network.entries.forEach { network ->
                NetworkOption(
                    network = network,
                    bundleCount = state.countsByNetwork[network] ?: 0,
                    onClick = { onNetworkSelected(network) }
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(4.dp))
            SecondaryButton(text = "Browse all bundles", onClick = onBrowseAll)
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun NetworkOption(network: Network, bundleCount: Int, onClick: () -> Unit) {
    val accent = network.brandColor()
    MzaziCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    network.shortName.take(1),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    network.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (bundleCount == 0) "No active bundles configured"
                    else "$bundleCount active bundle${if (bundleCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bundleCount == 0) TextMuted else TextSecondary
                )
            }
            Text(
                "›",
                style = MaterialTheme.typography.headlineSmall,
                color = ElectricBlue
            )
        }
    }
}
