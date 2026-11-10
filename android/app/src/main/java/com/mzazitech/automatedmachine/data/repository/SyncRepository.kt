package com.mzazitech.automatedmachine.data.repository

import com.mzazitech.automatedmachine.data.remote.ApiErrorMapper
import com.mzazitech.automatedmachine.data.remote.BundleDto
import com.mzazitech.automatedmachine.data.remote.MzaziApi
import com.mzazitech.automatedmachine.data.session.SessionManager
import com.mzazitech.automatedmachine.domain.model.Bundle
import com.mzazitech.automatedmachine.domain.model.UssdWorkflow
import com.mzazitech.automatedmachine.data.local.toDomain

/**
 * Pull/push of the catalogue and operator workflows.
 * Offline is a first-class state: a failed sync keeps the local copy and reports it.
 */
class SyncRepository(
    private val api: MzaziApi,
    private val session: SessionManager,
    private val bundles: BundleRepository,
    private val workflows: WorkflowRepository,
    private val audit: AuditRepository
) {

    data class SyncReport(
        val bundles: Int = 0,
        val workflows: Int = 0,
        val error: String? = null
    ) {
        val succeeded: Boolean get() = error == null
    }

    suspend fun pullCatalogue(): SyncReport {
        if (!session.isLoggedIn()) {
            return SyncReport(error = "Not signed in — showing the local catalogue.")
        }
        return runCatching {
            val remoteBundles = api.bundles()
            if (remoteBundles.isNotEmpty()) {
                bundles.replaceAll(remoteBundles.map(BundleDto::toDomain))
            }
            val remoteWorkflows = api.workflows()
            remoteWorkflows.forEach { dto ->
                workflows.save(dto.toDomain())
            }
            SyncReport(bundles = remoteBundles.size, workflows = remoteWorkflows.size)
        }.getOrElse { throwable ->
            SyncReport(error = ApiErrorMapper.from(throwable).message)
        }
    }

    suspend fun pushBundle(bundle: Bundle): Result<Bundle> {
        if (!session.isLoggedIn()) return Result.success(bundle)
        return runCatching {
            val dto = api.createBundle(BundleDto.from(bundle))
            dto.toDomain()
        }.onSuccess { remote ->
            bundles.upsert(remote)
            audit.record(session.session.value?.email ?: "admin", "bundle.create", remote.id, remote.name)
        }
    }

    suspend fun pushWorkflow(workflow: UssdWorkflow): Result<UssdWorkflow> {
        if (!session.isLoggedIn()) return Result.success(workflow)
        return runCatching {
            val dto = api.createWorkflow(
                com.mzazitech.automatedmachine.data.remote.WorkflowDto(
                    id = workflow.id,
                    network = workflow.network.code,
                    name = workflow.name,
                    steps = workflow.steps.map {
                        com.mzazitech.automatedmachine.data.remote.UssdStepDto.from(it)
                    },
                    successPattern = workflow.successPattern,
                    failurePattern = workflow.failurePattern,
                    timeoutMs = workflow.timeoutMs,
                    enabled = workflow.enabled,
                    verifiedBy = workflow.verifiedBy,
                    version = workflow.version,
                    updatedAt = workflow.updatedAt
                )
            )
            dto.toDomain()
        }.onSuccess { saved ->
            workflows.save(saved)
            audit.record(
                session.session.value?.email ?: "admin",
                "workflow.save",
                saved.id,
                "${saved.network.code} v${saved.version}"
            )
        }
    }
}
