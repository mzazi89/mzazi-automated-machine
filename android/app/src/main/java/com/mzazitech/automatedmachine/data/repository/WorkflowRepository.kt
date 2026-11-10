package com.mzazitech.automatedmachine.data.repository

import com.mzazitech.automatedmachine.core.Time
import com.mzazitech.automatedmachine.data.local.UssdWorkflowDao
import com.mzazitech.automatedmachine.data.local.toDomain
import com.mzazitech.automatedmachine.data.local.toEntity
import com.mzazitech.automatedmachine.domain.model.Network
import com.mzazitech.automatedmachine.domain.model.UssdStep
import com.mzazitech.automatedmachine.domain.model.UssdWorkflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Owns operator workflows. A workflow is only ever created from administrator input;
 * the app never guesses an operator code.
 */
class WorkflowRepository(private val dao: UssdWorkflowDao) {

    fun observeAll(): Flow<List<UssdWorkflow>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun forNetwork(network: Network, workflowId: String?): UssdWorkflow? {
        if (workflowId != null) {
            dao.byId(workflowId)?.let { return it.toDomain() }
        }
        return dao.enabledFor(network.code)
            .map { it.toDomain() }
            .firstOrNull { it.validate().isEmpty() }
    }

    suspend fun byId(id: String): UssdWorkflow? = dao.byId(id)?.toDomain()

    suspend fun save(workflow: UssdWorkflow) = dao.upsert(workflow.toEntity())

    suspend fun create(
        network: Network,
        name: String,
        steps: List<UssdStep>,
        successPattern: String?,
        failurePattern: String?,
        verifiedBy: String?
    ): UssdWorkflow {
        val workflow = UssdWorkflow(
            id = UUID.randomUUID().toString(),
            network = network,
            name = name.trim(),
            steps = steps,
            successPattern = successPattern,
            failurePattern = failurePattern,
            timeoutMs = steps.maxOfOrNull { it.timeoutMs } ?: 45_000,
            enabled = !verifiedBy.isNullOrBlank(),
            verifiedBy = verifiedBy,
            version = 1,
            updatedAt = Time.nowMillis()
        )
        dao.upsert(workflow.toEntity())
        return workflow
    }

    /**
     * Installs, once, one simulation-only workflow per network plus the placeholder
     * rows an administrator must replace with operator-verified codes.
     */
    suspend fun ensureDefaults() {
        Network.entries.forEach { network ->
            val existing = dao.enabledFor(network.code)
            if (existing.isNotEmpty()) return@forEach

            dao.upsert(simulationTemplate(network).toEntity())
            dao.upsert(productionPlaceholder(network).toEntity())
        }
    }

    private fun simulationTemplate(network: Network): UssdWorkflow = UssdWorkflow(
        id = "test-${network.code.lowercase()}",
        network = network,
        name = "${network.shortName} test workflow (simulation only)",
        steps = listOf(
            UssdStep(
                index = 0,
                label = "Simulated purchase request",
                requestTemplate = "*000*{bundleSizeMb}*{amount}#",
                expectedPattern = "(?i)simulated carrier receipt",
                timeoutMs = 30_000
            )
        ),
        successPattern = "(?i)simulated carrier receipt",
        failurePattern = "(?i)(failed|unsuccessful|invalid|insufficient|declined|unavailable)",
        timeoutMs = 30_000,
        enabled = true,
        verifiedBy = TEST_MARKER,
        version = 1,
        updatedAt = Time.nowMillis()
    )

    private fun productionPlaceholder(network: Network): UssdWorkflow = UssdWorkflow(
        id = "placeholder-${network.code.lowercase()}",
        network = network,
        name = "${network.shortName} live workflow (add verified codes)",
        steps = emptyList(),
        successPattern = "(?i)(successful|has been activated|you have purchased)",
        failurePattern = "(?i)(failed|unsuccessful|insufficient|declined)",
        timeoutMs = 45_000,
        enabled = false,
        verifiedBy = null,
        version = 1,
        updatedAt = Time.nowMillis()
    )

    companion object {
        /**
         * Marks workflows that exist only for rehearsals. The engine refuses to run
         * these on a real SIM, so a demo code can never reach a carrier.
         */
        const val TEST_MARKER = "MZAZI-TEST-ONLY"
    }
}
