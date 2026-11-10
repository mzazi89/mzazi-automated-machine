package com.mzazitech.automatedmachine.ui.theme

import androidx.compose.ui.graphics.Color
import com.mzazitech.automatedmachine.domain.model.Network

/** Brand accent used for a network's chip, badge and progress highlights. */
fun Network.brandColor(): Color = when (this) {
    Network.SAFARICOM -> SuccessGreen
    Network.AIRTEL -> DangerRed
    Network.TELKOM -> ElectricCyan
}
