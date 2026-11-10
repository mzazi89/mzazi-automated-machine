package com.mzazitech.automatedmachine.domain

import com.google.common.truth.Truth.assertThat
import com.mzazitech.automatedmachine.domain.model.Network
import com.mzazitech.automatedmachine.domain.model.UssdFailureCategory
import com.mzazitech.automatedmachine.domain.model.UssdRunParams
import com.mzazitech.automatedmachine.domain.model.UssdRunResult
import com.mzazitech.automatedmachine.domain.model.UssdStep
import com.mzazitech.automatedmachine.domain.model.UssdWorkflow
import com.mzazitech.automatedmachine.domain.ussd.SimSlotInfo
import com.mzazitech.automatedmachine.domain.ussd.UssdEngine
import com.mzazitech.automatedmachine.domain.ussd.UssdEngineConfig
import com.mzazitech.automatedmachine.domain.ussd.UssdExecutor
import com.mzazitech.automatedmachine.domain.ussd.UssdOutcome
import com.mzazitech.automatedmachine.domain.ussd.UssdRequest
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeExecutor(
    private val sims: List<SimSlotInfo> = listOf(SimSlotInfo(0, 1, "Test Carrier", "SIM 1", true)),
    private val simulated: Boolean = true,
    private val scripted: MutableList<UssdOutcome> = mutableListOf()
) : UssdExecutor {

    val sent = mutableListOf<String>()

    override fun isSimulation(): Boolean = simulated
    override suspend fun listSims(): List<SimSlotInfo> = sims

    override suspend fun send(request: UssdRequest): UssdOutcome {
        sent += request.ussd
        return if (scripted.isEmpty()) UssdOutcome.Response("default") else scripted.removeAt(0)
    }
}

class UssdEngineTest {

    private val params = UssdRunParams(
        msisdn = "254712345678",
        msisdnLocal = "0712345678",
        amountShillings = 100,
        bundleSizeMb = 1024,
        bundleCode = "SK1000",
        simSubscriptionId = 1,
        simSlot = 0
    )

    private fun workflow(
        steps: List<UssdStep>,
        verifiedBy: String? = "QA verified",
        successPattern: String? = "(?i)(successful|has been activated)",
        failurePattern: String? = "(?i)(failed|insufficient|declined)"
    ) = UssdWorkflow(
        id = "wf-1",
        network = Network.SAFARICOM,
        name = "Test workflow",
        steps = steps,
        successPattern = successPattern,
        failurePattern = failurePattern,
        timeoutMs = 30_000,
        enabled = true,
        verifiedBy = verifiedBy,
        version = 1,
        updatedAt = 0
    )

    private fun engine(executor: UssdExecutor) = UssdEngine(
        executor = executor,
        config = UssdEngineConfig(stepRetries = 0, interStepDelayMs = 0),
        now = { 0L },
        sleep = { }
    )

    private val singleStep = listOf(
        UssdStep(
            index = 0,
            label = "Purchase",
            requestTemplate = "*544*{bundleSizeMb}*{msisdnLocal}#",
            expectedPattern = null
        )
    )

    @Test
    fun `successful single step returns success with the carrier receipt`() = runTest {
        val executor = FakeExecutor(
            scripted = mutableListOf(UssdOutcome.Response("Your purchase was successful. 1GB added."))
        )

        val result = engine(executor).run(workflow(singleStep), params)

        assertThat(result).isInstanceOf(UssdRunResult.Success::class.java)
        assertThat((result as UssdRunResult.Success).response).contains("successful")
        assertThat(executor.sent).containsExactly("*544*1024*0712345678#")
    }

    @Test
    fun `carrier rejection is terminal and never retried`() = runTest {
        val executor = FakeExecutor(
            scripted = mutableListOf(UssdOutcome.Response("Request failed. Insufficient balance."))
        )

        val result = engine(executor).run(workflow(singleStep), params)

        val failed = result as UssdRunResult.Failed
        assertThat(failed.category).isEqualTo(UssdFailureCategory.CARRIER_REJECTED)
        assertThat(failed.retryable).isFalse()
        assertThat(executor.sent).hasSize(1)
    }

