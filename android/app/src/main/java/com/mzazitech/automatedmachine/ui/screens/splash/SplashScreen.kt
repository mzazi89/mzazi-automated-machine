package com.mzazitech.automatedmachine.ui.screens.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mzazitech.automatedmachine.ui.components.ProgressLine
import com.mzazitech.automatedmachine.ui.theme.ElectricBlue
import com.mzazitech.automatedmachine.ui.theme.ElectricCyan
import com.mzazitech.automatedmachine.ui.theme.MzaziBlack
import com.mzazitech.automatedmachine.ui.theme.TextMuted
import com.mzazitech.automatedmachine.ui.theme.TextPrimary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0.05f,
        animationSpec = tween(durationMillis = 1_400),
        label = "splashProgress"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(1_600)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MzaziBlack)
            .background(
                Brush.radialGradient(
                    colors = listOf(ElectricBlue.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(540f, 700f),
                    radius = 1100f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(listOf(ElectricBlue, ElectricCyan))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "M",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp
                    ),
                    color = Color.White
                )
            }

            Spacer(Modifier.height(26.dp))

            Text(
                "MZAZI",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp
                ),
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "TECH AUTOMATED MACHINE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                color = TextMuted
            )

            Spacer(Modifier.height(52.dp))
            ProgressLine(progress = progress, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Text(
                "Initialising secure terminal…",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "MZAZI TECH INC",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = TextMuted
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Nairobi · Kenya",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted.copy(alpha = 0.7f)
            )
        }
    }
}
