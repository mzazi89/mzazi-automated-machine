package com.mzazitech.automatedmachine.domain.model

import com.mzazitech.automatedmachine.core.Money

/**
 * A sellable data bundle. Prices are administrator controlled; the machine never
 * guesses an operator price.
 */
data class Bundle(
    val id: String,
    val network: Network,
    val name: String,
    val dataSizeMb: Int,
    val sellingPrice: Money,
    val costPrice: Money,
    val validityDays: Int,
    val workflowId: String?,
    val enabled: Boolean,
    val sortOrder: Int,
    val updatedAt: Long
) {
    val margin: Money get() = sellingPrice - costPrice

    val sizeLabel: String
        get() = when {
            dataSizeMb >= 1024 && dataSizeMb % 1024 == 0 -> "${dataSizeMb / 1024} GB"
            dataSizeMb >= 1024 -> String.format("%.1f GB", dataSizeMb / 1024.0)
            else -> "$dataSizeMb MB"
        }
}
