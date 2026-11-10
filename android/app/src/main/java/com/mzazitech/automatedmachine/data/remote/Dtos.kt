package com.mzazitech.automatedmachine.data.remote

import com.mzazitech.automatedmachine.core.Money
import com.mzazitech.automatedmachine.domain.model.AdminUser
import com.mzazitech.automatedmachine.domain.model.Bundle
import com.mzazitech.automatedmachine.domain.model.Network
import com.mzazitech.automatedmachine.domain.model.PaymentStatus
import com.mzazitech.automatedmachine.domain.model.PurchaseStatus
import com.mzazitech.automatedmachine.domain.model.Transaction
import com.mzazitech.automatedmachine.domain.model.UssdJob
import com.mzazitech.automatedmachine.domain.model.UssdJobStatus
import com.mzazitech.automatedmachine.domain.model.UssdStep
import com.mzazitech.automatedmachine.domain.model.UssdStepLog
import com.mzazitech.automatedmachine.domain.model.UssdWorkflow
import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long?,
    val admin: AdminDto
) {
    fun toAdmin(): AdminUser = AdminUser(
        id = admin.id,
        email = admin.email,
        name = admin.name ?: admin.email,
        role = admin.role ?: "operator"
    )
}

data class AdminDto(
    val id: String,
    val email: String,
    val name: String?,
    val role: String?
)

data class BundleDto(
    val id: String,
    val network: String,
    val name: String,
    val dataSizeMb: Int,
    val sellingPriceCents: Long,
    val costPriceCents: Long = 0,
    val validityDays: Int = 30,
    val workflowId: String? = null,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0
) {
    fun toDomain(): Bundle = Bundle(
        id = id,
        network = Network.fromCode(network) ?: Network.SAFARICOM,
        name = name,
        dataSizeMb = dataSizeMb,
        sellingPrice = Money(sellingPriceCents),
        costPrice = Money(costPriceCents),
        validityDays = validityDays,
        workflowId = workflowId,
        enabled = enabled,
        sortOrder = sortOrder,
        updatedAt = updatedAt
    )

    companion object {
        fun from(bundle: Bundle): BundleDto = BundleDto(
            id = bundle.id,
            network = bundle.network.code,
            name = bundle.name,
            dataSizeMb = bundle.dataSizeMb,
            sellingPriceCents = bundle.sellingPrice.cents,
            costPriceCents = bundle.costPrice.cents,
            validityDays = bundle.validityDays,
            workflowId = bundle.workflowId,
            enabled = bundle.enabled,
            sortOrder = bundle.sortOrder,
            updatedAt = bundle.updatedAt
        )
    }
}

data class UssdStepDto(
    val index: Int,
    val label: String,
    val requestTemplate: String,
    val expectedPattern: String? = null,
    val sendAsSessionInput: Boolean = false,
    val timeoutMs: Long = 45_000
) {
    fun toDomain(): UssdStep = UssdStep(
        index = index,
        label = label,
        requestTemplate = requestTemplate,
        expectedPattern = expectedPattern,
        sendAsSessionInput = sendAsSessionInput,
        timeoutMs = timeoutMs
    )

    companion object {
        fun from(step: UssdStep) = UssdStepDto(
            index = step.index,
            label = step.label,
            requestTemplate = step.requestTemplate,
            expectedPattern = step.expectedPattern,
            sendAsSessionInput = step.sendAsSessionInput,
            timeoutMs = step.timeoutMs
        )
    }
}

data class WorkflowDto(
    val id: String,
    val network: String,
    val name: String,
    val steps: List<UssdStepDto> = emptyList(),
    val successPattern: String? = null,
    val failurePattern: String? = null,
    val timeoutMs: Long = 45_000,
    val enabled: Boolean = false,
    val verifiedBy: String? = null,
    val version: Int = 1,
    val updatedAt: Long = 0
) {
    fun toDomain(): UssdWorkflow = UssdWorkflow(
        id = id,
        network = Network.fromCode(network) ?: Network.SAFARICOM,
        name = name,
        steps = steps.map { it.toDomain() },
        successPattern = successPattern,
        failurePattern = failurePattern,
        timeoutMs = timeoutMs,
        enabled = enabled,
        verifiedBy = verifiedBy,
        version = version,
        updatedAt = updatedAt
    )
}

