package com.mzazitech.automatedmachine

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mzazitech.automatedmachine.ui.MzaziApp
import com.mzazitech.automatedmachine.ui.theme.MzaziBlack
import com.mzazitech.automatedmachine.ui.theme.MzaziTechTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Results are surfaced contextually by the USSD engine. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestTelephonyPermissions()

        setContent {
            MzaziTechTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MzaziBlack)
                ) {
                    MzaziApp()
                }
            }
        }
    }

    /**
     * READ_PHONE_STATE is needed to enumerate SIMs, CALL_PHONE to send USSD.
     * A refusal is handled gracefully: the app still works in simulation mode.
     */
    private fun requestTelephonyPermissions() {
        val permissions = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}
