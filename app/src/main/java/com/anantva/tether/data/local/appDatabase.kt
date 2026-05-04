package com.anantva.tether.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.anantva.tether.data.local.dao.CategoryCorrectionDao
import com.anantva.tether.data.local.dao.GoalDao
import com.anantva.tether.data.local.dao.TransactionDao
import com.anantva.tether.data.local.dao.UserProfileDao
import com.anantva.tether.data.local.entity.CategoryCorrectionEntity
import com.anantva.tether.data.local.entity.GoalEntity
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.local.entity.UserProfileEntity

// We must list every Entity here so Room knows what tables to create
@Database(
    entities = [
        UserProfileEntity::class,
        TransactionEntity::class,
        GoalEntity::class,
        CategoryCorrectionEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // These connect the queries (DAOs) to the database
    abstract fun userProfileDao(): UserProfileDao
    abstract fun goalDao(): GoalDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryCorrectionDao(): CategoryCorrectionDao

    companion object {
        // @Volatile ensures this variable is instantly updated across all threads
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS category_corrections (" +
                    "merchantKey TEXT NOT NULL PRIMARY KEY, " +
                    "category TEXT NOT NULL)"
                )
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasStatusColumn = db.query("PRAGMA table_info(transactions)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    var found = false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "status") {
                            found = true
                            break
                        }
                    }
                    found
                }

                if (!hasStatusColumn) {
                    db.execSQL(
                        "ALTER TABLE transactions ADD COLUMN status TEXT NOT NULL DEFAULT 'CONFIRMED'"
                    )
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = mutableSetOf<String>()
                db.query("PRAGMA table_info(transactions)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        columns.add(cursor.getString(nameIndex))
                    }
                }

                if ("category" !in columns) {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT NOT NULL DEFAULT 'Other'")
                }
                if ("txnCategory" !in columns) {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN txnCategory TEXT NOT NULL DEFAULT 'NORMAL'")
                }
                if ("spendNature" !in columns) {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN spendNature TEXT NOT NULL DEFAULT 'UNKNOWN'")
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            // If the INSTANCE is not null, return it.
            // If it IS null, create the database.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tether_database" // The actual file name on the phone
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}
