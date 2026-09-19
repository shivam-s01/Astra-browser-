package com.astra.browser.data.local.dao

import androidx.room.*
import com.astra.browser.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC")
    fun search(query: String): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history WHERE visitedAt >= :since")
    suspend fun deleteSince(since: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE folderId = :folderId ORDER BY sortOrder ASC")
    fun observeByFolder(folderId: Long?): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    suspend fun isBookmarked(url: String): Boolean

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertFolder(folder: BookmarkFolderEntity): Long

    @Query("SELECT * FROM bookmark_folders ORDER BY name ASC")
    fun observeFolders(): Flow<List<BookmarkFolderEntity>>
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Insert
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE systemDownloadId = :systemDownloadId LIMIT 1")
    suspend fun getBySystemId(systemDownloadId: Long): DownloadEntity?
}

@Dao
interface SitePermissionDao {
    @Query("SELECT * FROM site_permissions WHERE origin = :origin")
    fun observeForOrigin(origin: String): Flow<List<SitePermissionEntity>>

    @Query("SELECT * FROM site_permissions WHERE origin = :origin AND permissionType = :type LIMIT 1")
    suspend fun getPermission(origin: String, type: String): SitePermissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(permission: SitePermissionEntity)

    @Query("DELETE FROM site_permissions WHERE origin = :origin")
    suspend fun resetForOrigin(origin: String)

    @Query("SELECT DISTINCT origin FROM site_permissions")
    fun observeAllOrigins(): Flow<List<String>>
}

@Dao
interface ClosedTabDao {
    @Query("SELECT * FROM closed_tabs ORDER BY closedAt DESC LIMIT 20")
    fun observeRecentlyClosed(): Flow<List<ClosedTabEntity>>

    @Insert
    suspend fun insert(tab: ClosedTabEntity): Long

    @Query("DELETE FROM closed_tabs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
