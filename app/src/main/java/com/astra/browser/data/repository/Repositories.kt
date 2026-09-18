package com.astra.browser.data.repository

import com.astra.browser.data.local.dao.*
import com.astra.browser.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(private val dao: HistoryDao) {
    fun observeAll(): Flow<List<HistoryEntity>> = dao.observeAll()
    fun search(query: String): Flow<List<HistoryEntity>> = dao.search(query)
    suspend fun record(url: String, title: String, faviconUrl: String? = null) {
        dao.insert(HistoryEntity(url = url, title = title, faviconUrl = faviconUrl))
    }
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    suspend fun clearAll() = dao.clearAll()
    suspend fun clearSince(since: Long) = dao.deleteSince(since)
}

@Singleton
class BookmarkRepository @Inject constructor(private val dao: BookmarkDao) {
    fun observeAll(): Flow<List<BookmarkEntity>> = dao.observeAll()
    fun observeByFolder(folderId: Long?): Flow<List<BookmarkEntity>> = dao.observeByFolder(folderId)
    fun search(query: String): Flow<List<BookmarkEntity>> = dao.search(query)
    fun observeFolders(): Flow<List<BookmarkFolderEntity>> = dao.observeFolders()
    suspend fun isBookmarked(url: String): Boolean = dao.isBookmarked(url)
    suspend fun add(url: String, title: String, faviconUrl: String? = null, folderId: Long? = null) {
        dao.insert(BookmarkEntity(url = url, title = title, faviconUrl = faviconUrl, folderId = folderId))
    }
    suspend fun update(bookmark: BookmarkEntity) = dao.update(bookmark)
    suspend fun removeByUrl(url: String) = dao.deleteByUrl(url)
    suspend fun removeById(id: Long) = dao.deleteById(id)
    suspend fun createFolder(name: String, parentId: Long? = null): Long =
        dao.insertFolder(BookmarkFolderEntity(name = name, parentFolderId = parentId))
}

@Singleton
class DownloadRepository @Inject constructor(private val dao: DownloadDao) {
    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()
    suspend fun insert(download: DownloadEntity): Long = dao.insert(download)
    suspend fun update(download: DownloadEntity) = dao.update(download)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    suspend fun clearAll() = dao.clearAll()
    suspend fun getById(id: Long): DownloadEntity? = dao.getById(id)
    suspend fun getBySystemId(systemDownloadId: Long): DownloadEntity? = dao.getBySystemId(systemDownloadId)
    suspend fun getActive(): List<DownloadEntity> = dao.getActive()
}

@Singleton
class ClosedTabRepository @Inject constructor(private val dao: ClosedTabDao) {
    fun observeRecentlyClosed(): Flow<List<ClosedTabEntity>> = dao.observeRecentlyClosed()
    suspend fun record(url: String, title: String) {
        dao.insert(ClosedTabEntity(url = url, title = title))
    }
    suspend fun remove(id: Long) = dao.deleteById(id)
}
