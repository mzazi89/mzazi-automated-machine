package com.mzazitech.automatedmachine

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.mzazitech.automatedmachine.work.JobScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MzaziApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()

        appScope.launch {
            container.bootstrap()
            JobScheduler.schedulePeriodicSync(this@MzaziApplication)
            // Recover any job that was interrupted by a process death.
            JobScheduler.enqueueUssdDrain(this@MzaziApplication)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val jobs = NotificationChannel(
            CHANNEL_JOBS,
            "Bundle delivery",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Results of automated USSD bundle purchases."
        }
        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            "Machine alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Payment and configuration problems that need an operator."
        }
        manager.createNotificationChannels(listOf(jobs, alerts))
    }

    companion object {
        const val CHANNEL_JOBS = "mzazi_jobs"
        const val CHANNEL_ALERTS = "mzazi_alerts"
    }
}

/** Convenience accessor used by workers and the Compose tree. */
val Context.mzaziContainer: AppContainer
    get() = (applicationContext as MzaziApplication).container
