package com.anantva.tether.di

import android.content.Context
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.AppDatabase
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.repository.TetherRepository
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
    fun provideRepository(
        db: AppDatabase,
        preferencesRepository: UserPreferencesRepository,
        authManager: FirebaseAuthManager
    ): TetherRepository {
        return TetherRepository(
            userProfileDao        = db.userProfileDao(),
            goalDao               = db.goalDao(),
            transactionDao        = db.transactionDao(),
            preferencesRepository = preferencesRepository,
            authManager           = authManager
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideFirebaseAuthManager(): FirebaseAuthManager {
        return FirebaseAuthManager()
    }
}
