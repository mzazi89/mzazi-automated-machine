package com.mzazitech.automatedmachine.core

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Money is always handled as whole cents (Long) to avoid floating point drift.
 * 1 KES == 100 cents.
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    val shillings: Long get() = cents / 100

    operator fun plus(other: Money) = Money(cents + other.cents)
    operator fun minus(other: Money) = Money(cents - other.cents)
    operator fun times(factor: Int) = Money(cents * factor)

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    /** "KES 1,250.00" */
    fun format(): String = "KES " + decimalFormat.format(cents / 100.0)

    /** "1,250.00" (no currency prefix, for compact KPI cards). */
    fun formatPlain(): String = decimalFormat.format(cents / 100.0)

    override fun toString(): String = format()

    companion object {
        val ZERO = Money(0)

        private val decimalFormat: NumberFormat =
            NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }

        /** Parses "250", "250.50", "1,250.50". Returns null when invalid or negative. */
        fun parse(input: String?): Money? {
            val cleaned = input?.trim()?.replace(",", "")?.replace("KES", "", ignoreCase = true)?.trim()
            if (cleaned.isNullOrEmpty()) return null
            val value = cleaned.toDoubleOrNull() ?: return null
            if (value < 0 || value.isNaN() || value.isInfinite()) return null
            return Money((value * 100).roundToLong())
        }

        fun fromShillings(shillings: Long): Money = Money(shillings * 100)

        fun fromCents(cents: Long): Money = Money(abs(cents))
    }
}
