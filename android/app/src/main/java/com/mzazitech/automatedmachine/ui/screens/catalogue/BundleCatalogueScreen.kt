package com.mzazitech.automatedmachine.ui.screens.catalogue

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.domain.model.Bundle
import com.mzazitech.automatedmachine.domain.model.Network
import com.mzazitech.automatedmachine.ui.SimpleViewModelFactory
import com.mzazitech.automatedmachine.ui.components.EmptyState
import com.mzazitech.automatedmachine.ui.components.MzaziCard
import com.mzazitech.automatedmachine.ui.components.SectionHeader
import com.mzazitech.automatedmachine.ui.theme.ElectricBlue
import androidx.compose.ui.graphics.Color
import com.mzazitech.automatedmachine.ui.theme.MzaziSurfaceElevated
import com.mzazitech.automatedmachine.ui.theme.TextMuted
import com.mzazitech.automatedmachine.ui.theme.TextPrimary
import com.mzazitech.automatedmachine.ui.theme.TextSecondary
import com.mzazitech.automatedmachine.ui.theme.brandColor

@Composable
fun BundleCatalogueScreen(
    container: AppContainer,
    networkCode: String,
    onBuy: (String) -> Unit,
    onBack: () -> Unit
) {
    val initial = if (networkCode.equals("ALL", ignoreCase = true)) null else Network.fromCode(networkCode)
    val viewModel: CatalogueViewModel = viewModel(
        key = "catalogue-$networkCode",
        factory = remember { SimpleViewModelFactory { CatalogueViewModel(container, initial) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val available = state.bundles

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
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
                    Text(
                        "Bundle catalogue",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        "${available.size} product${if (available.size == 1) "" else "s"} available",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterPill(
                        label = "All",
                        selected = state.network == null,
                        accent = ElectricBlue,
                        onClick = { viewModel.selectNetwork(null) }
                    )
                }
                items(Network.entries.toList()) { network ->
                    FilterPill(
                        label = network.shortName,
                        selected = state.network == network,
                        accent = network.brandColor(),
                        onClick = { viewModel.selectNetwork(network) }
                    )
                }
            }
        }

        if (available.isEmpty()) {
            item {
                EmptyState(
                    title = "No bundles configured",
                    subtitle = "An administrator must add products for this network before selling."
                )
            }
        } else {
            Network.entries.forEach { network ->
                val networkBundles = available.filter { it.network == network }
                if (networkBundles.isNotEmpty()) {
                    item {
                        SectionHeader(title = network.displayName)
                    }
                    items(networkBundles, key = { it.id }) { bundle ->
                        BundleRow(bundle = bundle, onBuy = { onBuy(bundle.id) })
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else MzaziSurfaceElevated)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else TextSecondary
        )
    }
}

@Composable
private fun BundleRow(bundle: Bundle, onBuy: () -> Unit) {
    val accent = bundle.network.brandColor()
    MzaziCard(highlighted = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    bundle.sizeLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(bundle.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${bundle.validityDays} days · ${bundle.network.shortName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    bundle.sellingPrice.format(),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricBlue)
                        .clickable { onBuy() }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        "SELL",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}
