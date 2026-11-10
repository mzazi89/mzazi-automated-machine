package com.mzazitech.automatedmachine.domain

import com.google.common.truth.Truth.assertThat
import com.mzazitech.automatedmachine.domain.model.UssdTemplate
import org.junit.Test

class UssdTemplateTest {

    @Test
    fun `accepts a well formed ussd request`() {
        assertThat(UssdTemplate.validate("*544*3*{msisdn}*{amount}#")).isNull()
        assertThat(UssdTemplate.validate("#100#")).isNull()
    }

    @Test
    fun `rejects templates that are not dialable`() {
        assertThat(UssdTemplate.validate("")).contains("empty")
        assertThat(UssdTemplate.validate("hello")).isNotNull()
        assertThat(UssdTemplate.validate("*544*3*{msisdn}")).contains("#")
        assertThat(UssdTemplate.validate("544*3#")).isNotNull()
    }

    @Test
    fun `rejects unknown placeholders`() {
        val problem = UssdTemplate.validate("*100*{pin}#")
        assertThat(problem).contains("{pin}")
    }

    @Test
    fun `renders placeholders and strips anything unsafe`() {
        val rendered = UssdTemplate.render(
            "*544*{bundleSizeMb}*{msisdnLocal}#",
            mapOf("bundleSizeMb" to "1024", "msisdnLocal" to "0712 345 678")
        )
        assertThat(rendered).isEqualTo("*544*1024*0712345678#")
    }

    @Test
    fun `sanitises injection attempts`() {
        val rendered = UssdTemplate.render("*100*{amount}#", mapOf("amount" to "10#*999*1"))
        assertThat(rendered).isEqualTo("*100*10#*999*1#")
        // Even when a customer value contains metacharacters, only USSD-safe characters
        // survive; letters cannot break out of the string.
        assertThat(UssdTemplate.sanitize("12ab#*")).isEqualTo("12#*")
    }
}
