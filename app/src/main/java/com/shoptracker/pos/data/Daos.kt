package com.shoptracker.pos.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): Product?

    @Insert
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)
}

@Dao
interface BusinessDayDao {
    @Query("SELECT * FROM business_days WHERE closedAt IS NULL ORDER BY id DESC LIMIT 1")
    fun observeOpen(): Flow<BusinessDay?>

    @Query("SELECT * FROM business_days WHERE closedAt IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getOpen(): BusinessDay?

    @Query("SELECT * FROM business_days ORDER BY id DESC")
    fun observeAll(): Flow<List<BusinessDay>>

    @Query("SELECT * FROM business_days ORDER BY id")
    suspend fun getAll(): List<BusinessDay>

    @Query("SELECT * FROM business_days ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): BusinessDay?

    @Query("SELECT * FROM business_days WHERE id = :id")
    suspend fun getById(id: Long): BusinessDay?

    @Insert
    suspend fun insert(day: BusinessDay): Long

    @Update
    suspend fun update(day: BusinessDay)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM sales_transactions WHERE businessDayId = :dayId ORDER BY timestamp, id")
    fun observeForDay(dayId: Long): Flow<List<SalesTransaction>>

    @Query("SELECT * FROM sales_transactions WHERE businessDayId = :dayId ORDER BY timestamp, id")
    suspend fun getForDay(dayId: Long): List<SalesTransaction>

    @Query("SELECT * FROM sales_transactions ORDER BY timestamp, id")
    suspend fun getAll(): List<SalesTransaction>

    @Query("SELECT * FROM sales_transactions WHERE businessDayId = :dayId AND status = 'COMPLETED' ORDER BY id DESC LIMIT 1")
    suspend fun getLastCompleted(dayId: Long): SalesTransaction?

    @Query("UPDATE sales_transactions SET status = 'UNDONE', undoneAt = :time WHERE id = :id AND status = 'COMPLETED'")
    suspend fun markUndone(id: Long, time: Long): Int

    @Insert
    suspend fun insert(tx: SalesTransaction): Long

    @Query("SELECT productId AS productId, SUM(quantity) AS units, SUM(totalCentavos) AS revenue FROM sales_transactions WHERE status = 'COMPLETED' GROUP BY productId")
    fun observeLifetime(): Flow<List<ProductTotal>>

    @Query("SELECT businessDayId AS dayId, SUM(quantity) AS units, SUM(totalCentavos) AS revenue FROM sales_transactions WHERE status = 'COMPLETED' GROUP BY businessDayId")
    fun observeDayTotals(): Flow<List<DayTotal>>
}
