package com.mzazitech.automatedmachine.domain.model

/**
 * The three Kenyan mobile networks supported by the machine.
 *
 * IMPORTANT: no USSD string is attached to a network here. Every operator flow is
 * configured by an administrator and must be verified with the operator before it is
 * enabled. See [UssdWorkflow].
 */
enum class Network(
    val code: String,
    val displayName: String,
    val shortName: String,
    val accent: Long
) {
    SAFARICOM("SAFARICOM", "Safaricom Kenya", "Safaricom", 0xFF00C853),
    AIRTEL("AIRTEL", "Airtel Kenya", "Airtel", 0xFFFF3B30),
    TELKOM("TELKOM", "Telkom Kenya", "Telkom", 0xFF2E7BFF);

    companion object {
        fun fromCode(code: String?): Network? =
            entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) }

        fun require(code: String): Network =
            fromCode(code) ?: error("Unknown network: $code")
    }
}
