package com.anantva.tether.di

import android.content.Context
import androidx.room.Room
import com.anantva.tether.data.local.AppDatabase
import com.anantva.tether.data.local.dao.CategoryCorrectionDao
import com.anantva.tether.data.local.dao.GoalDao
import com.anantva.tether.data.local.dao.MerchantPatternDao
import com.anantva.tether.data.local.dao.TransactionDao
import com.anantva.tether.data.local.dao.UserProfileDao
import com.anantva.tether.data.parser.CategoryEngine
import com.anantva.tether.data.parser.MerchantLearningEngine
import com.anantva.tether.data.repository.FirestoreRepository
import com.anantva.tether.data.repository.TransactionDataSourceRouter
import com.anantva.tether.data.repository.TetherRepository
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.UserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideTransactionDao(db: AppDatabase): TransactionDao {
        return db.transactionDao()
    }

    @Provides
    @Singleton
    fun provideGoalDao(db: AppDatabase): GoalDao {
        return db.goalDao()
    }

    @Provides
    @Singleton
    fun provideUserProfileDao(db: AppDatabase): UserProfileDao {
        return db.userProfileDao()
    }

    @Provides
    @Singleton
    fun provideCategoryCorrectionDao(db: AppDatabase): CategoryCorrectionDao {
        return db.categoryCorrectionDao()
    }

    @Provides
    @Singleton
    fun provideMerchantPatternDao(db: AppDatabase): MerchantPatternDao {
        return db.merchantPatternDao()
    }

    @Provides
    @Singleton
    fun provideFirebaseAuthManager(): FirebaseAuthManager {
        return FirebaseAuthManager()
    }

    @Provides
    @Singleton
    fun provideRepository(
        db: AppDatabase,
        preferencesRepository: UserPreferencesRepository,
        authManager: FirebaseAuthManager,
        firestoreRepository: FirestoreRepository,
        transactionDataSource: TransactionDataSourceRouter,
        merchantLearningEngine: MerchantLearningEngine
    ): TetherRepository {
        val categoryEngine = CategoryEngine(db.categoryCorrectionDao())
        return TetherRepository(
            userProfileDao        = db.userProfileDao(),
            goalDao               = db.goalDao(),
            transactionDao        = db.transactionDao(),
            categoryCorrectionDao = db.categoryCorrectionDao(),
            preferencesRepository = preferencesRepository,
            authManager           = authManager,
            firestoreRepository   = firestoreRepository,
            categoryEngine        = categoryEngine,
            transactionDataSource = transactionDataSource,
            merchantLearningEngine = merchantLearningEngine
        )
    }
}
