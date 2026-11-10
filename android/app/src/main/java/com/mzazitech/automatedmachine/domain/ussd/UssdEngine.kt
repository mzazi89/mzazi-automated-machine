package com.mzazitech.automatedmachine.domain.ussd

import com.mzazitech.automatedmachine.domain.model.UssdFailureCategory
import com.mzazitech.automatedmachine.domain.model.UssdRunParams
import com.mzazitech.automatedmachine.domain.model.UssdRunResult
import com.mzazitech.automatedmachine.domain.model.UssdStep
import com.mzazitech.automatedmachine.domain.model.UssdStepLog
import com.mzazitech.automatedmachine.domain.model.UssdTemplate
import com.mzazitech.automatedmachine.domain.model.UssdWorkflow
import kotlinx.coroutines.delay

data class UssdEngineConfig(
    /** Attempts per step after the first one. Zero keeps duplicate purchases impossible. */
    val stepRetries: Int = 0,
    val retryDelayMs: Long = 2_500L,
    val interStepDelayMs: Long = 1_200L
)

/**
 * Executes an operator-verified USSD workflow.
 *
 * Safety rules baked in here:
 *  - nothing runs without a workflow that passed validation;
 *  - nothing runs without an available SIM for the requested slot;
 *  - a step is never re-sent after it may have reached the carrier, because a USSD
 *    purchase is not idempotent on the carrier side (stepRetries defaults to 0);
 *  - when the carrier returns a menu, the engine stops and asks the operator to
 *    confirm manually instead of firing blind menu digits at a live session.
 */
