package com.mzazitech.automatedmachine.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BundleDao {
    @Query("SELECT * FROM bundles ORDER BY network ASC, sortOrder ASC, dataSizeMb ASC")
    fun observeAll(): Flow<List<BundleEntity>>

    @Query("SELECT * FROM bundles WHERE network = :network AND enabled = 1 ORDER BY sortOrder ASC, dataSizeMb ASC")
    fun observeByNetwork(network: String): Flow<List<BundleEntity>>

    @Query("SELECT * FROM bundles WHERE id = :id")
    suspend fun byId(id: String): BundleEntity?

    @Query("SELECT * FROM bundles")
    suspend fun all(): List<BundleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BundleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BundleEntity)

    @Query("DELETE FROM bundles WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE bundles SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: String): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE idempotencyKey = :key")
    suspend fun byIdempotencyKey(key: String): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE paymentStatus = 'PAID'
          AND purchaseStatus IN ('QUEUED','RUNNING')
          AND customerMsisdn = :msisdn
          AND bundleId = :bundleId
          AND createdAt > :since
        LIMIT 1
        """
    )
    suspend fun findActiveDelivery(msisdn: String, bundleId: String?, since: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: TransactionEntity)

    @Update
    suspend fun update(item: TransactionEntity)

    @Query(
        """
        UPDATE transactions
        SET purchaseStatus = :purchaseStatus, failureReason = :reason,
            manualConfirmationRequired = :manual, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updatePurchaseState(
        id: String,
        purchaseStatus: String,
        reason: String?,
        manual: Boolean,
        updatedAt: Long
    )

    @Query("SELECT COUNT(*) FROM transactions WHERE paymentStatus = 'PAID' AND createdAt >= :since")
    suspend fun countPaidSince(since: Long): Int

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE paymentStatus = 'PAID' AND createdAt >= :since")
    fun observeRevenueSince(since: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE paymentStatus = 'PAID' AND purchaseStatus = 'SUCCESS'")
    fun observeLifetimeSales(): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE purchaseStatus = :status")
    fun observeCountByPurchaseStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE paymentStatus = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeTotalCount(): Flow<Int>

    @Query("DELETE FROM transactions WHERE paymentStatus = 'PENDING' AND createdAt < :before")
    suspend fun purgeStalePending(before: Long)
}

@Dao
interface UssdJobDao {
    @Query("SELECT * FROM ussd_jobs WHERE id = :id")
    suspend fun byId(id: String): UssdJobEntity?

    @Query("SELECT * FROM ussd_jobs WHERE transactionId = :transactionId")
    suspend fun byTransaction(transactionId: String): UssdJobEntity?

    @Query(
        """
        SELECT * FROM ussd_jobs
        WHERE status IN ('QUEUED','AWAITING_MANUAL_CONFIRMATION')
          AND nextAttemptAt <= :now
        ORDER BY createdAt ASC
        LIMIT 1
        """
    )
    suspend fun nextRunnable(now: Long): UssdJobEntity?

    @Query("SELECT * FROM ussd_jobs WHERE status IN ('QUEUED','CLAIMED','RUNNING','AWAITING_MANUAL_CONFIRMATION') ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<UssdJobEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: UssdJobEntity)

    @Update
    suspend fun update(item: UssdJobEntity)

    @Query("SELECT COUNT(*) FROM ussd_jobs WHERE status IN ('QUEUED','CLAIMED','RUNNING','AWAITING_MANUAL_CONFIRMATION')")
    fun observeActiveCount(): Flow<Int>

    /**
     * Atomically moves a job from a runnable state to RUNNING for exactly one caller.
     * Returns 1 when this caller won the race and must execute the job.
     */
    @Query(
        """
        UPDATE ussd_jobs
        SET status = 'RUNNING', attemptCount = attemptCount + 1, updatedAt = :now
        WHERE id = :id AND status IN ('QUEUED','AWAITING_MANUAL_CONFIRMATION')
        """
    )
    suspend fun claim(id: String, now: Long): Int
}

@Dao
interface UssdWorkflowDao {
    @Query("SELECT * FROM ussd_workflows WHERE enabled = 1 AND network = :network ORDER BY updatedAt DESC")
    suspend fun enabledFor(network: String): List<UssdWorkflowEntity>

    @Query("SELECT * FROM ussd_workflows WHERE id = :id")
    suspend fun byId(id: String): UssdWorkflowEntity?

    @Query("SELECT * FROM ussd_workflows ORDER BY network ASC")
    fun observeAll(): Flow<List<UssdWorkflowEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: UssdWorkflowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<UssdWorkflowEntity>)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: SettingEntity)

    @Query("SELECT * FROM settings")
    suspend fun all(): List<SettingEntity>
}

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(item: AuditLogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>
}

@Dao
interface DashboardDao {
    data class Counts(
        val total: Int,
        val successful: Int,
        val failed: Int,
        val pending: Int
    )

    @Transaction
    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM transactions) AS total,
          (SELECT COUNT(*) FROM transactions WHERE purchaseStatus = 'SUCCESS') AS successful,
          (SELECT COUNT(*) FROM transactions WHERE purchaseStatus = 'FAILED') AS failed,
          (SELECT COUNT(*) FROM transactions WHERE paymentStatus = 'PENDING') AS pending
        """
    )
    fun observeCounts(): Flow<Counts>
}
