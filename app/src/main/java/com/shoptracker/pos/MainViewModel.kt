package com.shoptracker.pos

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.shoptracker.pos.data.AppDatabase
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.DeliveryRate
import com.shoptracker.pos.data.ExportScope
import com.shoptracker.pos.data.Expense
import com.shoptracker.pos.data.ORDER_DELIVERY
import com.shoptracker.pos.data.Product
import com.shoptracker.pos.data.ProductTotal
import com.shoptracker.pos.data.STATUS_CANCELLED
import com.shoptracker.pos.data.STATUS_COMPLETED
import com.shoptracker.pos.data.SalesOrder
import com.shoptracker.pos.data.SalesTransaction
import com.shoptracker.pos.export.XlsxExporter
import com.shoptracker.pos.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val productDao = db.productDao()
    private val dayDao = db.businessDayDao()
    private val txDao = db.transactionDao()
    private val orderDao = db.orderDao()
    private val expenseDao = db.expenseDao()
    private val rateDao = db.rateDao()

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _confirmTaps = MutableStateFlow(prefs.getBoolean("confirm_taps", true))

    /** When true (default) every product tap / + / - asks for confirmation first. */
    val confirmTaps: StateFlow<Boolean> = _confirmTaps.asStateFlow()

    fun setConfirmTaps(value: Boolean) {
        _confirmTaps.value = value
        prefs.edit().putBoolean("confirm_taps", value).apply()
    }

    val products: StateFlow<List<Product>> =
        productDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val openDay: StateFlow<BusinessDay?> =
        dayDao.observeOpen().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Newest first. */
    val allDays: StateFlow<List<BusinessDay>> =
        dayDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lifetime: StateFlow<Map<Long, ProductTotal>> =
        txDao.observeLifetime().map { list -> list.associateBy { it.productId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val allTxns: StateFlow<List<SalesTransaction>> =
        txDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allOrders: StateFlow<List<SalesOrder>> =
        orderDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allExpenses: StateFlow<List<Expense>> =
        expenseDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rates: StateFlow<List<DeliveryRate>> =
        rateDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _events = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<String> = _events.asSharedFlow()

    private fun say(msg: String) {
        _events.tryEmit(msg)
    }

    // ---------------- sales ----------------

    /** Records one sale of [product] under the open business day, atomically. Reduces stock by 1. */
    fun sell(product: Product) {
        viewModelScope.launch {
            try {
                val ok: Boolean = db.withTransaction {
                    val day = dayDao.getOpen() ?: return@withTransaction false
                    val p = productDao.getById(product.id) ?: return@withTransaction false
                    if (!p.active) return@withTransaction false
                    txDao.insert(
                        SalesTransaction(
                            businessDayId = day.id,
                            productId = p.id,
                            productName = p.name,
                            unitPriceCentavos = p.priceCentavos,
                            quantity = 1,
                            totalCentavos = p.priceCentavos,
                            timestamp = System.currentTimeMillis(),
                            unitProfitCentavos = p.profitCentavos
                        )
                    )
                    productDao.adjustStock(p.id, -1)
                    true
                }
                if (!ok) say("Sale not recorded: no active business day or product disabled")
            } catch (e: Exception) {
                say("Could not save sale: ${e.message}")
            }
        }
    }

    /**
     * The "-" button: removes the latest single sale of this product from the open day
     * (the record is kept as UNDONE) and puts the unit back in stock.
     */
    fun removeOne(product: Product) {
        viewModelScope.launch {
            try {
                val removed: SalesTransaction? = db.withTransaction {
                    val day = dayDao.getOpen() ?: return@withTransaction null
                    val last = txDao.getLastSingleSale(day.id, product.id) ?: return@withTransaction null
                    val changed = txDao.markUndone(last.id, System.currentTimeMillis())
                    if (changed == 1) {
                        productDao.adjustStock(last.productId, last.quantity)
                        last
                    } else {
                        null
                    }
                }
                if (removed == null) {
                    say("No single sale of ${product.name} to remove (cancel an order from Orders instead)")
                } else {
                    say("Removed 1 × ${removed.productName}")
                }
            } catch (e: Exception) {
                say("Could not remove sale: ${e.message}")
            }
        }
    }

    /**
     * Undoes the most recent completed sale of the open business day. Can be repeated.
     * If that sale is part of an order, the whole order is cancelled so orders never end up half-undone.
     */
    fun undo() {
        viewModelScope.launch {
            try {
                val message: String? = db.withTransaction {
                    val day = dayDao.getOpen() ?: return@withTransaction null
                    val last = txDao.getLastCompleted(day.id) ?: return@withTransaction null
                    val orderId = last.orderId
                    if (orderId != null) {
                        cancelOrderInternal(orderId)
                        "Undid order #$orderId"
                    } else {
                        val changed = txDao.markUndone(last.id, System.currentTimeMillis())
                        if (changed == 1) {
                            productDao.adjustStock(last.productId, last.quantity)
                            "Undid: ${last.productName} (${Fmt.peso(last.totalCentavos)})"
                        } else {
                            null
                        }
                    }
                }
                say(message ?: "Nothing to undo")
            } catch (e: Exception) {
                say("Undo failed: ${e.message}")
            }
        }
    }

    // ---------------- orders ----------------

    /** Records a bulk / pickup / delivery order as sales of the open business day, atomically. */
    fun createOrder(
        customer: String,
        type: String,
        address: String,
        deliveryFeeCentavos: Long,
        note: String,
        items: List<Pair<Long, Int>>,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            var ok = false
            try {
                ok = db.withTransaction {
                    val day = dayDao.getOpen() ?: return@withTransaction false
                    val lines = items.filter { it.second > 0 }
                    if (lines.isEmpty()) return@withTransaction false
                    val now = System.currentTimeMillis()
                    val orderId = orderDao.insert(
                        SalesOrder(
                            businessDayId = day.id,
                            customerName = customer.trim(),
                            type = type,
                            address = address.trim(),
                            deliveryFeeCentavos = if (type == ORDER_DELIVERY) deliveryFeeCentavos else 0L,
                            note = note.trim(),
                            createdAt = now
                        )
                    )
                    var inserted = 0
                    for ((productId, qty) in lines) {
                        val p = productDao.getById(productId) ?: continue
                        txDao.insert(
                            SalesTransaction(
                                businessDayId = day.id,
                                productId = p.id,
                                productName = p.name,
                                unitPriceCentavos = p.priceCentavos,
                                quantity = qty,
                                totalCentavos = p.priceCentavos * qty,
                                timestamp = now,
                                unitProfitCentavos = p.profitCentavos,
                                orderId = orderId
                            )
                        )
                        productDao.adjustStock(p.id, -qty)
                        inserted++
                    }
                    if (inserted == 0) throw IllegalStateException("None of the products exist any more")
                    true
                }
                say(if (ok) "Order saved" else "Order not saved: no active business day")
            } catch (e: Exception) {
                say("Could not save order: ${e.message}")
            }
            onResult(ok)
        }
    }

    fun cancelOrder(orderId: Long) {
        viewModelScope.launch {
            try {
                val ok: Boolean = db.withTransaction {
                    val order = orderDao.getById(orderId) ?: return@withTransaction false
                    val day = dayDao.getById(order.businessDayId)
                    if (day == null || day.closedAt != null) return@withTransaction false
                    cancelOrderInternal(orderId)
                    true
                }
                say(if (ok) "Order #$orderId cancelled" else "Only orders of the open business day can be cancelled")
            } catch (e: Exception) {
                say("Could not cancel order: ${e.message}")
            }
        }
    }

    /** Must be called inside a transaction. Restores stock and marks the order and its lines. */
    private suspend fun cancelOrderInternal(orderId: Long) {
        val order = orderDao.getById(orderId) ?: return
        if (order.status != STATUS_COMPLETED) return
        val now = System.currentTimeMillis()
        for (line in txDao.getByOrder(orderId)) {
            if (line.status == STATUS_COMPLETED) productDao.adjustStock(line.productId, line.quantity)
        }
        txDao.markUndoneByOrder(orderId, now)
        orderDao.update(order.copy(status = STATUS_CANCELLED, cancelledAt = now))
    }

    // ---------------- expenses ----------------

    fun addExpense(description: String, amountCentavos: Long) {
        viewModelScope.launch {
            try {
                val ok: Boolean = db.withTransaction {
                    val day = dayDao.getOpen() ?: return@withTransaction false
                    expenseDao.insert(
                        Expense(
                            businessDayId = day.id,
                            description = description.trim(),
                            amountCentavos = amountCentavos,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    true
                }
                say(if (ok) "Expense added" else "Start a business day before adding expenses")
            } catch (e: Exception) {
                say("Could not add expense: ${e.message}")
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                val ok: Boolean = db.withTransaction {
                    val day = dayDao.getById(expense.businessDayId)
                    if (day == null || day.closedAt != null) return@withTransaction false
                    expenseDao.delete(expense)
                    true
                }
                say(if (ok) "Expense deleted" else "Expenses of closed days cannot be deleted")
            } catch (e: Exception) {
                say("Could not delete expense: ${e.message}")
            }
        }
    }

    // ---------------- delivery fee reference ----------------

    fun addRate(name: String, feeCentavos: Long, note: String) {
        viewModelScope.launch {
            try {
                rateDao.insert(DeliveryRate(name = name.trim(), feeCentavos = feeCentavos, note = note.trim()))
            } catch (e: Exception) {
                say("Could not add rate: ${e.message}")
            }
        }
    }

    fun updateRate(rate: DeliveryRate) {
        viewModelScope.launch {
            try {
                rateDao.update(rate)
                say("Delivery fee updated")
            } catch (e: Exception) {
                say("Could not update rate: ${e.message}")
            }
        }
    }

    fun deleteRate(rate: DeliveryRate) {
        viewModelScope.launch {
            try {
                rateDao.delete(rate)
            } catch (e: Exception) {
                say("Could not delete rate: ${e.message}")
            }
        }
    }

    // ---------------- business days ----------------

    fun startDay(
        openingFloat: Long,
        openingCash: Long,
        targetUnits: Int,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            var ok = false
            try {
                ok = db.withTransaction {
                    if (dayDao.getOpen() != null) {
                        false // a day is already open: never create a second one
                    } else {
                        dayDao.insert(
                            BusinessDay(
                                openedAt = System.currentTimeMillis(),
                                openingFloatCentavos = openingFloat,
                                openingCashCentavos = openingCash,
                                targetUnits = targetUnits
                            )
                        )
                        true
                    }
                }
                say(if (ok) "New business day started" else "A business day is already open")
            } catch (e: Exception) {
                say("Could not start new day: ${e.message}")
            }
            onResult(ok)
        }
    }

    fun closeDay(dayId: Long, finalFloat: Long, finalCash: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            var ok = false
            try {
                ok = db.withTransaction {
                    val day = dayDao.getById(dayId)
                    if (day == null || day.closedAt != null) {
                        false
                    } else {
                        dayDao.update(
                            day.copy(
                                closedAt = System.currentTimeMillis(),
                                finalFloatCentavos = finalFloat,
                                finalCashCentavos = finalCash
                            )
                        )
                        true
                    }
                }
                say(if (ok) "Business day closed" else "That day is already closed")
            } catch (e: Exception) {
                say("Could not close day: ${e.message}")
            }
            onResult(ok)
        }
    }

    fun updateFinals(dayId: Long, finalFloat: Long, finalCash: Long) {
        viewModelScope.launch {
            try {
                db.withTransaction {
                    val day = dayDao.getById(dayId)
                    if (day != null && day.closedAt != null) {
                        dayDao.update(
                            day.copy(finalFloatCentavos = finalFloat, finalCashCentavos = finalCash)
                        )
                    }
                }
                say("Final amounts updated")
            } catch (e: Exception) {
                say("Could not update: ${e.message}")
            }
        }
    }

    // ---------------- products ----------------

    fun addProduct(name: String, priceCentavos: Long, profitCentavos: Long, stock: Int) {
        viewModelScope.launch {
            try {
                productDao.insert(
                    Product(
                        name = name.trim(),
                        priceCentavos = priceCentavos,
                        profitCentavos = profitCentavos,
                        stock = stock
                    )
                )
            } catch (e: Exception) {
                say("Could not add product: ${e.message}")
            }
        }
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            try {
                productDao.update(product)
            } catch (e: Exception) {
                say("Could not update product: ${e.message}")
            }
        }
    }

    /** History is safe: transactions keep their own name/price/profit snapshot. */
    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            try {
                productDao.delete(product)
                say("Deleted ${product.name}")
            } catch (e: Exception) {
                say("Could not delete product: ${e.message}")
            }
        }
    }

    // ---------------- export ----------------

    fun export(uri: Uri, scope: ExportScope) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val days: List<BusinessDay> = when (scope) {
                        ExportScope.Today -> listOfNotNull(dayDao.getOpen() ?: dayDao.getLatest())
                        is ExportScope.Day -> listOfNotNull(dayDao.getById(scope.dayId))
                        ExportScope.All -> dayDao.getAll()
                    }
                    if (days.isEmpty()) throw IOException("There are no business days to export")
                    val ids = days.map { it.id }.toSet()
                    val txns = txDao.getAll().filter { it.businessDayId in ids }
                    val orders = orderDao.getAll().filter { it.businessDayId in ids }
                    val expenses = expenseDao.getAll().filter { it.businessDayId in ids }
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "w")
                        ?: throw IOException("Could not open the chosen file")
                    XlsxExporter.write(out, days, txns, orders, expenses)
                }
                say("Excel file saved")
            } catch (e: Exception) {
                say("Export failed: ${e.message}")
            }
        }
    }
}
