package com.shoptracker.pos.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val STATUS_COMPLETED = "COMPLETED"
const val STATUS_UNDONE = "UNDONE"
const val STATUS_CANCELLED = "CANCELLED"

const val ORDER_PICKUP = "PICKUP"
const val ORDER_DELIVERY = "DELIVERY"

/** All money is stored as whole centavos (Long) to avoid floating point errors. */
@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val priceCentavos: Long,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** Profit per unit, in centavos (entered by the user). */
    @ColumnInfo(defaultValue = "0") val profitCentavos: Long = 0,
    /** Remaining stock (units). Reduced by sales, restored by undo/remove/cancel. */
    @ColumnInfo(defaultValue = "0") val stock: Int = 0
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
 * A single sale line. productName, unitPriceCentavos and unitProfitCentavos are snapshots taken
 * at the time of sale, so later product edits never change history. Undone sales are kept
 * (status = UNDONE). orderId is set when the line belongs to a New Order.
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
    val undoneAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val unitProfitCentavos: Long = 0,
    val orderId: Long? = null
)

/** A pickup or delivery order (bulk or regular). Its items are stored as SalesTransactions. */
@Entity(
    tableName = "orders",
    indices = [Index("businessDayId")]
)
data class SalesOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessDayId: Long,
    val customerName: String,
    val type: String,
    val address: String,
    val deliveryFeeCentavos: Long,
    val note: String,
    val createdAt: Long,
    val status: String = STATUS_COMPLETED,
    val cancelledAt: Long? = null
)

@Entity(
    tableName = "expenses",
    indices = [Index("businessDayId")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessDayId: Long,
    val description: String,
    val amountCentavos: Long,
    val createdAt: Long
)

/** Delivery fee reference list entry (area / destination and its fee). */
@Entity(tableName = "delivery_rates")
data class DeliveryRate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val feeCentavos: Long,
    val note: String = ""
)

data class ProductTotal(
    val productId: Long,
    val units: Int,
    val revenue: Long
)

sealed interface ExportScope {
    data object Today : ExportScope
    data class Day(val dayId: Long) : ExportScope
    data object All : ExportScope
}
