package com.mzazitech.automatedmachine.domain.model

import com.mzazitech.automatedmachine.core.Money

/**
 * Payment state is decided by the backend after it verifies the M-Pesa callback.
 * The Android device never marks a transaction as paid by itself, and an SMS is
 * never treated as proof of payment.
 */
enum class PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    CANCELLED,
    REVERSED,
    REFUNDED;

    companion object {
        fun fromCode(code: String?): PaymentStatus =
            entries.firstOrNull { it.name.equals(code?.trim(), ignoreCase = true) } ?: PENDING
    }
}

/** Fulfilment state of the bundle delivery (the USSD execution). */
enum class PurchaseStatus {
    QUEUED,
    RUNNING,
    AWAITING_MANUAL_CONFIRMATION,
    SUCCESS,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean get() = this == SUCCESS || this == FAILED || this == CANCELLED

    companion object {
        fun fromCode(code: String?): PurchaseStatus =
            entries.firstOrNull { it.name.equals(code?.trim(), ignoreCase = true) } ?: QUEUED
    }
}

data class Transaction(
    val id: String,
    val reference: String,
    val customerMsisdn: String,
    val network: Network,
    val bundleId: String?,
    val bundleName: String,
    val bundleSizeMb: Int,
    val amount: Money,
    val paymentStatus: PaymentStatus,
    val purchaseStatus: PurchaseStatus,
    val paymentReference: String?,
    val mpesaReceipt: String?,
    val checkoutRequestId: String?,
    val failureReason: String?,
    val simSlot: Int?,
    val manualConfirmationRequired: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val idempotencyKey: String
) {
    val isSettled: Boolean get() = paymentStatus != PaymentStatus.PENDING
}
