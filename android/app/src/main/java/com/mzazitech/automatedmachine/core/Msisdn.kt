package com.mzazitech.automatedmachine.core

/**
 * Kenyan MSISDN normalisation.
 *
 * Safaricom, Airtel and Telkom all publish numbers in the 07XX / 01XX national format
 * and in the 2547XX / 2541XX international format. Internal storage and API
 * transport always use the international format without a plus sign (2547XXXXXXXX)
 * because that is what USSD templates and Daraja expect.
 */
object Msisdn {

    const val COUNTRY_CODE = "254"

    private val nationalPrefixes = listOf("7", "1")

    /**
     * Accepts 07XXXXXXXX, 01XXXXXXXX, 7XXXXXXXX, 2547XXXXXXXX, +2547XXXXXXXX,
     * with spaces, dashes or brackets. Returns 254XXXXXXXXX or null when invalid.
     */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        var digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null

        digits = when {
            digits.startsWith("254") -> digits
            digits.startsWith("0") -> COUNTRY_CODE + digits.substring(1)
            digits.length == 9 && nationalPrefixes.any { digits.startsWith(it) } -> COUNTRY_CODE + digits
            else -> return null
        }

        return if (isValid(digits)) digits else null
    }

    /** 254712345678 -> 0712345678 */
    fun toNational(msisdn: String): String {
        val normalized = normalize(msisdn) ?: return msisdn
        return "0" + normalized.removePrefix(COUNTRY_CODE)
    }

    fun isValid(msisdn: String): Boolean {
        if (msisdn.length != 12) return false
        if (!msisdn.startsWith(COUNTRY_CODE)) return false
        val subscriber = msisdn.removePrefix(COUNTRY_CODE)
        if (subscriber.length != 9) return false
        if (!subscriber.all { it.isDigit() }) return false
        // Kenyan mobile numbers begin with 7 or 1 after the country code.
        return nationalPrefixes.any { subscriber.startsWith(it) } && subscriber[1] != '0'
    }

    fun mask(msisdn: String): String {
        val normalized = normalize(msisdn) ?: return "—"
        return normalized.take(6) + "***" + normalized.takeLast(2)
    }

    /**
     * Best-effort mapping of a numbering prefix to a network, used only as a UI hint.
     * Number portability means a prefix never proves the current carrier, so the
     * operator always remains free to override the selection.
     */
    fun guessNetwork(msisdn: String): String? {
        val normalized = normalize(msisdn) ?: return null
        val prefix = normalized.removePrefix(COUNTRY_CODE).take(3)
        return when {
            prefix.startsWith("70") || prefix.startsWith("71") || prefix.startsWith("72") ||
                prefix.startsWith("79") || prefix == "757" || prefix == "758" -> "SAFARICOM"
            prefix.startsWith("73") || prefix.startsWith("78") || prefix.startsWith("010") -> "AIRTEL"
            prefix.startsWith("77") -> "TELKOM"
            prefix.startsWith("011") -> "SAFARICOM"
            else -> null
        }
    }
}
