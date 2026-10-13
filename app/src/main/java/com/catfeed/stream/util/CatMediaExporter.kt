package com.catfeed.stream.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.catfeed.stream.data.model.CatItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Saves a cat image or GIF to the device gallery (Pictures/PurrPlay).
 * Uses MediaStore on API 29+ for scoped-storage compliance.
 */
object CatMediaExporter {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun saveToGallery(
        context: Context,
        cat: CatItem,
        bufferManager: CatBufferManager
    ): Boolean = withContext(Dispatchers.IO) {
        val imageBytes = getImageBytes(cat, bufferManager) ?: return@withContext false

        val mimeType = if (cat.isGif) "image/gif" else "image/jpeg"
        val ext = if (cat.isGif) ".gif" else ".jpg"
        val displayName = "purr_${cat.id.take(8)}_${System.currentTimeMillis()}$ext"

        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, imageBytes, displayName, mimeType)
        } else {
            saveLegacy(context, imageBytes, displayName)
        }
    }

    private fun getImageBytes(cat: CatItem, bufferManager: CatBufferManager): ByteArray? {
        // Try local buffer first (instant, no network)
        val localFile: File? = try {
            kotlinx.coroutines.runBlocking { bufferManager.getLocalFile(cat.id) }
        } catch (_: Exception) { null }

        if (localFile != null && localFile.exists()) {
            return localFile.readBytes()
        }

        // Fallback: download fresh from network
        return try {
            val request = Request.Builder()
                .url(cat.url)
                .header("User-Agent", "PurrPlay/1.0")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        mimeType: String
    ): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PurrPlay")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values
        ) ?: return false

        return try {
            resolver.openOutputStream(uri)?.use { stream: OutputStream ->
                stream.write(bytes)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        context: Context,
        bytes: ByteArray,
        displayName: String
    ): Boolean {
        return try {
            val purrDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "PurrPlay"
            )
            purrDir.mkdirs()
            val dest = File(purrDir, displayName)
            dest.writeBytes(bytes)
            true
        } catch (_: Exception) {
            false
        }
    }
}
