package com.catfeed.stream.data.repository

import com.catfeed.stream.data.api.CataasApiClient
import com.catfeed.stream.data.db.LikedCatDao
import com.catfeed.stream.data.db.LikedCatEntity
import com.catfeed.stream.data.model.CatItem
import com.catfeed.stream.data.model.FilterMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class CatFeedRepository(
    private val apiClient: CataasApiClient,
    private val likedCatDao: LikedCatDao
) {
    // Fast cache of liked IDs in memory for instant lookups
    private val likedIdsCache = ConcurrentHashMap.newKeySet<String>()

    suspend fun initLikedCache() = withContext(Dispatchers.IO) {
        val ids = likedCatDao.getAllLikedIds()
        likedIdsCache.clear()
        likedIdsCache.addAll(ids)
    }

    suspend fun fetchMoreCats(mode: FilterMode, count: Int = 8): List<CatItem> = withContext(Dispatchers.IO) {
        val fetched = apiClient.fetchCatBatch(mode, count)
        fetched.map { cat ->
            cat.copy(isLiked = likedIdsCache.contains(cat.id))
        }
    }

    suspend fun toggleLike(cat: CatItem): Boolean = withContext(Dispatchers.IO) {
        val currentlyLiked = likedIdsCache.contains(cat.id)
        if (currentlyLiked) {
            likedCatDao.deleteById(cat.id)
            likedIdsCache.remove(cat.id)
            false
        } else {
            val entity = LikedCatEntity(
                id = cat.id,
                url = cat.url,
                isGif = cat.isGif,
                tagsString = cat.tags.joinToString(",")
            )
            likedCatDao.insertLikedCat(entity)
            likedIdsCache.add(cat.id)
            true
        }
    }

    fun observeLikedCats(): Flow<List<CatItem>> {
        return likedCatDao.getAllLikedCats().map { entities ->
            entities.map { entity ->
                CatItem(
                    id = entity.id,
                    url = entity.url,
                    isGif = entity.isGif,
                    tags = entity.tagsString.split(",").filter { it.isNotBlank() },
                    isLiked = true
                )
            }
        }
    }

    fun isCatLiked(id: String): Boolean {
        return likedIdsCache.contains(id)
    }
}
