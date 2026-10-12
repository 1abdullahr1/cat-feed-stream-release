package com.catfeed.stream.data.model

data class CatItem(
    val id: String,
    val url: String,
    val isGif: Boolean,
    val tags: List<String> = emptyList(),
    val isLiked: Boolean = false,
    val likeCount: Int = 0
)

enum class FilterMode {
    ALL,
    PHOTOS,
    GIFS,
    LIKED
}
