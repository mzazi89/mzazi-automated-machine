package com.mzazitech.automatedmachine.data.repository

import com.mzazitech.automatedmachine.core.Msisdn
import com.mzazitech.automatedmachine.core.Time
import com.mzazitech.automatedmachine.data.local.TransactionDao
import com.mzazitech.automatedmachine.data.local.UssdJobDao
import com.mzazitech.automatedmachine.data.local.toDomain
import com.mzazitech.automatedmachine.data.ussd.SimulatorUssdExecutor
import com.mzazitech.automatedmachine.domain.model.Network
import com.mzazitech.automatedmachine.domain.model.PaymentStatus
import com.mzazitech.automatedmachine.domain.model.PurchaseStatus
import com.mzazitech.automatedmachine.domain.model.UssdFailureCategory
import com.mzazitech.automatedmachine.domain.model.UssdJob
import com.mzazitech.automatedmachine.domain.model.UssdJobStatus
import com.mzazitech.automatedmachine.domain.model.UssdRunParams
import com.mzazitech.automatedmachine.domain.model.UssdRunResult
import com.mzazitech.automatedmachine.domain.ussd.UssdEngine
import com.mzazitech.automatedmachine.domain.ussd.UssdExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Executes queued USSD jobs exactly once.
 *
 * The single most important guard in the whole app lives here: a job only runs when the
 * transaction is already PAID according to the backend. A carrier-side USSD purchase
 * cannot be reversed, so the runner refuses to act on an unverified payment.
 */
class UssdJobRunner(
    private val jobDao: UssdJobDao,
    private val transactionDao: TransactionDao,
    private val workflowRepository: WorkflowRepository,
    private val settings: SettingsRepository,
    private val realExecutor: UssdExecutor,
    private val simExecutor: SimulatorUssdExecutor,
    private val audit: AuditRepository
) {

    fun observeActive(): Flow<List<UssdJob>> =
        jobDao.observeActive().map { list -> list.map { it.toDomain() } }

    fun activeCount(): Flow<Int> = jobDao.observeActiveCount()

    /**
     * Runs at most one due job. Returns true when work was performed, so callers can
     * drain the queue without spinning forever.
     */
    suspend fun runNext(): Boolean {
        val now = Time.nowMillis()
        val next = jobDao.nextRunnable(now) ?: return false
        val claimed = jobDao.claim(next.id, now)
        if (claimed != 1) return false
        execute(next.id)
        return true
    }

    suspend fun execute(jobId: String): UssdRunResult? {
        val job = jobDao.byId(jobId) ?: return null
        val transaction = transactionDao.byId(job.transactionId) ?: run {
            failJob(job, "Transaction ${job.transactionId} no longer exists.")
            return null
        }

        if (PaymentStatus.fromCode(transaction.paymentStatus) != PaymentStatus.PAID) {
            val reason = "Refused to deliver: payment is not verified as PAID."
            failJob(job, reason)
            transactionDao.updatePurchaseState(
                id = transaction.id,
                purchaseStatus = PurchaseStatus.FAILED.name,
                reason = reason,
                manual = false,
                updatedAt = Time.nowMillis()
            )
            audit.record("system", "ussd.blocked_unpaid", transaction.id, reason)
            return null
        }

        transactionDao.updatePurchaseState(
            id = transaction.id,
            purchaseStatus = PurchaseStatus.RUNNING.name,
            reason = null,
            manual = false,
            updatedAt = Time.nowMillis()
        )

        val network = Network.fromCode(job.network)
        val workflow = workflowRepository.forNetwork(network ?: Network.SAFARICOM, job.workflowId)
            ?: run {
                val reason = "No verified USSD workflow is configured for this network."
                failJob(job, reason)
                transactionDao.updatePurchaseState(
                    transaction.id,
                    PurchaseStatus.FAILED.name,
                    reason,
                    false,
                    Time.nowMillis()
                )
                return null
            }

        val simulation = settings.isSimulationMode()
        val executor: UssdExecutor = if (simulation) simExecutor else realExecutor
        val engine = UssdEngine(executor)

        val msisdn = Msisdn.normalize(transaction.customerMsisdn) ?: transaction.customerMsisdn
        val params = UssdRunParams(
            msisdn = msisdn,
            msisdnLocal = Msisdn.toNational(msisdn),
            amountShillings = transaction.amountCents / 100,
            bundleSizeMb = transaction.bundleSizeMb,
            bundleCode = transaction.bundleId,
            simSubscriptionId = null,
            simSlot = job.simSlot
        )

        val result = engine.run(workflow, params) { progress ->
            audit.record("ussd", "ussd.progress", job.id, progress)
        }

        applyResult(job, result, simulation)
        return result
    }

    private suspend fun applyResult(
        job: com.mzazitech.automatedmachine.data.local.UssdJobEntity,
        result: UssdRunResult,
        simulation: Boolean
    ) {
        val now = Time.nowMillis()
        when (result) {
            is UssdRunResult.Success -> {
                jobDao.update(job.copy(status = UssdJobStatus.SUCCESS.name, lastError = null, updatedAt = now))
                transactionDao.updatePurchaseState(
                    job.transactionId,
                    PurchaseStatus.SUCCESS.name,
                    null,
                    false,
                    now
                )
                audit.record(
                    "ussd",
                    if (simulation) "ussd.simulated_success" else "ussd.success",
                    job.transactionId,
                    result.response.take(300)
                )
            }

            is UssdRunResult.NeedsManualConfirmation -> {
                jobDao.update(
                    job.copy(
                        status = UssdJobStatus.AWAITING_MANUAL_CONFIRMATION.name,
                        lastError = result.reason,
                        updatedAt = now
                    )
                )
                transactionDao.updatePurchaseState(
                    job.transactionId,
                    PurchaseStatus.AWAITING_MANUAL_CONFIRMATION.name,
                    result.reason,
                    true,
                    now
                )
                audit.record("ussd", "ussd.manual_confirmation_required", job.transactionId, result.reason)
            }

            is UssdRunResult.Failed -> {
                val canRetry = result.retryable &&
                    result.category == UssdFailureCategory.NETWORK_ERROR &&
                    job.attemptCount < job.maxAttempts &&
                    settings.isAutoRetryEnabled()

                if (canRetry) {
                    jobDao.update(
                        job.copy(
                            status = UssdJobStatus.QUEUED.name,
                            lastError = result.reason,
                            nextAttemptAt = now + RETRY_BACKOFF_MS * job.attemptCount,
                            updatedAt = now
                        )
                    )
                    transactionDao.updatePurchaseState(
                        job.transactionId,
                        PurchaseStatus.QUEUED.name,
                        result.reason,
                        false,
                        now
                    )
                } else {
                    jobDao.update(
                        job.copy(
                            status = UssdJobStatus.FAILED.name,
                            lastError = result.reason,
                            updatedAt = now
                        )
                    )
                    transactionDao.updatePurchaseState(
                        job.transactionId,
                        PurchaseStatus.FAILED.name,
                        result.reason,
                        result.category == UssdFailureCategory.INTERACTIVE_UNSUPPORTED,
                        now
                    )
                }
                audit.record("ussd", "ussd.failed", job.transactionId, "${result.category}: ${result.reason}")
            }
        }
    }

    private suspend fun failJob(job: com.mzazitech.automatedmachine.data.local.UssdJobEntity, reason: String) {
        jobDao.update(
            job.copy(
                status = UssdJobStatus.FAILED.name,
                lastError = reason,
                updatedAt = Time.nowMillis()
            )
        )
    }

    companion object {
        const val RETRY_BACKOFF_MS = 60_000L
    }
}
