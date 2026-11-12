package com.shoptracker.pos

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.shoptracker.pos.data.AppDatabase
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.DayTotal
import com.shoptracker.pos.data.ExportScope
import com.shoptracker.pos.data.Product
import com.shoptracker.pos.data.ProductTotal
import com.shoptracker.pos.data.SalesTransaction
import com.shoptracker.pos.export.XlsxExporter
import com.shoptracker.pos.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    val dayTotals: StateFlow<Map<Long, DayTotal>> =
        txDao.observeDayTotals().map { list -> list.associateBy { it.dayId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayTxns: StateFlow<List<SalesTransaction>> =
        openDay.map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else txDao.observeForDay(id)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun txnsFor(dayId: Long): Flow<List<SalesTransaction>> = txDao.observeForDay(dayId)

    private val _events = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<String> = _events.asSharedFlow()

    private fun say(msg: String) {
        _events.tryEmit(msg)
    }

    // ---------------- sales ----------------

    /** Records one sale of [product] under the open business day, atomically. */
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
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    true
                }
                if (!ok) say("Sale not recorded: no active business day or product disabled")
            } catch (e: Exception) {
                say("Could not save sale: ${e.message}")
            }
        }
    }

    /** Undoes the most recent completed sale of the open business day. Can be repeated. */
    fun undo() {
        viewModelScope.launch {
            try {
                val undone: SalesTransaction? = db.withTransaction {
                    val day = dayDao.getOpen() ?: return@withTransaction null
                    val last = txDao.getLastCompleted(day.id) ?: return@withTransaction null
                    val changed = txDao.markUndone(last.id, System.currentTimeMillis())
                    if (changed == 1) last else null
                }
                if (undone == null) say("Nothing to undo")
                else say("Undid: ${undone.productName} (${Fmt.peso(undone.totalCentavos)})")
            } catch (e: Exception) {
                say("Undo failed: ${e.message}")
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

    fun addProduct(name: String, priceCentavos: Long) {
        viewModelScope.launch {
            try {
                productDao.insert(Product(name = name.trim(), priceCentavos = priceCentavos))
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

    /** History is safe: transactions keep their own name/price snapshot. */
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
                    val txns: List<SalesTransaction> = when (scope) {
                        ExportScope.All -> txDao.getAll()
                        else -> days.flatMap { txDao.getForDay(it.id) }
                    }
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "w")
                        ?: throw IOException("Could not open the chosen file")
                    XlsxExporter.write(out, days, txns)
                }
                say("Excel file saved")
            } catch (e: Exception) {
                say("Export failed: ${e.message}")
            }
        }
    }
}
