package com.mzazitech.automatedmachine.data.repository

import com.mzazitech.automatedmachine.core.Time
import com.mzazitech.automatedmachine.data.local.AuditLogDao
import com.mzazitech.automatedmachine.data.local.AuditLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Local audit trail. Every privileged action (login, bundle edit, USSD execution,
 * manual override) is appended here and mirrored to the backend when online.
 * Never contains PINs or API secrets.
 */
class AuditRepository(private val dao: AuditLogDao) {

    fun observeRecent(limit: Int = 100): Flow<List<AuditLogEntity>> = dao.observeRecent(limit)

    suspend fun record(actor: String, action: String, target: String? = null, detail: String? = null) {
        dao.insert(
            AuditLogEntity(
                actor = actor.take(80),
                action = action.take(80),
                target = target?.take(120),
                detail = detail?.take(500),
                createdAt = Time.nowMillis()
            )
        )
    }
}