data class CreateTransactionRequest(
    val customerMsisdn: String,
    val network: String,
    val bundleId: String,
    val simSlot: Int = 0,
    val paymentMethod: String = "MPESA",
    /** Only honoured by the backend when it runs in TEST_MODE. */
    val testMode: Boolean = false
)

data class TransactionDto(
    val id: String,
    val reference: String,
    val customerMsisdn: String,
    val network: String,
    val bundleId: String?,
    val bundleName: String,
    val bundleSizeMb: Int,
    val amountCents: Long,
    val paymentStatus: String,
    val purchaseStatus: String,
    val paymentReference: String? = null,
    val mpesaReceipt: String? = null,
    val checkoutRequestId: String? = null,
    val failureReason: String? = null,
    val simSlot: Int? = null,
    val manualConfirmationRequired: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val idempotencyKey: String = ""
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        reference = reference,
        customerMsisdn = customerMsisdn,
        network = Network.fromCode(network) ?: Network.SAFARICOM,
        bundleId = bundleId,
        bundleName = bundleName,
        bundleSizeMb = bundleSizeMb,
        amount = Money(amountCents),
        paymentStatus = PaymentStatus.fromCode(paymentStatus),
        purchaseStatus = PurchaseStatus.fromCode(purchaseStatus),
        paymentReference = paymentReference,
        mpesaReceipt = mpesaReceipt,
        checkoutRequestId = checkoutRequestId,
        failureReason = failureReason,
        simSlot = simSlot,
        manualConfirmationRequired = manualConfirmationRequired,
        createdAt = createdAt,
        updatedAt = updatedAt,
        idempotencyKey = idempotencyKey
    )
}

data class DashboardSummaryDto(
    val totalSalesCents: Long = 0,
    val todayRevenueCents: Long = 0,
    val totalTransactions: Int = 0,
    val successfulTransactions: Int = 0,
    val failedTransactions: Int = 0,
    val pendingTransactions: Int = 0,
    val activeBundles: Int = 0,
    val lowFloatAlert: Boolean = false
)

data class UssdJobDto(
    val id: String,
    val transactionId: String,
    val network: String,
    val workflowId: String? = null,
    val status: String,
    val attemptCount: Int = 0,
    val maxAttempts: Int = 3,
    val simSlot: Int = 0,
    val customerMsisdn: String? = null,
    val bundleName: String? = null,
    val bundleSizeMb: Int = 0,
    val amountCents: Long = 0,
    val workflow: WorkflowDto? = null
) {
    fun toDomain(): UssdJob = UssdJob(
        id = id,
        transactionId = transactionId,
        network = Network.fromCode(network) ?: Network.SAFARICOM,
        workflowId = workflowId,
        status = UssdJobStatus.entries.firstOrNull { it.name == status } ?: UssdJobStatus.QUEUED,
        attemptCount = attemptCount,
        maxAttempts = maxAttempts,
        nextAttemptAt = 0,
        lastError = null,
        simSlot = simSlot,
        createdAt = 0,
        updatedAt = 0
    )
}

data class UssdStepLogDto(
    val index: Int,
    val label: String,
    val request: String,
    val response: String?,
    val outcome: String,
    val durationMs: Long
) {
    companion object {
        fun from(log: UssdStepLog) = UssdStepLogDto(
            index = log.index,
            label = log.label,
            request = log.request,
            response = log.response,
            outcome = log.outcome,
            durationMs = log.durationMs
        )
    }
}

data class JobResultRequest(
    val status: String,
    val steps: List<UssdStepLogDto> = emptyList(),
    val error: String? = null,
    val receiptText: String? = null,
    val failureCategory: String? = null,
    val retryable: Boolean = false,
    @SerializedName("manualFallback") val manualFallback: Boolean = false
)

data class ManualConfirmationRequest(
    val outcome: String,
    val note: String? = null,
    val carrierReference: String? = null
)

data class SimulatePaymentRequest(
    val transactionId: String,
    val outcome: String = "SUCCESS"
)

data class ApiErrorBody(
    val error: String? = null,
    val message: String? = null,
    val code: String? = null
)
