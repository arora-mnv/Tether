package com.anantva.tether.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anantva.tether.data.local.entity.CategoryCorrectionEntity

@Dao
interface CategoryCorrectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrection(correction: CategoryCorrectionEntity)

    @Query("SELECT category FROM category_corrections WHERE merchantKey = :merchantKey LIMIT 1")
    suspend fun getCorrection(merchantKey: String): String?

    @Query("SELECT * FROM category_corrections")
    suspend fun getAllCorrections(): List<CategoryCorrectionEntity>
}
