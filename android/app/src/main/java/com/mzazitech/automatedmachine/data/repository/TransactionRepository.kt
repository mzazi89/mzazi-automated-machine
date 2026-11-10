package com.mzazitech.automatedmachine.data.repository

import com.mzazitech.automatedmachine.core.Money
import com.mzazitech.automatedmachine.core.Msisdn
import com.mzazitech.automatedmachine.core.Time
import com.mzazitech.automatedmachine.data.local.TransactionDao
import com.mzazitech.automatedmachine.data.local.UssdJobDao
import com.mzazitech.automatedmachine.data.local.toDomain
import com.mzazitech.automatedmachine.data.local.toEntity
import com.mzazitech.automatedmachine.data.remote.ApiErrorMapper
import com.mzazitech.automatedmachine.data.remote.CreateTransactionRequest
import com.mzazitech.automatedmachine.data.remote.MzaziApi
import com.mzazitech.automatedmachine.data.remote.SimulatePaymentRequest
import com.mzazitech.automatedmachine.data.session.SessionManager
import com.mzazitech.automatedmachine.domain.model.Bundle
import com.mzazitech.automatedmachine.domain.model.PaymentStatus
import com.mzazitech.automatedmachine.domain.model.PurchaseStatus
import com.mzazitech.automatedmachine.domain.model.Transaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

sealed interface PurchaseResult {
    data class Created(val transaction: Transaction, val online: Boolean) : PurchaseResult
    data class Duplicate(val existing: Transaction) : PurchaseResult
    data class Invalid(val message: String) : PurchaseResult
    data class Error(val message: String) : PurchaseResult
}

/**
 * Creates and tracks customer purchases.
 *
 * Money rules:
 *  - the client never decides that a payment succeeded;
 *  - a pending duplicate within [DUPLICATE_WINDOW_MS] is refused before any request
 *    leaves the device, and the backend enforces the same rule again;
 *  - the unique idempotency key makes a retried HTTP call return the original record.
 */
