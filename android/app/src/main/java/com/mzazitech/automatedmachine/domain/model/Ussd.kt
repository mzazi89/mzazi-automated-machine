package com.mzazitech.automatedmachine.domain.model

/**
 * One step of an operator-verified USSD workflow.
 *
 * [requestTemplate] supports the placeholders {msisdn}, {msisdnLocal}, {amount},
 * {bundleSizeMb} and {bundleCode}. It must be supplied by an administrator from
 * documentation verified with the operator — MZAZI TECH never ships guessed codes.
 */
data class UssdStep(
    val index: Int,
    val label: String,
    val requestTemplate: String,
    val expectedPattern: String? = null,
    val sendAsSessionInput: Boolean = false,
    val timeoutMs: Long = 45_000L
)

data class UssdWorkflow(
    val id: String,
    val network: Network,
    val name: String,
    val steps: List<UssdStep>,
    val successPattern: String?,
    val failurePattern: String?,
    val timeoutMs: Long,
    val enabled: Boolean,
    val verifiedBy: String?,
    val version: Int,
    val updatedAt: Long
) {
    val requiresInteractiveSession: Boolean
        get() = steps.any { it.sendAsSessionInput } || steps.size > 1

    fun validate(): List<String> {
        val problems = mutableListOf<String>()
        if (steps.isEmpty()) problems += "Workflow has no steps."
        if (successPattern.isNullOrBlank()) problems += "Success pattern is required to classify a receipt."
        steps.forEach { step ->
            val rendered = UssdTemplate.validate(step.requestTemplate)
            if (rendered != null) problems += "Step ${step.index + 1}: $rendered"
            if (step.timeoutMs < 5_000) problems += "Step ${step.index + 1}: timeout must be at least 5 seconds."
        }
        if (verifiedBy.isNullOrBlank()) {
            problems += "Workflow must record who verified the operator codes."
        }
        return problems
    }
}

/** Renders and sanity-checks administrator supplied USSD templates. */
object UssdTemplate {

    private val allowedPlaceholders = setOf("msisdn", "msisdnLocal", "amount", "bundleSizeMb", "bundleCode")
    private val placeholderRegex = Regex("\\{([a-zA-Z0-9_]+)}")
    private val dialableRegex = Regex("^[0-9*#+]{4,64}$")

    /** Returns an error string when the template is unusable, or null when it is fine. */
    fun validate(template: String?): String? {
        if (template.isNullOrBlank()) return "USSD template is empty."
        val trimmed = template.trim()

        placeholderRegex.findAll(trimmed).forEach { match ->
            val name = match.groupValues[1]
            if (name !in allowedPlaceholders) return "Unknown placeholder {$name}."
        }

        // Validate the shape of the request with placeholders substituted, because the
        // braces themselves are not dialable characters.
        val skeleton = placeholderRegex.replace(trimmed, "0")
        if (!dialableRegex.matches(skeleton)) {
            return "Only digits, *, # and + are allowed in a USSD template."
        }
        if (!skeleton.startsWith("*") && !skeleton.startsWith("#")) {
            return "A USSD request must start with * or #."
        }
        if (!skeleton.contains("#")) {
            return "A USSD request must be terminated with #."
        }
        return null
    }

    fun render(template: String, values: Map<String, String>): String =
        placeholderRegex.replace(template.trim()) { match ->
            values[match.groupValues[1]]?.let { sanitize(it) } ?: match.value
        }

    /** Strips anything that could break out of a USSD string. */
    fun sanitize(value: String): String = value.filter { it.isDigit() || it == '*' || it == '#' }
}

data class UssdRunParams(
    val msisdn: String,
    val msisdnLocal: String,
    val amountShillings: Long,
    val bundleSizeMb: Int,
    val bundleCode: String?,
    val simSubscriptionId: Int?,
    val simSlot: Int
) {
    fun asMap(): Map<String, String> = buildMap {
        put("msisdn", msisdn)
        put("msisdnLocal", msisdnLocal)
        put("amount", amountShillings.toString())
        put("bundleSizeMb", bundleSizeMb.toString())
        bundleCode?.let { put("bundleCode", it) }
    }
}

enum class UssdFailureCategory {
    NO_SIM,
    SIM_UNAVAILABLE,
    PERMISSION_DENIED,
    INVALID_REQUEST,
    CARRIER_REJECTED,
    TIMEOUT,
    INTERACTIVE_UNSUPPORTED,
    NETWORK_ERROR,
    CANCELLED,
    UNKNOWN
}

data class UssdStepLog(
    val index: Int,
    val label: String,
    val request: String,
    val response: String?,
    val outcome: String,
    val durationMs: Long
)

sealed interface UssdRunResult {
    data class Success(val response: String, val steps: List<UssdStepLog>) : UssdRunResult
    data class Failed(
        val reason: String,
        val category: UssdFailureCategory,
        val retryable: Boolean,
        val steps: List<UssdStepLog>
    ) : UssdRunResult
    data class NeedsManualConfirmation(
        val reason: String,
        val instruction: String,
        val dialCode: String?,
        val steps: List<UssdStepLog>
    ) : UssdRunResult
}

enum class UssdJobStatus {
    QUEUED,
    CLAIMED,
    RUNNING,
    AWAITING_MANUAL_CONFIRMATION,
    SUCCESS,
    FAILED,
    CANCELLED,
    DEAD_LETTER;

    val isTerminal: Boolean
        get() = this == SUCCESS || this == FAILED || this == CANCELLED || this == DEAD_LETTER
}

data class UssdJob(
    val id: String,
    val transactionId: String,
    val network: Network,
    val workflowId: String?,
    val status: UssdJobStatus,
    val attemptCount: Int,
    val maxAttempts: Int,
    val nextAttemptAt: Long,
    val lastError: String?,
    val simSlot: Int,
    val createdAt: Long,
    val updatedAt: Long
)
