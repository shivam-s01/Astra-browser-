package com.astra.browser.core.engine

import android.app.DownloadManager as SystemDownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.astra.browser.data.local.entity.DownloadEntity
import com.astra.browser.data.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delegates actual file transfer to the platform's DownloadManager service —
 * real background downloads with real progress, not a simulated bar. Astra
 * tracks metadata in its own Room table so the Downloads screen can show a
 * unified list even before/after querying the system service.
 */
@Singleton
class AstraDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository
) {
    private val systemDownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as SystemDownloadManager

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        registerCompletionReceiver()
    }

    /**
     * Without this, DownloadEntity.status was written once as "RUNNING"
     * at enqueue time and never updated again -- the Downloads screen
     * would show every download stuck on "Running" forever even after it
     * actually finished successfully in the background. This listens for
     * the system's own completion broadcast and syncs our DB row to match
     * DownloadManager's real, authoritative status.
     */
    private fun registerCompletionReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val systemId = intent.getLongExtra(SystemDownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (systemId == -1L) return
                scope.launch {
                    val status = queryStatus(systemId) ?: return@launch
                    val entity = downloadRepository.getBySystemId(systemId) ?: return@launch
                    downloadRepository.update(
                        entity.copy(
                            status = status.status,
                            localPath = status.localUri
                        )
                    )
                }
            }
        }
        val filter = IntentFilter(SystemDownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    suspend fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?): Long {
        val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
        val cookie = CookieManager.getInstance().getCookie(url)

        val request = SystemDownloadManager.Request(url.toUri()).apply {
            setMimeType(mimeType)
            addRequestHeader("cookie", cookie)
            addRequestHeader("User-Agent", userAgent)
            setDescription("Downloading via Astra Browser")
            setTitle(fileName)
            setNotificationVisibility(SystemDownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
        }

        val systemId = systemDownloadManager.enqueue(request)

        return downloadRepository.insert(
            DownloadEntity(
                fileName = fileName,
                url = url,
                mimeType = mimeType,
                localPath = null,
                status = "RUNNING",
                systemDownloadId = systemId
            )
        )
    }

    fun queryStatus(systemDownloadId: Long): DownloadStatus? {
        val query = SystemDownloadManager.Query().setFilterById(systemDownloadId)
        systemDownloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val statusIdx = cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_STATUS)
            val bytesDownloadedIdx = cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val bytesTotalIdx = cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val localUriIdx = cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_LOCAL_URI)

            val status = when (cursor.getInt(statusIdx)) {
                SystemDownloadManager.STATUS_SUCCESSFUL -> "COMPLETED"
                SystemDownloadManager.STATUS_FAILED -> "FAILED"
                SystemDownloadManager.STATUS_RUNNING -> "RUNNING"
                SystemDownloadManager.STATUS_PAUSED -> "PAUSED"
                else -> "PENDING"
            }

            return DownloadStatus(
                status = status,
                downloadedBytes = cursor.getLong(bytesDownloadedIdx),
                totalBytes = cursor.getLong(bytesTotalIdx),
                localUri = cursor.getString(localUriIdx)
            )
        }
    }

    fun cancelDownload(systemDownloadId: Long) {
        systemDownloadManager.remove(systemDownloadId)
    }

    fun openDownloadedFile(uri: Uri, mimeType: String?): android.content.Intent {
        return android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

data class DownloadStatus(
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val localUri: String?
)
