package com.mzazitech.automatedmachine.data.repository

import com.mzazitech.automatedmachine.core.Money
import com.mzazitech.automatedmachine.core.Time
import com.mzazitech.automatedmachine.data.local.BundleDao
import com.mzazitech.automatedmachine.data.local.toDomain
import com.mzazitech.automatedmachine.data.local.toEntity
import com.mzazitech.automatedmachine.domain.model.Bundle
import com.mzazitech.automatedmachine.domain.model.Network
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class BundleRepository(private val dao: BundleDao) {

    fun observeAll(): Flow<List<Bundle>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeByNetwork(network: Network): Flow<List<Bundle>> =
        dao.observeByNetwork(network.code).map { list -> list.map { it.toDomain() } }

    suspend fun byId(id: String): Bundle? = dao.byId(id)?.toDomain()

    suspend fun upsert(bundle: Bundle) = dao.upsert(bundle.toEntity())

    suspend fun setEnabled(id: String, enabled: Boolean) =
        dao.setEnabled(id, enabled, Time.nowMillis())

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun create(
        network: Network,
        name: String,
        dataSizeMb: Int,
        sellingPrice: Money,
        costPrice: Money,
        validityDays: Int,
        workflowId: String?
    ): Bundle {
        val bundle = Bundle(
            id = UUID.randomUUID().toString(),
            network = network,
            name = name.trim(),
            dataSizeMb = dataSizeMb,
            sellingPrice = sellingPrice,
            costPrice = costPrice,
            validityDays = validityDays,
            workflowId = workflowId,
            enabled = true,
            sortOrder = dataSizeMb,
            updatedAt = Time.nowMillis()
        )
        dao.upsert(bundle.toEntity())
        return bundle
    }

    suspend fun replaceAll(bundles: List<Bundle>) = dao.upsertAll(bundles.map { it.toEntity() })

    /**
     * Sample catalogue used until the administrator imports real products.
     * The prices are placeholders, not operator or market prices.
     */
    suspend fun ensureDefaults() {
        if (dao.all().isNotEmpty()) return

        val now = Time.nowMillis()
        val samples = buildList {
            listOf(512 to 50L, 1024 to 100L, 2048 to 200L, 5120 to 450L).forEach { (mb, price) ->
                Network.entries.forEach { network ->
                    add(
                        Bundle(
                            id = "seed-${network.code.lowercase()}-$mb",
                            network = network,
                            name = "${network.shortName} ${if (mb >= 1024) "${mb / 1024} GB" else "$mb MB"}",
                            dataSizeMb = mb,
                            sellingPrice = Money.fromShillings(price),
                            costPrice = Money.fromShillings((price * 0.85).toLong()),
                            validityDays = 30,
                            workflowId = "test-${network.code.lowercase()}",
                            enabled = true,
                            sortOrder = mb,
                            updatedAt = now
                        )
                    )
                }
            }
        }
        dao.upsertAll(samples.map { it.toEntity() })
    }
}
