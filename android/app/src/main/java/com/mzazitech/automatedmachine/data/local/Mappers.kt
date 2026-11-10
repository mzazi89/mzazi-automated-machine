package com.mzazitech.automatedmachine.data.local

import com.mzazitech.automatedmachine.core.Money
import com.mzazitech.automatedmachine.domain.model.Bundle
import com.mzazitech.automatedmachine.domain.model.Network
import com.mzazitech.automatedmachine.domain.model.PaymentStatus
import com.mzazitech.automatedmachine.domain.model.PurchaseStatus
import com.mzazitech.automatedmachine.domain.model.Transaction
import com.mzazitech.automatedmachine.domain.model.UssdJob
import com.mzazitech.automatedmachine.domain.model.UssdJobStatus
import com.mzazitech.automatedmachine.domain.model.UssdStep
import com.mzazitech.automatedmachine.domain.model.UssdWorkflow
import org.json.JSONArray
import org.json.JSONObject

fun BundleEntity.toDomain(): Bundle = Bundle(
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

fun Bundle.toEntity(): BundleEntity = BundleEntity(
    id = id,
    network = network.code,
    name = name,
    dataSizeMb = dataSizeMb,
    sellingPriceCents = sellingPrice.cents,
    costPriceCents = costPrice.cents,
    validityDays = validityDays,
    workflowId = workflowId,
    enabled = enabled,
    sortOrder = sortOrder,
    updatedAt = updatedAt
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
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

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    reference = reference,
    customerMsisdn = customerMsisdn,
    network = network.code,
    bundleId = bundleId,
    bundleName = bundleName,
    bundleSizeMb = bundleSizeMb,
    amountCents = amount.cents,
    paymentStatus = paymentStatus.name,
    purchaseStatus = purchaseStatus.name,
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

fun UssdJobEntity.toDomain(): UssdJob = UssdJob(
    id = id,
    transactionId = transactionId,
    network = Network.fromCode(network) ?: Network.SAFARICOM,
    workflowId = workflowId,
    status = UssdJobStatus.entries.firstOrNull { it.name == status } ?: UssdJobStatus.QUEUED,
    attemptCount = attemptCount,
    maxAttempts = maxAttempts,
    nextAttemptAt = nextAttemptAt,
    lastError = lastError,
    simSlot = simSlot,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun UssdStep.toJson(): JSONObject = JSONObject().apply {
    put("index", index)
    put("label", label)
    put("requestTemplate", requestTemplate)
    put("expectedPattern", expectedPattern ?: JSONObject.NULL)
    put("sendAsSessionInput", sendAsSessionInput)
    put("timeoutMs", timeoutMs)
}

fun JSONObject.toUssdStep(): UssdStep = UssdStep(
    index = optInt("index", 0),
    label = optString("label", "Step"),
    requestTemplate = optString("requestTemplate", ""),
    expectedPattern = if (isNull("expectedPattern")) null else optString("expectedPattern"),
    sendAsSessionInput = optBoolean("sendAsSessionInput", false),
    timeoutMs = optLong("timeoutMs", 45_000L)
)

fun List<UssdStep>.toStepsJson(): String {
    val array = JSONArray()
    forEach { array.put(it.toJson()) }
    return array.toString()
}

fun String.toSteps(): List<UssdStep> = runCatching {
    val array = JSONArray(this)
    (0 until array.length()).map { array.getJSONObject(it).toUssdStep() }
}.getOrDefault(emptyList())

fun UssdWorkflowEntity.toDomain(): UssdWorkflow = UssdWorkflow(
    id = id,
    network = Network.fromCode(network) ?: Network.SAFARICOM,
    name = name,
    steps = stepsJson.toSteps(),
    successPattern = successPattern,
    failurePattern = failurePattern,
    timeoutMs = timeoutMs,
    enabled = enabled,
    verifiedBy = verifiedBy,
    version = version,
    updatedAt = updatedAt
)

fun UssdWorkflow.toEntity(): UssdWorkflowEntity = UssdWorkflowEntity(
    id = id,
    network = network.code,
    name = name,
    stepsJson = steps.toStepsJson(),
    successPattern = successPattern,
    failurePattern = failurePattern,
    timeoutMs = timeoutMs,
    enabled = enabled,
    verifiedBy = verifiedBy,
    version = version,
    updatedAt = updatedAt
)
