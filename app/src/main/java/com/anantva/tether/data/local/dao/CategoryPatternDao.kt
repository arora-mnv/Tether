package com.anantva.tether.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.anantva.tether.data.local.entity.CategoryPatternEntity

@Dao
interface CategoryPatternDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: CategoryPatternEntity)

    @Query("SELECT * FROM category_patterns WHERE keyword = :keyword AND isGlobal = :isGlobal LIMIT 1")
    suspend fun getPattern(keyword: String, isGlobal: Boolean): CategoryPatternEntity?

    @Query("SELECT * FROM category_patterns WHERE isGlobal = :isGlobal ORDER BY count DESC")
    suspend fun getAllPatterns(isGlobal: Boolean): List<CategoryPatternEntity>

    @Query("SELECT * FROM category_patterns WHERE keyword LIKE '%' || :keyword || '%' AND isGlobal = :isGlobal ORDER BY count DESC")
    suspend fun searchPatterns(keyword: String, isGlobal: Boolean): List<CategoryPatternEntity>

    @Update
    suspend fun updatePattern(pattern: CategoryPatternEntity)

    @Query("DELETE FROM category_patterns WHERE isGlobal = 1")
    suspend fun clearGlobalPatterns()

    @Query("DELETE FROM category_patterns WHERE isGlobal = 0")
    suspend fun clearLocalPatterns()
}
