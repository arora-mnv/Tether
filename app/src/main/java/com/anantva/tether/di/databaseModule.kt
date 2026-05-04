package com.anantva.tether.di

import android.content.Context
import androidx.room.Room
import com.anantva.tether.data.local.AppDatabase
import com.anantva.tether.data.local.dao.CategoryCorrectionDao
import com.anantva.tether.data.local.dao.GoalDao
import com.anantva.tether.data.local.dao.TransactionDao
import com.anantva.tether.data.local.dao.UserProfileDao
import com.anantva.tether.data.parser.CategoryEngine
import com.anantva.tether.data.repository.FirestoreRepository
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
    fun provideFirebaseAuthManager(): FirebaseAuthManager {
        return FirebaseAuthManager()
    }

    @Provides
    @Singleton
    fun provideRepository(
        db: AppDatabase,
        preferencesRepository: UserPreferencesRepository,
        authManager: FirebaseAuthManager,
        firestoreRepository: FirestoreRepository
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
            categoryEngine        = categoryEngine
        )
    }
}
