package com.anantva.tether.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anantva.tether.data.local.entity.MerchantPatternEntity

@Dao
interface MerchantPatternDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPattern(pattern: MerchantPatternEntity)

    @Query("SELECT * FROM merchant_patterns WHERE normalizedMerchant = :merchant LIMIT 1")
    suspend fun getPattern(merchant: String): MerchantPatternEntity?

    @Query("SELECT * FROM merchant_patterns ORDER BY usageCount DESC, confidenceScore DESC")
    suspend fun getAllPatterns(): List<MerchantPatternEntity>

    @Query("DELETE FROM merchant_patterns WHERE normalizedMerchant = :merchant")
    suspend fun deletePattern(merchant: String)

    @Query("DELETE FROM merchant_patterns")
    suspend fun deleteAll()
}
