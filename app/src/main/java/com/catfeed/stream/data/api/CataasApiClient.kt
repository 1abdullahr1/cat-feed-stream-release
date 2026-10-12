package com.catfeed.stream.data.api

import com.catfeed.stream.data.model.CatItem
import com.catfeed.stream.data.model.CataasResponse
import com.catfeed.stream.data.model.FilterMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class CataasApiClient {

    private val gson = Gson()
    private val pageSkipCounter = AtomicInteger(0)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(15, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val popularTags = listOf(
        "cute", "playful", "kitten", "sleepy", "fluffy", "purr",
        "tabby", "calico", "whiskers", "paws", "sweet", "orange"
    )

    suspend fun fetchCatBatch(mode: FilterMode, count: Int = 10): List<CatItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<CatItem>()

        // 1. First attempt: TheCatAPI CloudFront CDN (sub-100ms global edge delivery)
        try {
            val cdnItems = fetchFromTheCatApi(mode, count)
            if (cdnItems.isNotEmpty()) {
                result.addAll(cdnItems)
            }
        } catch (_: Exception) {
            // Silently proceed to Cataas batch endpoint
        }

        // 2. Second attempt / Supplement: Cataas batch endpoint
        if (result.size < count) {
            try {
                val needed = count - result.size
                val cataasItems = fetchFromCataasBatch(mode, needed)
                result.addAll(cataasItems)
            } catch (_: Exception) {
                // Silently proceed to resilient fallback
            }
        }

        // 3. Fallback: If network is severely constrained, generate direct CDN URLs so user never sees empty screens
        if (result.isEmpty()) {
            result.addAll(generateFallbackBatch(mode, count))
        }

        result
    }

    private fun fetchFromTheCatApi(mode: FilterMode, count: Int): List<CatItem> {
        val urlBuilder = StringBuilder("https://api.thecatapi.com/v1/images/search?limit=").append(count)
        when (mode) {
            FilterMode.GIFS -> urlBuilder.append("&mime_types=gif")
            FilterMode.PHOTOS -> urlBuilder.append("&mime_types=jpg,png")
            FilterMode.ALL, FilterMode.LIKED -> {
                // Return mixed content
            }
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("Accept", "application/json")
            .header("User-Agent", "PurrFeed/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()

            val listType = object : TypeToken<List<CataasResponse>>() {}.type
            val parsedList: List<CataasResponse> = gson.fromJson(body, listType) ?: return emptyList()

            return parsedList.mapNotNull { item ->
                val fullUrl = item.url
                if (fullUrl.isNullOrBlank()) return@mapNotNull null
                val isGif = fullUrl.endsWith(".gif", ignoreCase = true) || item.isAnimatedGif()
                val id = item.getEffectiveId().ifBlank { UUID.randomUUID().toString().take(8) }

                CatItem(
                    id = id,
                    url = fullUrl,
                    isGif = isGif,
                    tags = getRandomTags()
                )
            }
        }
    }

    private fun fetchFromCataasBatch(mode: FilterMode, count: Int): List<CatItem> {
        val skip = pageSkipCounter.getAndAdd(count) % 200
        val urlBuilder = StringBuilder("https://cataas.com/api/cats?limit=").append(count).append("&skip=").append(skip)
        if (mode == FilterMode.GIFS) {
            urlBuilder.append("&tags=gif")
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("Accept", "application/json")
            .header("User-Agent", "PurrFeed/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()

            val listType = object : TypeToken<List<CataasResponse>>() {}.type
            val parsedList: List<CataasResponse> = gson.fromJson(body, listType) ?: return emptyList()

            return parsedList.mapNotNull { item ->
                val id = item.getEffectiveId()
                if (id.isBlank()) return@mapNotNull null
                val isGif = item.isAnimatedGif() || (item.tags?.any { it.equals("gif", ignoreCase = true) } == true)
                val fullUrl = "https://cataas.com/cat/$id"
                val tags = item.tags?.filter { it.isNotBlank() && !it.equals("gif", ignoreCase = true) }

                CatItem(
                    id = id,
                    url = fullUrl,
                    isGif = isGif,
                    tags = if (tags.isNullOrEmpty()) getRandomTags() else tags
                )
            }
        }
    }

    private fun generateFallbackBatch(mode: FilterMode, count: Int): List<CatItem> {
        return (1..count).map {
            val fallbackId = UUID.randomUUID().toString().take(8)
            val isGif = (mode == FilterMode.GIFS) || (mode == FilterMode.ALL && Random.nextFloat() < 0.35f)
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
    }

    private fun getRandomTags(): List<String> {
        val count = Random.nextInt(1, 4)
        return popularTags.shuffled().take(count)
    }
}
