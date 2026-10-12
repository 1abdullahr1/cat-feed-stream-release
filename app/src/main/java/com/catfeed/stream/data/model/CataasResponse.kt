package com.catfeed.stream.data.model

import com.google.gson.annotations.SerializedName

data class CataasResponse(
    @SerializedName("_id")
    val id: String?,
    @SerializedName("id")
    val idAlt: String?,
    @SerializedName("mimetype")
    val mimetype: String?,
    @SerializedName("tags")
    val tags: List<String>?,
    @SerializedName("url")
    val url: String?
) {
    fun getEffectiveId(): String {
        return id ?: idAlt ?: ""
    }

    fun isAnimatedGif(): Boolean {
        return mimetype?.contains("gif", ignoreCase = true) == true ||
                url?.contains(".gif", ignoreCase = true) == true
    }
}
