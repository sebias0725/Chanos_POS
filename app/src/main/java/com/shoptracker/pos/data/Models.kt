package com.shoptracker.pos.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val STATUS_COMPLETED = "COMPLETED"
const val STATUS_UNDONE = "UNDONE"

/** All money is stored as whole centavos (Long) to avoid floating point errors. */
@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val priceCentavos: Long,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "business_days")
data class BusinessDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val openedAt: Long,
    val closedAt: Long? = null,
    val openingFloatCentavos: Long,
    val openingCashCentavos: Long,
    val targetUnits: Int,
    val finalFloatCentavos: Long? = null,
    val finalCashCentavos: Long? = null
)

val BusinessDay.isOpen: Boolean get() = closedAt == null

/**
 * A single sale. productName and unitPriceCentavos are snapshots taken at the time of sale,
 * so later product edits never change history. Undone sales are kept (status = UNDONE).
 */
@Entity(
    tableName = "sales_transactions",
    indices = [Index("businessDayId")]
)
data class SalesTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessDayId: Long,
    val productId: Long,
    val productName: String,
    val unitPriceCentavos: Long,
    val quantity: Int = 1,
    val totalCentavos: Long,
    val timestamp: Long,
    val status: String = STATUS_COMPLETED,
    val undoneAt: Long? = null
)

data class ProductTotal(
    val productId: Long,
    val units: Int,
    val revenue: Long
)

data class DayTotal(
    val dayId: Long,
    val units: Int,
    val revenue: Long
)

sealed interface ExportScope {
    data object Today : ExportScope
    data class Day(val dayId: Long) : ExportScope
    data object All : ExportScope
}