class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val jobDao: UssdJobDao,
    private val api: MzaziApi,
    private val session: SessionManager,
    private val settings: SettingsRepository
) {

    fun observeRecent(limit: Int = 8): Flow<List<Transaction>> =
        transactionDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeAll(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeById(id: String): Flow<Transaction?> =
        transactionDao.observeById(id).map { it?.toDomain() }

    suspend fun byId(id: String): Transaction? = transactionDao.byId(id)?.toDomain()

    suspend fun refresh(id: String): Transaction? {
        if (!session.isLoggedIn()) return byId(id)
        return runCatching {
            val dto = api.transaction(id)
            val domain = dto.toDomain()
            transactionDao.byId(id)?.let { existing ->
                transactionDao.update(domain.toEntity().copy(createdAt = existing.createdAt))
            } ?: transactionDao.insert(domain.toEntity())
            domain
        }.getOrNull()
    }

    suspend fun createPurchase(
        bundle: Bundle,
        rawMsisdn: String,
        simSlot: Int
    ): PurchaseResult {
        val msisdn = Msisdn.normalize(rawMsisdn)
            ?: return PurchaseResult.Invalid("Enter a valid Kenyan mobile number, e.g. 0712 345 678.")

        if (!bundle.enabled) {
            return PurchaseResult.Invalid("${bundle.name} is currently disabled.")
        }

        val now = Time.nowMillis()
        val duplicate = transactionDao.findActiveDelivery(msisdn, bundle.id, now - DUPLICATE_WINDOW_MS)
        if (duplicate != null) {
            return PurchaseResult.Duplicate(duplicate.toDomain())
        }

        val testMode = settings.isTestMode()
        val online = session.isLoggedIn()
        val idempotencyKey = UUID.randomUUID().toString()
        val reference = "MZ" + now.toString().takeLast(9)

        if (online) {
            return runCatching {
                val dto = api.createTransaction(
                    idempotencyKey,
                    CreateTransactionRequest(
                        customerMsisdn = msisdn,
                        network = bundle.network.code,
                        bundleId = bundle.id,
                        simSlot = simSlot,
                        testMode = testMode
                    )
                )
                val domain = dto.toDomain()
                val existing = transactionDao.byId(domain.id)
                if (existing == null) {
                    transactionDao.insert(domain.toEntity())
                } else {
                    transactionDao.update(domain.toEntity().copy(createdAt = existing.createdAt))
                }
                if (testMode && domain.paymentStatus == PaymentStatus.PENDING) {
                    runCatching { api.simulatePayment(SimulatePaymentRequest(domain.id)) }
                }
                PurchaseResult.Created(domain, online = true)
            }.getOrElse { throwable ->
                val error = ApiErrorMapper.from(throwable)
                if (error.statusCode == 409) {
                    PurchaseResult.Invalid("A purchase for this number is already in progress.")
                } else {
                    PurchaseResult.Error(error.message)
                }
            }
        }

        if (!testMode) {
            return PurchaseResult.Error(
                "Sign in to the MZAZI backend to take real payments, or enable test mode in Settings."
            )
        }

        // Local test mode: simulated settlement, never a real payment.
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            reference = reference,
            customerMsisdn = msisdn,
            network = bundle.network,
            bundleId = bundle.id,
            bundleName = bundle.name,
            bundleSizeMb = bundle.dataSizeMb,
            amount = Money(bundle.sellingPrice.cents),
            paymentStatus = PaymentStatus.PAID,
            purchaseStatus = PurchaseStatus.QUEUED,
            paymentReference = "TEST-$reference",
            mpesaReceipt = null,
            checkoutRequestId = null,
            failureReason = null,
            simSlot = simSlot,
            manualConfirmationRequired = false,
            createdAt = now,
            updatedAt = now,
            idempotencyKey = idempotencyKey
        )

        return runCatching {
            transactionDao.insert(transaction.toEntity())
            enqueueLocalJob(transaction, bundle.workflowId)
            PurchaseResult.Created(transaction, online = false)
        }.getOrElse { throwable ->
            if (throwable is android.database.sqlite.SQLiteConstraintException) {
                PurchaseResult.Invalid("This purchase was already recorded. Check transaction history.")
            } else {
                PurchaseResult.Error(throwable.message ?: "Could not save the purchase.")
            }
        }
    }

    /** Polls the backend until the payment settles or the deadline passes. */
    suspend fun awaitPayment(transactionId: String, timeoutMs: Long = 180_000): Transaction? {
        if (!session.isLoggedIn()) return byId(transactionId)
        val deadline = Time.nowMillis() + timeoutMs
        var current = byId(transactionId)
        while (Time.nowMillis() < deadline) {
            current = refresh(transactionId) ?: current
            when (current?.paymentStatus) {
                PaymentStatus.PAID, PaymentStatus.FAILED, PaymentStatus.CANCELLED,
                PaymentStatus.REVERSED, PaymentStatus.REFUNDED -> return current
                else -> delay(3_000)
            }
        }
        return current
    }

    suspend fun markManualOutcome(transactionId: String, delivered: Boolean, note: String?) {
        val now = Time.nowMillis()
        if (transactionDao.byId(transactionId) == null) return

        transactionDao.updatePurchaseState(
            id = transactionId,
            purchaseStatus = if (delivered) PurchaseStatus.SUCCESS.name else PurchaseStatus.FAILED.name,
            reason = note,
            manual = true,
            updatedAt = now
        )

        val job = jobDao.byTransaction(transactionId)
        if (job != null) {
            jobDao.update(
                job.copy(
                    status = if (delivered) "SUCCESS" else "FAILED",
                    lastError = note,
                    updatedAt = now
                )
            )
        }
    }

    private suspend fun enqueueLocalJob(transaction: Transaction, workflowId: String?) {
        val now = Time.nowMillis()
        val job = com.mzazitech.automatedmachine.data.local.UssdJobEntity(
            id = UUID.randomUUID().toString(),
            transactionId = transaction.id,
            network = transaction.network.code,
            workflowId = workflowId,
            status = "QUEUED",
            attemptCount = 0,
            maxAttempts = MAX_ATTEMPTS,
            nextAttemptAt = now,
            lastError = null,
            simSlot = transaction.simSlot ?: 0,
            origin = "LOCAL",
            createdAt = now,
            updatedAt = now
        )
        runCatching { jobDao.insert(job) }
    }

    companion object {
        /** Refuses a second active purchase for the same number and bundle. */
        const val DUPLICATE_WINDOW_MS = 15 * 60 * 1000L
        const val MAX_ATTEMPTS = 3
    }
}
