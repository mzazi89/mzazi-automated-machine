package com.mzazitech.automatedmachine.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MzaziColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = ElectricBlueDeep,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricCyan,
    onSecondary = MzaziBlack,
    tertiary = SuccessGreen,
    background = MzaziBlack,
    onBackground = TextPrimary,
    surface = MzaziSurface,
    onSurface = TextPrimary,
    surfaceVariant = MzaziSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = MzaziBorder,
    error = DangerRed,
    onError = Color.White
)

@Composable
fun MzaziTechTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MzaziBlack.toArgb()
            window.navigationBarColor = MzaziBlack.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = MzaziColorScheme,
        typography = MzaziTypography,
        shapes = MzaziShapes,
        content = content
    )
}
