package com.mzazitech.automatedmachine.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mzazitech.automatedmachine.mzaziContainer

/**
 * Periodically refreshes the catalogue and operator workflows from the backend.
 * Failures are non-fatal: the terminal keeps selling from its local copy.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.mzaziContainer
        if (!container.session.isLoggedIn()) return Result.success()

        val report = container.sync.pullCatalogue()
        return if (report.succeeded) Result.success() else Result.retry()
    }
}
