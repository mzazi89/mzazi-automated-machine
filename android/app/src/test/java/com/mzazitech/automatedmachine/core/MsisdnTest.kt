package com.mzazitech.automatedmachine.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MsisdnTest {

    @Test
    fun `normalises every common Kenyan format to 254`() {
        val expected = "254712345678"
        assertThat(Msisdn.normalize("0712345678")).isEqualTo(expected)
        assertThat(Msisdn.normalize("712345678")).isEqualTo(expected)
        assertThat(Msisdn.normalize("+254712345678")).isEqualTo(expected)
        assertThat(Msisdn.normalize("254712345678")).isEqualTo(expected)
        assertThat(Msisdn.normalize("0712 345 678")).isEqualTo(expected)
        assertThat(Msisdn.normalize("0712-345-678")).isEqualTo(expected)
        assertThat(Msisdn.normalize("(0712) 345678")).isEqualTo(expected)
    }

    @Test
    fun `accepts the 01 subscriber range`() {
        assertThat(Msisdn.normalize("0110000000")).isEqualTo("254110000000")
    }

    @Test
    fun `rejects obviously invalid input`() {
        assertThat(Msisdn.normalize(null)).isNull()
        assertThat(Msisdn.normalize("")).isNull()
        assertThat(Msisdn.normalize("abc")).isNull()
        assertThat(Msisdn.normalize("0712345")).isNull()
        assertThat(Msisdn.normalize("0812345678")).isNull() // 08 is not a mobile range
        assertThat(Msisdn.normalize("255712345678")).isNull() // wrong country code
    }

    @Test
    fun `converts back to national format for USSD templates`() {
        assertThat(Msisdn.toNational("254712345678")).isEqualTo("0712345678")
    }

    @Test
    fun `masks the subscriber digits`() {
        val masked = Msisdn.mask("254712345678")
        assertThat(masked).isEqualTo("254712***78")
    }

    @Test
    fun `prefix hint maps well known ranges`() {
        assertThat(Msisdn.guessNetwork("0712345678")).isEqualTo("SAFARICOM")
        assertThat(Msisdn.guessNetwork("0733123456")).isEqualTo("AIRTEL")
        assertThat(Msisdn.guessNetwork("0770123456")).isEqualTo("TELKOM")
    }
}
