package com.shoptracker.pos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Product::class,
        BusinessDay::class,
        SalesTransaction::class,
        SalesOrder::class,
        Expense::class,
        DeliveryRate::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun businessDayDao(): BusinessDayDao
    abstract fun transactionDao(): TransactionDao
    abstract fun orderDao(): OrderDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun rateDao(): RateDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 -> v2: adds profit/stock, orders, expenses and delivery fee reference. Keeps all data. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN profitCentavos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN stock INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sales_transactions ADD COLUMN unitProfitCentavos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sales_transactions ADD COLUMN orderId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS orders (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "businessDayId INTEGER NOT NULL, " +
                        "customerName TEXT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "address TEXT NOT NULL, " +
                        "deliveryFeeCentavos INTEGER NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "cancelledAt INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_businessDayId ON orders (businessDayId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS expenses (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "businessDayId INTEGER NOT NULL, " +
                        "description TEXT NOT NULL, " +
                        "amountCentavos INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_businessDayId ON expenses (businessDayId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS delivery_rates (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "feeCentavos INTEGER NOT NULL, " +
                        "note TEXT NOT NULL)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shop_tracker.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
