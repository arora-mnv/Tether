package com.anantva.tether.di

import android.content.Context
import com.anantva.tether.data.local.AppDatabase
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
    fun provideRepository(db: AppDatabase): TetherRepository {
        return TetherRepository(
            db.userProfileDao(),
            db.goalDao(),
            db.transactionDao()
        )
    }

}
