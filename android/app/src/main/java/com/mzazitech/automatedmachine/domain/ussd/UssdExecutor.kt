package com.mzazitech.automatedmachine.domain.ussd

import com.mzazitech.automatedmachine.domain.model.UssdFailureCategory

/** A single physical SIM slot as reported by the platform. */
data class SimSlotInfo(
    val slotIndex: Int,
    val subscriptionId: Int?,
    val carrierName: String?,
    val displayName: String?,
    val isDefaultVoice: Boolean
) {
    val label: String
        get() = "SIM ${slotIndex + 1}" + (carrierName?.let { " · $it" } ?: "")
}

data class UssdRequest(
    val ussd: String,
    val subscriptionId: Int?,
    val simSlot: Int,
    val timeoutMs: Long
)

sealed interface UssdOutcome {
    data class Response(val text: String) : UssdOutcome
    data class Failure(
        val message: String,
        val category: UssdFailureCategory,
        val retryable: Boolean = false
    ) : UssdOutcome
    data object Cancelled : UssdOutcome
}

/**
 * Platform abstraction over USSD transmission.
 *
 * [com.mzazitech.automatedmachine.data.ussd.AndroidTelephonyUssdExecutor] uses the
 * public TelephonyManager API. A simulation implementation is used for the safe test
 * mode so no real request ever reaches a carrier.
 */
interface UssdExecutor {
    suspend fun listSims(): List<SimSlotInfo>
    suspend fun send(request: UssdRequest): UssdOutcome
    fun isSimulation(): Boolean
}
