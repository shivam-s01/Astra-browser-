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
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.astra.browser.data.local.entity.DownloadEntity
import com.astra.browser.data.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Live progress for one download, keyed by our DB row id. */
data class DownloadProgress(
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long
) {
    /** 0f..1f, or -1f when the total size is unknown (indeterminate bar). */
    val fraction: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else -1f
}

/**
 * Delegates the actual transfer to the platform DownloadManager (real
 * background download, survives app death, shows the system notification)
 * and exposes live progress so the UI can animate it like Chrome does.
 *
 * Polling only runs WHILE something is downloading and stops as soon as
 * everything finishes, so it costs nothing when idle (no battery/heat).
 */
@Singleton
class AstraDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository
) {
    private val systemDownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as SystemDownloadManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _progress = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    /** dbId -> live progress, for downloads currently in flight. */
    val progress: StateFlow<Map<Long, DownloadProgress>> = _progress

    private var pollerRunning = false

    init {
        registerCompletionReceiver()
    }

    private fun registerCompletionReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val systemId = intent.getLongExtra(SystemDownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (systemId == -1L) return
                scope.launch { syncOne(systemId) }
            }
        }
        val filter = IntentFilter(SystemDownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    /** Copies DownloadManager's real status for [systemId] into our DB row. */
    private suspend fun syncOne(systemId: Long) {
        val status = queryStatus(systemId) ?: return
        val entity = downloadRepository.getBySystemId(systemId) ?: return
        val finished = status.status == "COMPLETED" || status.status == "FAILED"
        downloadRepository.update(
            entity.copy(
                status = status.status,
                localPath = status.localUri ?: entity.localPath,
                totalBytes = if (status.totalBytes > 0) status.totalBytes else entity.totalBytes,
                downloadedBytes = status.downloadedBytes,
                completedAt = if (finished) System.currentTimeMillis() else entity.completedAt
            )
        )
    }

    /**
     * Starts a download. blob:/data: URLs can't be fetched by the system
     * service (it has no access to the page's memory), so those are handled
     * separately by [saveDataUrl].
     */
    suspend fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ): Long {
        if (url.startsWith("data:")) return saveDataUrl(url, mimeType)

        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()

        val request = SystemDownloadManager.Request(url.toUri()).apply {
            if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
            // addRequestHeader throws on a null value -> guard both.
            if (!cookie.isNullOrBlank()) addRequestHeader("Cookie", cookie)
            if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
            setDescription("Downloading via Astra Browser")
            setTitle(fileName)
            setNotificationVisibility(SystemDownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val systemId = systemDownloadManager.enqueue(request)

        val dbId = downloadRepository.insert(
            DownloadEntity(
                fileName = fileName,
                url = url,
                mimeType = mimeType,
                localPath = null,
                status = "RUNNING",
                systemDownloadId = systemId
            )
        )
        ensurePolling()
        return dbId
    }

    /** Saves a base64 data: URL straight into the public Downloads folder. */
    private suspend fun saveDataUrl(dataUrl: String, mimeType: String?): Long {
        val comma = dataUrl.indexOf(',')
        if (comma == -1) return -1L
        val header = dataUrl.substring(5, comma) // after "data:"
        val body = dataUrl.substring(comma + 1)
        val isBase64 = header.endsWith(";base64")
        val mime = mimeType ?: header.substringBefore(';').ifBlank { "application/octet-stream" }

        val bytes = try {
            if (isBase64) android.util.Base64.decode(body, android.util.Base64.DEFAULT)
            else Uri.decode(body).toByteArray()
        } catch (e: Exception) {
            return -1L
        }

        val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        val fileName = "astra_${System.currentTimeMillis()}.$ext"
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeBytes(bytes)

        return downloadRepository.insert(
            DownloadEntity(
                fileName = fileName,
                url = "data:",
                mimeType = mime,
                localPath = Uri.fromFile(file).toString(),
                totalBytes = bytes.size.toLong(),
                downloadedBytes = bytes.size.toLong(),
                status = "COMPLETED",
                completedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * One coroutine that ticks every 500ms only while at least one download
     * is RUNNING/PENDING/PAUSED, then exits. No timers when idle.
     */
    private fun ensurePolling() {
        if (pollerRunning) return
        pollerRunning = true
        scope.launch {
            try {
                while (true) {
                    val live = mutableMapOf<Long, DownloadProgress>()
                    val active = downloadRepository.getActive()
                    if (active.isEmpty()) break

                    for (entity in active) {
                        val sysId = entity.systemDownloadId ?: continue
                        val st = queryStatus(sysId)
                        if (st == null) {
                            // Removed from the system (cancelled by the user
                            // from the notification shade).
                            downloadRepository.update(entity.copy(status = "CANCELLED"))
                            continue
                        }
                        live[entity.id] = DownloadProgress(st.status, st.downloadedBytes, st.totalBytes)
                        if (st.status == "COMPLETED" || st.status == "FAILED") {
                            syncOne(sysId)
                        }
                    }
                    _progress.value = live
                    delay(500)
                }
            } finally {
                _progress.value = emptyMap()
                pollerRunning = false
            }
        }
    }

    /** Call when the Downloads screen opens, to resume tracking anything in flight. */
    fun resumeTracking() = ensurePolling()

    fun queryStatus(systemDownloadId: Long): DownloadStatus? {
        val query = SystemDownloadManager.Query().setFilterById(systemDownloadId)
        systemDownloadManager.query(query).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) return null
            val statusIdx = cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_STATUS)
            val doneIdx = cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIdx = cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val uriIdx = cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_LOCAL_URI)

            val status = when (cursor.getInt(statusIdx)) {
                SystemDownloadManager.STATUS_SUCCESSFUL -> "COMPLETED"
                SystemDownloadManager.STATUS_FAILED -> "FAILED"
                SystemDownloadManager.STATUS_RUNNING -> "RUNNING"
                SystemDownloadManager.STATUS_PAUSED -> "PAUSED"
                else -> "PENDING"
            }

            return DownloadStatus(
                status = status,
                downloadedBytes = cursor.getLong(doneIdx),
                totalBytes = cursor.getLong(totalIdx),
                localUri = cursor.getString(uriIdx)
            )
        }
    }

    fun cancelDownload(systemDownloadId: Long) {
        systemDownloadManager.remove(systemDownloadId)
    }

    fun openDownloadedFile(uri: Uri, mimeType: String?): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}

data class DownloadStatus(
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val localUri: String?
)
