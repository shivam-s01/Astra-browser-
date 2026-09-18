package com.astra.browser.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.astra.browser.data.local.dao.*
import com.astra.browser.data.local.entity.*

@Database(
    entities = [
        HistoryEntity::class,
        BookmarkEntity::class,
        BookmarkFolderEntity::class,
        DownloadEntity::class,
        SitePermissionEntity::class,
        ClosedTabEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AstraDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadDao(): DownloadDao
    abstract fun sitePermissionDao(): SitePermissionDao
    abstract fun closedTabDao(): ClosedTabDao
}
