package com.mzazitech.automatedmachine.domain

import com.google.common.truth.Truth.assertThat
import com.mzazitech.automatedmachine.domain.ussd.UssdResponseClassifier
import com.mzazitech.automatedmachine.domain.ussd.UssdResponseClassifier.Verdict
import org.junit.Test

class UssdResponseClassifierTest {

    private val successPattern = "(?i)(successful|has been activated|you have purchased)"
    private val failurePattern = "(?i)(failed|insufficient|declined)"

    @Test
    fun `configured success pattern wins`() {
        val result = UssdResponseClassifier.classify(
            "Dear customer, your bundle purchase was successful. Thank you.",
            successPattern,
            failurePattern
        )
        assertThat(result.verdict).isEqualTo(Verdict.SUCCESS)
    }

    @Test
    fun `configured failure pattern wins over a success hint`() {
        // Real carriers often say "successfully received your request" and then fail.
        val result = UssdResponseClassifier.classify(
            "We have successfully received your request but the transaction failed.",
            successPattern,
            failurePattern
        )
        assertThat(result.verdict).isEqualTo(Verdict.FAILURE)
    }

    @Test
    fun `menu responses require a human`() {
        val result = UssdResponseClassifier.classify(
            "Select option:\n1. Buy data\n2. Buy airtime",
            successPattern,
            failurePattern
        )
        assertThat(result.verdict).isEqualTo(Verdict.MENU)
        assertThat(result.requiresUserInput).isTrue()
    }

    @Test
    fun `empty response is unknown, never success`() {
        assertThat(UssdResponseClassifier.classify("", successPattern, failurePattern).verdict)
            .isEqualTo(Verdict.UNKNOWN)
        assertThat(UssdResponseClassifier.classify(null, successPattern, failurePattern).verdict)
            .isEqualTo(Verdict.UNKNOWN)
    }

    @Test
    fun `falls back to builtin hints when no pattern matches`() {
        val success = UssdResponseClassifier.classify("You have bought 1GB valid for 24hrs", null, null)
        assertThat(success.verdict).isEqualTo(Verdict.SUCCESS)

        val failure = UssdResponseClassifier.classify("Request declined. Insufficient balance.", null, null)
        assertThat(failure.verdict).isEqualTo(Verdict.FAILURE)
    }

    @Test
    fun `an invalid regex does not crash the engine`() {
        val result = UssdResponseClassifier.classify("anything", "([unclosed", "");
        assertThat(result.verdict).isEqualTo(Verdict.UNKNOWN)
    }
}
