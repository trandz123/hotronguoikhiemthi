package com.trandz123.hotronguoikhiemthi.di

import android.content.Context
import androidx.room.Room
import com.trandz123.hotronguoikhiemthi.data.history.AppDatabase
import com.trandz123.hotronguoikhiemthi.data.history.ScanHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "mat_ai.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideScanHistoryDao(db: AppDatabase): ScanHistoryDao = db.scanHistoryDao()
}
