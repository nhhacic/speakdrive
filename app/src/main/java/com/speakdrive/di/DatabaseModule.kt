package com.speakdrive.di

import android.content.Context
import androidx.room.Room
import com.speakdrive.data.local.ALL_MIGRATIONS
import com.speakdrive.data.local.AppDatabase
import com.speakdrive.data.local.dao.SessionDao
import com.speakdrive.data.local.dao.WordDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "speakdrive_db")
            .addMigrations(*ALL_MIGRATIONS)
            // Version 1 only ever existed on development builds.
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
            .build()

    @Provides
    fun provideSessionDao(database: AppDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideWordDao(database: AppDatabase): WordDao = database.wordDao()
}
