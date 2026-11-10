package com.mzazitech.automatedmachine.data.ussd

import com.mzazitech.automatedmachine.domain.model.UssdFailureCategory
import com.mzazitech.automatedmachine.domain.ussd.SimSlotInfo
import com.mzazitech.automatedmachine.domain.ussd.UssdExecutor
import com.mzazitech.automatedmachine.domain.ussd.UssdOutcome
import com.mzazitech.automatedmachine.domain.ussd.UssdRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Test-mode transport. No radio, no carrier, no money.
 *
 * The responses below are synthetic strings produced by this app for rehearsal only;
 * they are not operator codes and must never be presented as such.
 */
class SimulatorUssdExecutor(
    private var sims: List<SimSlotInfo> = listOf(
        SimSlotInfo(0, 1, "MZAZI SIM 1 (test)", "SIM 1", true),
        SimSlotInfo(1, 2, "MZAZI SIM 2 (test)", "SIM 2", false)
    ),
    /** 0.0 - 1.0 chance the simulated carrier returns a failure. Kept at 0 for determinism. */
    private var failureRate: Double = 0.0
) : UssdExecutor {

    private val mutex = Mutex()
    private var calls = 0

    override fun isSimulation(): Boolean = true

    fun configure(sims: List<SimSlotInfo>, failureRate: Double) {
        this.sims = sims
        this.failureRate = failureRate.coerceIn(0.0, 1.0)
    }

    override suspend fun listSims(): List<SimSlotInfo> = mutex.withLock { sims }

    override suspend fun send(request: UssdRequest): UssdOutcome {
        delay(SIMULATED_LATENCY_MS)
        mutex.withLock { calls++ }

        if (!request.ussd.startsWith("*") && !request.ussd.startsWith("#")) {
            return UssdOutcome.Failure(
                "Simulated carrier rejected a malformed request.",
                UssdFailureCategory.INVALID_REQUEST
            )
        }
        if (request.subscriptionId != null && sims.none { it.subscriptionId == request.subscriptionId }) {
            return UssdOutcome.Failure(
                "Simulated carrier: SIM not registered on the network.",
                UssdFailureCategory.SIM_UNAVAILABLE
            )
        }

        val failing = failureRate > 0.0 && (calls * 37 % 100) / 100.0 < failureRate
        return if (failing) {
            UssdOutcome.Failure(
                "Simulated carrier failure: insufficient airtime.",
                UssdFailureCategory.CARRIER_REJECTED
            )
        } else {
            UssdOutcome.Response(SIMULATED_SUCCESS)
        }
    }

    companion object {
        const val SIMULATED_LATENCY_MS = 900L
        const val SIMULATED_SUCCESS =
            "[TEST MODE] Simulated carrier receipt: your data bundle request has been processed successfully. " +
                "No real purchase was made."
    }
}
