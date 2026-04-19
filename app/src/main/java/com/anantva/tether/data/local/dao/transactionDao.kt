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
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY date ASC")
    fun observePendingTransactions(): Flow<List<TransactionEntity>>

    @Query("DELETE FROM transactions WHERE transactionId = :id AND status = 'PENDING'")
    suspend fun deletePendingTransaction(id: Long)

    @Query("SELECT * FROM transactions WHERE transactionId = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

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
        SELECT COALESCE(SUM(amount), 0)
        FROM transactions
        WHERE status = 'CONFIRMED'
          AND type = 'Expense'
          AND date BETWEEN :startOfDay AND :endOfDay
        """
    )
    fun observeDailyExpenseSpent(startOfDay: Long, endOfDay: Long): Flow<Double?>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0)
        FROM transactions
        WHERE status = 'CONFIRMED'
          AND type = 'Expense'
          AND date BETWEEN :startOfDay AND :endOfDay
        """
    )
    suspend fun getExpenseSpentValue(startOfDay: Long, endOfDay: Long): Double

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
}
