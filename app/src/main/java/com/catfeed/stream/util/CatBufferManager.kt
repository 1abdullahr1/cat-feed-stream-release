package com.catfeed.stream.util

import android.content.Context
import com.catfeed.stream.data.api.CataasApiClient
import com.catfeed.stream.data.db.BufferedCatDao
import com.catfeed.stream.data.db.BufferedCatEntity
import com.catfeed.stream.data.model.FilterMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Keeps ~50 cat images/GIFs cached on disk so the feed is always instant.
 * Strategy: fetch if below 50, prune oldest non-liked down to 45 if above 55.
 */
class CatBufferManager(
    private val context: Context,
    private val apiClient: CataasApiClient,
    private val bufferedCatDao: BufferedCatDao
) {

    private val bufferDir: File by lazy {
        File(context.filesDir, "cat_buffer").also { it.mkdirs() }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        const val TARGET_BUFFER = 50
        const val PRUNE_THRESHOLD = 55
        const val PRUNE_TARGET = 45
    }

    suspend fun replenishBuffer() = withContext(Dispatchers.IO) {
        val currentCount = bufferedCatDao.getCount()

        // Prune if overfilled
        if (currentCount > PRUNE_THRESHOLD) {
            val excess = currentCount - PRUNE_TARGET
            bufferedCatDao.deleteOldestNonLiked(excess)
            pruneOrphanedFiles()
        }

        val countAfterPrune = bufferedCatDao.getCount()
        if (countAfterPrune >= TARGET_BUFFER) return@withContext

        val needed = TARGET_BUFFER - countAfterPrune
        val batchSize = minOf(needed, 15)

        try {
            val fetched = apiClient.fetchCatBatch(FilterMode.ALL, batchSize)
            for (cat in fetched) {
                val ext = if (cat.isGif) ".gif" else ".jpg"
                val file = File(bufferDir, "${cat.id}$ext")
                if (file.exists()) continue
                val downloaded = downloadToFile(cat.url, file)
                if (downloaded) {
                    bufferedCatDao.insert(
                        BufferedCatEntity(
                            id = cat.id,
                            localFilePath = file.absolutePath,
                            originalUrl = cat.url,
                            isGif = cat.isGif,
                            tagsString = cat.tags.joinToString(",")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Network unavailable — silently skip, will retry next launch
        }
    }

    private fun downloadToFile(url: String, dest: File): Boolean {
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "PurrPlay/1.0").build()
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

    /**
     * Deletes files on disk that no longer have a matching DB record.
     */
    private suspend fun pruneOrphanedFiles() {
        val dbIds = bufferedCatDao.getAllBuffered().map { it.id }.toSet()
        bufferDir.listFiles()?.forEach { file ->
            val fileId = file.nameWithoutExtension
            if (fileId !in dbIds) {
                file.delete()
            }
        }
    }

    /**
     * Returns local file for a given cat ID, or null if not in buffer.
     */
    suspend fun getLocalFile(catId: String): File? = withContext(Dispatchers.IO) {
        val entity = bufferedCatDao.getAllBuffered().find { it.id == catId } ?: return@withContext null
        val file = File(entity.localFilePath)
        if (file.exists()) file else null
    }
}
