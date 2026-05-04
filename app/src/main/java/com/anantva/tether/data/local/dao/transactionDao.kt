package com.anantva.tether.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.anantva.tether.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE transactionId = :id LIMIT 1)")
    suspend fun exists(id: Long): Boolean

    @Query("SELECT * FROM transactions WHERE transactionId = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY date DESC")
    fun observePendingTransactions(): Flow<List<TransactionEntity>>

    @Query("DELETE FROM transactions WHERE transactionId = :id AND status = 'PENDING'")
    suspend fun deletePendingTransaction(id: Long)

    @Query(
        """
        SELECT COALESCE(
            SUM(
                CASE
                    WHEN type = 'Expense' THEN amount
                    WHEN type = 'Credit' THEN -amount
                    ELSE 0
                END
            ),
            0
        )
        FROM transactions
        WHERE status = 'CONFIRMED'
          AND date BETWEEN :startOfDay AND :endOfDay
        """
    )
    fun getDailyNetSpent(startOfDay: Long, endOfDay: Long): Flow<Double?>

    @Query(
        """
        SELECT COALESCE(CAST(SUM(amount) AS INTEGER), 0)
        FROM transactions
        WHERE status = 'CONFIRMED'
          AND type = 'Expense'
          AND date BETWEEN :startOfDay AND :endOfDay
        """
    )
    fun observeDailyExpenseSpent(startOfDay: Long, endOfDay: Long): Flow<Int?>

    @Query(
        """
        SELECT COALESCE(CAST(SUM(amount) AS INTEGER), 0)
        FROM transactions
        WHERE status = 'CONFIRMED'
          AND type = 'Expense'
          AND txnCategory = 'NORMAL'
          AND date BETWEEN :startOfDay AND :endOfDay
        """
    )
    suspend fun getStreakRelevantSpent(startOfDay: Long, endOfDay: Long): Int

    @Query(
        """
        SELECT COALESCE(CAST(SUM(amount) AS INTEGER), 0)
        FROM transactions
        WHERE status = 'CONFIRMED'
          AND type = 'Expense'
          AND date BETWEEN :startOfDay AND :endOfDay
        """
    )
    suspend fun getExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM transactions
        WHERE status = 'CONFIRMED'
          AND date BETWEEN :startOfDay AND :endOfDay
        """
    )
    suspend fun getConfirmedTransactionCount(startOfDay: Long, endOfDay: Long): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("SELECT * FROM transactions WHERE status = 'CONFIRMED' ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = 'CONFIRMED' ORDER BY date DESC")
    suspend fun getAllConfirmedTransactions(): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE status = 'CONFIRMED'")
    suspend fun deleteAllConfirmedTransactions()

    @Query("DELETE FROM transactions WHERE transactionId = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query(
        """
        SELECT COALESCE(CAST(SUM(amount) AS INTEGER), 0)
        FROM transactions
        WHERE status = 'CONFIRMED'
          AND type = 'Expense'
          AND txnCategory = 'NORMAL'
          AND date BETWEEN :startOfDay AND :endOfDay
        """
    )
    suspend fun getDiscretionarySpend(startOfDay: Long, endOfDay: Long): Int

    @Query(
        """
        SELECT COALESCE(CAST(SUM(amount) AS INTEGER), 0)
        FROM transactions
        WHERE status = 'CONFIRMED'
          AND type = 'Expense'
          AND spendNature = 'WANT'
          AND date BETWEEN :startOfDay AND :endOfDay
        """
    )
    suspend fun getWantSpend(startOfDay: Long, endOfDay: Long): Int

    @Query(
        """
        SELECT COALESCE(CAST(SUM(amount) AS INTEGER), 0)
        FROM transactions
        WHERE status = 'CONFIRMED'
          AND type = 'Expense'
          AND spendNature = 'NEED'
          AND date BETWEEN :startOfDay AND :endOfDay
        """
    )
    suspend fun getNeedSpend(startOfDay: Long, endOfDay: Long): Int

    @Query(
        """
        SELECT category, COALESCE(CAST(SUM(amount) AS INTEGER), 0) as total
        FROM transactions
        WHERE status = 'CONFIRMED'
          AND type = 'Expense'
          AND date BETWEEN :startOfDay AND :endOfDay
        GROUP BY category
        ORDER BY total DESC
        """
    )
    suspend fun getCategoryBreakdown(startOfDay: Long, endOfDay: Long): List<CategorySpend>

    @Query(
        """
        SELECT COALESCE(CAST(SUM(amount) AS INTEGER), 0)
        FROM transactions
        WHERE status = 'CONFIRMED'
          AND type = 'Expense'
          AND txnCategory = 'NORMAL'
          AND date BETWEEN :startOfDay AND :endOfDay
        """
    )
    suspend fun getNormalExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int
}

data class CategorySpend(
    val category: String,
    val total: Int
)
