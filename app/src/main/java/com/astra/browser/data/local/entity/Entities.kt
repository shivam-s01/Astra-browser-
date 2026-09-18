package com.astra.browser.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val faviconUrl: String? = null,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val faviconUrl: String? = null,
    val folderId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)

@Entity(tableName = "bookmark_folders")
data class BookmarkFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentFolderId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val url: String,
    val mimeType: String?,
    val localPath: String?,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: String, // PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val systemDownloadId: Long? = null
)

@Entity(tableName = "site_permissions")
data class SitePermissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val origin: String,
    val permissionType: String, // CAMERA, MICROPHONE, LOCATION, NOTIFICATIONS, POPUPS, COOKIES, JAVASCRIPT, AUTOPLAY, CLIPBOARD
    val state: String, // ALLOW, ASK, BLOCK
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "closed_tabs")
data class ClosedTabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val closedAt: Long = System.currentTimeMillis()
)
