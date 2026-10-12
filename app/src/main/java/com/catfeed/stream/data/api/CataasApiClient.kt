package com.catfeed.stream.data.api

import com.catfeed.stream.data.model.CatItem
import com.catfeed.stream.data.model.CataasResponse
import com.catfeed.stream.data.model.FilterMode
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class CataasApiClient {

    private val gson = Gson()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val popularTags = listOf(
        "cute", "sleepy", "kitten", "playful", "fluffy", "funny",
        "whiskers", "paws", "tabby", "calico", "black", "white", "orange"
    )

    suspend fun fetchCatBatch(mode: FilterMode, count: Int = 8): List<CatItem> = coroutineScope {
        val deferredList = (1..count).map {
            async(Dispatchers.IO) {
                fetchSingleCat(mode)
            }
        }
        deferredList.awaitAll().filterNotNull()
    }

    private suspend fun fetchSingleCat(mode: FilterMode): CatItem? = withContext(Dispatchers.IO) {
        val requestUrl = when (mode) {
            FilterMode.GIFS -> "https://cataas.com/cat/gif?json=true"
            FilterMode.PHOTOS -> "https://cataas.com/cat?json=true"
            FilterMode.ALL, FilterMode.LIKED -> {
                if (Random.nextFloat() < 0.35f) {
                    "https://cataas.com/cat/gif?json=true"
                } else {
                    "https://cataas.com/cat?json=true"
                }
            }
        }

        try {
            val request = Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "PurrFeed/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val parsed = gson.fromJson(bodyString, CataasResponse::class.java)
                        val id = parsed.getEffectiveId().ifBlank { UUID.randomUUID().toString() }
                        val isGif = parsed.isAnimatedGif() || requestUrl.contains("gif")
                        val fullUrl = if (id.isNotBlank()) "https://cataas.com/cat/$id" else requestUrl.replace("?json=true", "")
                        val tags = parsed.tags?.filter { it.isNotBlank() } ?: getRandomTags()

                        return@withContext CatItem(
                            id = id,
                            url = fullUrl,
                            isGif = isGif,
                            tags = if (tags.isEmpty()) getRandomTags() else tags
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Silently fall back to a random seed direct URL so user never suffers network interruptions
        }

        // Resilient fallback item
        val fallbackId = UUID.randomUUID().toString().take(8)
        val isGif = (mode == FilterMode.GIFS) || (mode == FilterMode.ALL && Random.nextFloat() < 0.3f)
        val fallbackUrl = if (isGif) {
            "https://cataas.com/cat/gif?t=${System.currentTimeMillis()}_$fallbackId"
        } else {
            "https://cataas.com/cat?t=${System.currentTimeMillis()}_$fallbackId"
        }

        CatItem(
            id = "cat_$fallbackId",
            url = fallbackUrl,
            isGif = isGif,
            tags = getRandomTags()
        )
    }

    private fun getRandomTags(): List<String> {
        val count = Random.nextInt(1, 4)
        return popularTags.shuffled().take(count)
    }
}
