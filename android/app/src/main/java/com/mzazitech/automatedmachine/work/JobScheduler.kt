package com.mzazitech.automatedmachine.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object JobScheduler {

    private const val USSD_DRAIN = "mzazi_ussd_drain"
    private const val SYNC = "mzazi_catalogue_sync"

    /** Kicks the USSD queue. Unique work keeps a double tap from stacking runners. */
    fun enqueueUssdDrain(context: Context) {
        val request = OneTimeWorkRequestBuilder<UssdJobWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(USSD_DRAIN)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            USSD_DRAIN,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    fun enqueueUssdDrainNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<UssdJobWorker>()
            .addTag(USSD_DRAIN)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            USSD_DRAIN,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun schedulePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
