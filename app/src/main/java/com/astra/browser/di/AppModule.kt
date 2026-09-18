package com.astra.browser.di

import android.content.Context
import androidx.room.Room
import com.astra.browser.data.local.AstraDatabase
import com.astra.browser.data.local.dao.*
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
    fun provideDatabase(@ApplicationContext context: Context): AstraDatabase =
        Room.databaseBuilder(context, AstraDatabase::class.java, "astra.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideHistoryDao(db: AstraDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideBookmarkDao(db: AstraDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideDownloadDao(db: AstraDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideSitePermissionDao(db: AstraDatabase): SitePermissionDao = db.sitePermissionDao()

    @Provides
    fun provideClosedTabDao(db: AstraDatabase): ClosedTabDao = db.closedTabDao()
}
