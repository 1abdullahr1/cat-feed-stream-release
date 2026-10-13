package com.catfeed.stream.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.catfeed.stream.data.model.CatItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shares a cat as an actual image/GIF file attachment (not a plain link).
 * Uses FileProvider for secure URI sharing.
 */
object CatShareHelper {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun shareImage(
        context: Context,
        cat: CatItem,
        bufferManager: CatBufferManager
    ) = withContext(Dispatchers.IO) {
        val ext = if (cat.isGif) ".gif" else ".jpg"
        val mimeType = if (cat.isGif) "image/gif" else "image/jpeg"
        val shareFile = File(context.cacheDir, "share_cat_${cat.id.take(8)}$ext")

        // Prefer local buffer file for instant sharing
        val localFile = bufferManager.getLocalFile(cat.id)
        if (localFile != null && localFile.exists()) {
            localFile.copyTo(shareFile, overwrite = true)
        } else {
            // Download to share cache
            val ok = downloadToFile(cat.url, shareFile)
            if (!ok) return@withContext
        }

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, shareFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent, "Share Kitty"))
        }
    }

    private fun downloadToFile(url: String, dest: File): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PurrPlay/1.0")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val bytes = response.body?.bytes() ?: return false
                dest.writeBytes(bytes)
                true
            }
        } catch (_: Exception) {
            dest.delete()
            false
        }
    }
}
