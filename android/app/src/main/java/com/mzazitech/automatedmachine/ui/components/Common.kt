package com.mzazitech.automatedmachine.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mzazitech.automatedmachine.domain.model.PaymentStatus
import com.mzazitech.automatedmachine.domain.model.PurchaseStatus
import com.mzazitech.automatedmachine.ui.theme.DangerRed
import com.mzazitech.automatedmachine.ui.theme.ElectricBlue
import com.mzazitech.automatedmachine.ui.theme.ElectricCyan
import com.mzazitech.automatedmachine.ui.theme.MzaziBlack
import com.mzazitech.automatedmachine.ui.theme.MzaziBorder
import com.mzazitech.automatedmachine.ui.theme.MzaziSurface
import com.mzazitech.automatedmachine.ui.theme.MzaziSurfaceElevated
import com.mzazitech.automatedmachine.ui.theme.PendingGrey
import com.mzazitech.automatedmachine.ui.theme.SuccessGreen
import com.mzazitech.automatedmachine.ui.theme.TextMuted
import com.mzazitech.automatedmachine.ui.theme.TextPrimary
import com.mzazitech.automatedmachine.ui.theme.TextSecondary
import com.mzazitech.automatedmachine.ui.theme.WarningAmber

/** Ambient background with the electric-blue glow used across the app. */
@Composable
fun MzaziBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MzaziBlack)
            .background(
                Brush.radialGradient(
                    colors = listOf(ElectricBlue.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(120f, -40f),
                    radius = 900f
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(ElectricCyan.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(950f, 1600f),
                    radius = 1100f
                )
            )
    ) {
        content()
    }
}

@Composable
fun MzaziCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) ElectricBlue.copy(alpha = 0.55f) else MzaziBorder,
        animationSpec = tween(280),
        label = "cardBorder"
    )

    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(
            Brush.verticalGradient(
                listOf(
                    if (highlighted) ElectricBlue.copy(alpha = 0.10f) else MzaziSurface,
                    MzaziSurfaceElevated.copy(alpha = 0.92f)
                )
            )
        )

    val shaped = if (onClick != null) {
        base.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else base

    Card(
        modifier = modifier.then(shaped),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            letterSpacing = 1.2.sp
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = ElectricBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ElectricBlue,
            contentColor = Color.White,
            disabledContainerColor = ElectricBlue.copy(alpha = 0.25f),
            disabledContentColor = TextMuted
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onClick() },
        color = MzaziSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MzaziBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) TextPrimary else TextMuted
            )
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = ElectricBlue,
    caption: String? = null,
    icon: ImageVector? = null
) {
    MzaziCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 22.dp),
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1
        )
    }
}

@Composable
fun PaymentStatusChip(status: PaymentStatus) {
    val (label, color) = when (status) {
        PaymentStatus.PAID -> "Paid" to SuccessGreen
        PaymentStatus.PENDING -> "Awaiting pay" to WarningAmber
        PaymentStatus.FAILED -> "Payment failed" to DangerRed
        PaymentStatus.CANCELLED -> "Cancelled" to PendingGrey
        PaymentStatus.REVERSED -> "Reversed" to DangerRed
        PaymentStatus.REFUNDED -> "Refunded" to PendingGrey
    }
    StatusChip(label, color)
}

@Composable
fun PurchaseStatusChip(status: PurchaseStatus) {
    val (label, color) = when (status) {
        PurchaseStatus.QUEUED -> "Queued" to PendingGrey
        PurchaseStatus.RUNNING -> "Sending" to ElectricCyan
        PurchaseStatus.AWAITING_MANUAL_CONFIRMATION -> "Confirm" to WarningAmber
        PurchaseStatus.SUCCESS -> "Delivered" to SuccessGreen
        PurchaseStatus.FAILED -> "Failed" to DangerRed
        PurchaseStatus.CANCELLED -> "Cancelled" to PendingGrey
    }
    StatusChip(label, color)
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(ElectricBlue.copy(alpha = 0.10f))
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "progress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MzaziBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(ElectricBlue, ElectricCyan)))
        )
    }
}

@Composable
fun KeyValueRow(label: String, value: String, valueColor: Color = TextPrimary) {
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
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
