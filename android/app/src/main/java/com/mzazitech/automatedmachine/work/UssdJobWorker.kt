package com.mzazitech.automatedmachine.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mzazitech.automatedmachine.mzaziContainer

/**
 * Drains the local USSD queue in the background.
 *
 * Each job is claimed atomically by the repository, so a worker restart, a duplicate
 * enqueue or two operators tapping "retry" at once still produces a single execution.
 */
class UssdJobWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.mzaziContainer
        return runCatching {
            var processed = 0
            while (processed < MAX_JOBS_PER_RUN) {
                val ran = container.jobRunner.runNext()
                if (!ran) break
                processed++
            }
            Result.success()
        }.getOrElse {
            // Let WorkManager retry with backoff instead of losing the queue.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_JOBS_PER_RUN = 5
        const val MAX_ATTEMPTS = 3
    }
}
