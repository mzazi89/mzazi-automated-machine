package com.mzazitech.automatedmachine.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {

    @Test
    fun `parses shillings into cents without float drift`() {
        assertThat(Money.parse("100")?.cents).isEqualTo(10_000L)
        assertThat(Money.parse("100.50")?.cents).isEqualTo(10_050L)
        assertThat(Money.parse("1,250.99")?.cents).isEqualTo(125_099L)
        assertThat(Money.parse("KES 250")?.cents).isEqualTo(25_000L)
    }

    @Test
    fun `rejects negative and non numeric values`() {
        assertThat(Money.parse("-5")).isNull()
        assertThat(Money.parse("abc")).isNull()
        assertThat(Money.parse("")).isNull()
        assertThat(Money.parse(null)).isNull()
    }

    @Test
    fun `formats with the KES prefix and two decimals`() {
        assertThat(Money(125_050).format()).isEqualTo("KES 1,250.50")
        assertThat(Money(10_000).formatPlain()).isEqualTo("100.00")
    }

    @Test
    fun `arithmetic stays in cents`() {
        val price = Money(10_000)
        assertThat((price + Money(250)).cents).isEqualTo(10_250L)
        assertThat((price - Money(250)).cents).isEqualTo(9_750L)
        assertThat((price * 3).cents).isEqualTo(30_000L)
    }

    @Test
    fun `comparison supports margin checks`() {
        assertThat(Money(10_000) > Money(9_000)).isTrue()
        assertThat(Money(9_000) < Money(10_000)).isTrue()
    }
}