    @Test
    fun `timeout is surfaced as a timeout failure`() = runTest {
        val executor = FakeExecutor(
            scripted = mutableListOf(
                UssdOutcome.Failure("No response", UssdFailureCategory.TIMEOUT)
            )
        )

        val result = engine(executor).run(workflow(singleStep), params)

        assertThat((result as UssdRunResult.Failed).category)
            .isEqualTo(UssdFailureCategory.TIMEOUT)
    }

    @Test
    fun `menu response stops the engine and asks for manual confirmation`() = runTest {
        val executor = FakeExecutor(
            scripted = mutableListOf(
                UssdOutcome.Response("Select option\n1. Buy data\n2. Buy airtime")
            )
        )

        val result = engine(executor).run(workflow(singleStep), params)

        assertThat(result).isInstanceOf(UssdRunResult.NeedsManualConfirmation::class.java)
        assertThat((result as UssdRunResult.NeedsManualConfirmation).dialCode)
            .isEqualTo("*544*1024*0712345678#")
    }

    @Test
    fun `interactive step is refused instead of firing blind menu digits`() = runTest {
        val steps = listOf(
            UssdStep(0, "Dial", "*544*{bundleSizeMb}#"),
            UssdStep(1, "Confirm", "1", sendAsSessionInput = true)
        )
        val executor = FakeExecutor(
            scripted = mutableListOf(UssdOutcome.Response("Your request was successful"))
        )

        val result = engine(executor).run(workflow(steps), params)

        assertThat(result).isInstanceOf(UssdRunResult.NeedsManualConfirmation::class.java)
        // Only the first step ever reached the executor.
        assertThat(executor.sent).containsExactly("*544*1024#")
    }

    @Test
    fun `no sim fails before any request is sent`() = runTest {
        val executor = FakeExecutor(sims = emptyList())

        val result = engine(executor).run(workflow(singleStep), params)

        assertThat((result as UssdRunResult.Failed).category).isEqualTo(UssdFailureCategory.NO_SIM)
        assertThat(executor.sent).isEmpty()
    }

    @Test
    fun `simulation-only workflow is refused on a real sim`() = runTest {
        val executor = FakeExecutor(simulated = false)

        val result = engine(executor).run(
            workflow(singleStep, verifiedBy = UssdEngine.TEST_WORKFLOW_MARKER),
            params
        )

        assertThat((result as UssdRunResult.Failed).category)
            .isEqualTo(UssdFailureCategory.INVALID_REQUEST)
        assertThat(executor.sent).isEmpty()
    }

    @Test
    fun `unverified workflow cannot run`() = runTest {
        val executor = FakeExecutor()

        val result = engine(executor).run(workflow(singleStep, verifiedBy = null), params)

        assertThat(result).isInstanceOf(UssdRunResult.Failed::class.java)
        assertThat(executor.sent).isEmpty()
    }

    @Test
    fun `malformed template is rejected without touching the radio`() = runTest {
        val executor = FakeExecutor()
        val badStep = listOf(UssdStep(0, "Bad", "not-a-ussd-code"))

        val result = engine(executor).run(workflow(badStep), params)

        assertThat((result as UssdRunResult.Failed).category)
            .isEqualTo(UssdFailureCategory.INVALID_REQUEST)
        assertThat(executor.sent).isEmpty()
    }

    @Test
    fun `unknown receipt never reports success`() = runTest {
        val executor = FakeExecutor(scripted = mutableListOf(UssdOutcome.Response("OK")))

        val result = engine(executor).run(workflow(singleStep), params)

        assertThat(result).isInstanceOf(UssdRunResult.NeedsManualConfirmation::class.java)
    }
}