class UssdEngine(
    private val executor: UssdExecutor,
    private val config: UssdEngineConfig = UssdEngineConfig(),
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) }
) {

    suspend fun run(
        workflow: UssdWorkflow,
        params: UssdRunParams,
        onProgress: suspend (String) -> Unit = {}
    ): UssdRunResult {
        val problems = workflow.validate()
        if (problems.isNotEmpty()) {
            return UssdRunResult.Failed(
                reason = problems.joinToString(" "),
                category = UssdFailureCategory.INVALID_REQUEST,
                retryable = false,
                steps = emptyList()
            )
        }

        // A rehearsal workflow must never reach a real carrier.
        if (workflow.verifiedBy == TEST_WORKFLOW_MARKER && !executor.isSimulation()) {
            return UssdRunResult.Failed(
                reason = "This is a simulation-only workflow. Configure an operator-verified " +
                    "workflow before executing on a real SIM.",
                category = UssdFailureCategory.INVALID_REQUEST,
                retryable = false,
                steps = emptyList()
            )
        }

        val sims = executor.listSims()
        if (sims.isEmpty()) {
            return UssdRunResult.Failed(
                reason = "No SIM card detected on this device.",
                category = UssdFailureCategory.NO_SIM,
                retryable = false,
                steps = emptyList()
            )
        }
        val sim = sims.firstOrNull { it.slotIndex == params.simSlot }
            ?: sims.firstOrNull { it.subscriptionId != null && it.subscriptionId == params.simSubscriptionId }
            ?: return UssdRunResult.Failed(
                reason = "SIM ${params.simSlot + 1} is not available on this device.",
                category = UssdFailureCategory.SIM_UNAVAILABLE,
                retryable = false,
                steps = emptyList()
            )

        val logs = mutableListOf<UssdStepLog>()
        val values = params.asMap()
        val primaryCode = UssdTemplate.render(workflow.steps.first().requestTemplate, values)

        workflow.steps.forEachIndexed { position, step ->
            val request = UssdTemplate.render(step.requestTemplate, values)
            val templateProblem = UssdTemplate.validate(request)
            if (templateProblem != null) {
                return UssdRunResult.Failed(
                    reason = "Step ${position + 1}: $templateProblem",
                    category = UssdFailureCategory.INVALID_REQUEST,
                    retryable = false,
                    steps = logs
                )
            }

            if (position > 0 && step.sendAsSessionInput) {
                // Android's public API cannot type into a live USSD session.
                logs += UssdStepLog(
                    index = position,
                    label = step.label,
                    request = maskCode(request),
                    response = null,
                    outcome = "INTERACTIVE_SESSION_UNSUPPORTED",
                    durationMs = 0
                )
                return UssdRunResult.NeedsManualConfirmation(
                    reason = "This workflow needs a live interactive USSD session, which Android does " +
                        "not expose to applications.",
                    instruction = "Dial the code on the device, complete the menu, then record the result.",
                    dialCode = primaryCode,
                    steps = logs
                )
            }

            onProgress("Sending step ${position + 1}/${workflow.steps.size}: ${step.label}")
            val startedAt = now()
            val outcome = sendWithRetry(request, step, sim.subscriptionId, params.simSlot)
            val duration = now() - startedAt

            when (outcome) {
                is UssdOutcome.Failure -> {
                    logs += UssdStepLog(
                        index = position,
                        label = step.label,
                        request = maskCode(request),
                        response = null,
                        outcome = outcome.category.name,
                        durationMs = duration
                    )
                    return UssdRunResult.Failed(
                        reason = outcome.message,
                        category = outcome.category,
                        retryable = outcome.retryable,
                        steps = logs
                    )
                }

                UssdOutcome.Cancelled -> {
                    logs += UssdStepLog(
                        index = position,
                        label = step.label,
                        request = maskCode(request),
                        response = null,
                        outcome = "CANCELLED",
                        durationMs = duration
                    )
                    return UssdRunResult.Failed(
                        reason = "The USSD session was cancelled on the device.",
                        category = UssdFailureCategory.CANCELLED,
                        retryable = false,
                        steps = logs
                    )
                }

                is UssdOutcome.Response -> {
                    val classification = UssdResponseClassifier.classify(
                        outcome.text,
                        workflow.successPattern,
                        workflow.failurePattern
                    )
                    logs += UssdStepLog(
                        index = position,
                        label = step.label,
                        request = maskCode(request),
                        response = outcome.text,
                        outcome = classification.verdict.name,
                        durationMs = duration
                    )

                    when (classification.verdict) {
                        UssdResponseClassifier.Verdict.FAILURE -> {
                            return UssdRunResult.Failed(
                                reason = outcome.text.ifBlank { "The carrier rejected the request." },
                                category = UssdFailureCategory.CARRIER_REJECTED,
                                // A rejection is a definite answer; never retry it.
                                retryable = false,
                                steps = logs
                            )
                        }

                        UssdResponseClassifier.Verdict.SUCCESS -> {
                            val isLast = position == workflow.steps.lastIndex
                            if (isLast) {
                                return UssdRunResult.Success(outcome.text, logs)
                            }
                            onProgress("Step ${position + 1} confirmed. Continuing.")
                            sleep(config.interStepDelayMs)
                        }

                        UssdResponseClassifier.Verdict.MENU -> {
                            return UssdRunResult.NeedsManualConfirmation(
                                reason = "The carrier returned a menu that requires a human response.",
                                instruction = "Reply to the USSD prompt on the device, then record the result.",
                                dialCode = primaryCode,
                                steps = logs
                            )
                        }

                        UssdResponseClassifier.Verdict.UNKNOWN -> {
                            return UssdRunResult.NeedsManualConfirmation(
                                reason = "The carrier response could not be classified with certainty.",
                                instruction = "Check the SMS receipt or dial *100# equivalent balance check, " +
                                    "then record the outcome. Do not resend until confirmed.",
                                dialCode = primaryCode,
                                steps = logs
                            )
                        }
                    }
                }
            }
        }

        // Every step completed without an explicit receipt — never assume delivery.
        return UssdRunResult.NeedsManualConfirmation(
            reason = "The workflow finished without a definitive receipt.",
            instruction = "Verify delivery on the operator side, then record the outcome.",
            dialCode = primaryCode,
            steps = logs
        )
    }

    private suspend fun sendWithRetry(
        request: String,
        step: UssdStep,
        subscriptionId: Int?,
        simSlot: Int
    ): UssdOutcome {
        var attempt = 0
        var last: UssdOutcome = UssdOutcome.Failure(
            "USSD request was not sent.",
            UssdFailureCategory.UNKNOWN
        )
        while (attempt <= config.stepRetries) {
            last = executor.send(
                UssdRequest(
                    ussd = request,
                    subscriptionId = subscriptionId,
                    simSlot = simSlot,
                    timeoutMs = step.timeoutMs
                )
            )
            val retryable = when (last) {
                is UssdOutcome.Failure ->
                    last.retryable && last.category == UssdFailureCategory.NETWORK_ERROR
                else -> false
            }
            if (!retryable || attempt == config.stepRetries) return last
            attempt++
            sleep(config.retryDelayMs * attempt)
        }
        return last
    }

    /** Keeps the customer number readable in logs while still useful for support. */
    private fun maskCode(code: String): String {
        if (code.length < 6) return code
        return code.replaceRange(1, minOf(4, code.length), "***")
    }

    companion object {
        const val TEST_WORKFLOW_MARKER = "MZAZI-TEST-ONLY"
    }
}
