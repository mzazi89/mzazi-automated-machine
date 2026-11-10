package com.mzazitech.automatedmachine.domain.ussd

/**
 * Turns a raw USSD response string into an actionable classification.
 *
 * The administrator configures the authoritative success/failure regular expressions
 * per network. The menu heuristics below are only a secondary signal, because carriers
 * change wording without notice and a wrong guess could consume customer money.
 */
object UssdResponseClassifier {

    enum class Verdict { SUCCESS, FAILURE, MENU, UNKNOWN }

    data class Classification(
        val verdict: Verdict,
        val matchedPattern: String? = null,
        val requiresUserInput: Boolean = false
    )

    private val menuIndicators = listOf(
        "reply with",
        "reply ",
        "select option",
        "select an option",
        "choose",
        "enter ",
        "enter the",
        "invalid input",
        "1.",
        "1)",
        "2.",
        "2)",
        "0. back",
        "99. back"
    )

    private val successHints = listOf(
        "successfully",
        "successful",
        "has been activated",
        "you have bought",
        "you have purchased",
        "your request has been processed"
    )

    private val failureHints = listOf(
        "failed",
        "unsuccessful",
        "not successful",
        "insufficient",
        "invalid",
        "declined",
        "cannot be processed",
        "could not be processed",
        "not available",
        "error"
    )

    fun classify(
        rawResponse: String?,
        successPattern: String?,
        failurePattern: String?
    ): Classification {
        val text = rawResponse?.trim().orEmpty()
        if (text.isEmpty()) {
            return Classification(Verdict.UNKNOWN)
        }
        val normalized = text.lowercase()

        val configuredSuccess = matches(successPattern, text)
        val configuredFailure = matches(failurePattern, text)

        // A configured failure pattern wins over the success pattern: refusing a
        // delivery is always safer than double-delivering a bundle.
        if (configuredFailure) {
            return Classification(Verdict.FAILURE, failurePattern)
        }
        if (configuredSuccess) {
            return Classification(Verdict.SUCCESS, successPattern)
        }

        val looksLikeMenu = menuIndicators.any { normalized.contains(it) }
        if (looksLikeMenu) {
            return Classification(Verdict.MENU, requiresUserInput = true)
        }

        if (successHints.any { normalized.contains(it) }) {
            return Classification(Verdict.SUCCESS, "builtin-success-hint")
        }
        if (failureHints.any { normalized.contains(it) }) {
            return Classification(Verdict.FAILURE, "builtin-failure-hint")
        }

        return Classification(Verdict.UNKNOWN)
    }

    private fun matches(pattern: String?, text: String): Boolean {
        if (pattern.isNullOrBlank()) return false
        return runCatching { Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text) }
            .getOrDefault(false)
    }
}
