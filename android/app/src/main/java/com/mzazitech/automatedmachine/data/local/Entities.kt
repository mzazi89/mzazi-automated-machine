package com.mzazitech.automatedmachine.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bundles")
data class BundleEntity(
    @PrimaryKey val id: String,
    val network: String,
    val name: String,
    val dataSizeMb: Int,
    val sellingPriceCents: Long,
    val costPriceCents: Long,
    val validityDays: Int,
    val workflowId: String?,
    val enabled: Boolean,
    val sortOrder: Int,
    val updatedAt: Long
)

@Entity(
    tableName = "transactions",
    indices = [
        // The idempotency key is what makes a double tap on "Buy" harmless.
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["createdAt"]),
        Index(value = ["paymentStatus"]),
        Index(value = ["purchaseStatus"])
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val reference: String,
    val customerMsisdn: String,
    val network: String,
    val bundleId: String?,
    val bundleName: String,
    val bundleSizeMb: Int,
    val amountCents: Long,
    val paymentStatus: String,
    val purchaseStatus: String,
    val paymentReference: String?,
    val mpesaReceipt: String?,
    val checkoutRequestId: String?,
    val failureReason: String?,
    val simSlot: Int?,
    val manualConfirmationRequired: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val idempotencyKey: String
)

@Entity(
    tableName = "ussd_jobs",
    indices = [
        Index(value = ["transactionId"], unique = true),
        Index(value = ["status"]),
        Index(value = ["nextAttemptAt"])
    ]
)
data class UssdJobEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val network: String,
    val workflowId: String?,
    val status: String,
    val attemptCount: Int,
    val maxAttempts: Int,
    val nextAttemptAt: Long,
    val lastError: String?,
    val simSlot: Int,
    val origin: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "ussd_workflows")
data class UssdWorkflowEntity(
    @PrimaryKey val id: String,
    val network: String,
    val name: String,
    val stepsJson: String,
    val successPattern: String?,
    val failurePattern: String?,
    val timeoutMs: Long,
    val enabled: Boolean,
    val verifiedBy: String?,
    val version: Int,
    val updatedAt: Long
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "audit_logs", indices = [Index(value = ["createdAt"])])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actor: String,
    val action: String,
    val target: String?,
    val detail: String?,
    val createdAt: Long
)
