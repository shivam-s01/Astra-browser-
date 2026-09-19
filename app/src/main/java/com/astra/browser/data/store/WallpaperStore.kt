package com.astra.browser.data.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Owns the user's custom home-page wallpaper.
 *
 * The picked image is COPIED into app-private storage (downscaled, EXIF
 * rotation applied, re-encoded as JPEG). We never keep the gallery Uri:
 * gallery/photo-picker permissions are temporary, so a stored Uri would
 * silently stop working after a restart, or if the photo is deleted or
 * moved. The private copy always works.
 */
@Singleton
class WallpaperStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file get() = File(context.filesDir, FILE_NAME)

    /** Decoded custom wallpaper (null = none). Reloads only when the version changes. */
    val custom: StateFlow<ImageBitmap?> = settings.wallpaperVersion
        .distinctUntilChanged()
        .map { loadFromDisk() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun importFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        // A huge photo can throw OutOfMemoryError (an Error, NOT an Exception,
        // so plain runCatching would let it crash the app). Treat it as a
        // normal "couldn't use that image" failure instead.
        try {
            importInternal(uri)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun importInternal(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching false

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TARGET_LONG_SIDE) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap: Bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@runCatching false

            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            bitmap = applyOrientation(bitmap, orientation)

            val longSide = max(bitmap.width, bitmap.height)
            if (longSide > MAX_LONG_SIDE) {
                val scale = MAX_LONG_SIDE.toFloat() / longSide
                val scaled = Bitmap.createScaledBitmap(
                    bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
                )
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }

            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bitmap.recycle()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }

            settings.setWallpaperMode("CUSTOM")
            settings.setWallpaperVersion(System.currentTimeMillis())
            true
        }.getOrDefault(false)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { file.delete() }
        settings.setWallpaperMode("NIGHT_SKY")
        settings.setWallpaperVersion(0L)
    }

    private fun loadFromDisk(): ImageBitmap? {
        val f = file
        if (!f.exists()) return null
        return runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull()
    }

    private fun applyOrientation(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (rotated !== src) src.recycle()
        return rotated
    }

    private companion object {
        const val FILE_NAME = "home_wallpaper.jpg"
        const val TARGET_LONG_SIDE = 1920
        const val MAX_LONG_SIDE = 2400
    }
}
