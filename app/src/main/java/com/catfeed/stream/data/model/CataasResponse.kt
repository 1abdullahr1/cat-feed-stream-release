package com.catfeed.stream.data.model

import com.google.gson.annotations.SerializedName

data class CataasResponse(
    @SerializedName("_id")
    val id: String? = null,
    @SerializedName("id")
    val idAlt: String? = null,
    @SerializedName("mimetype")
    val mimetype: String? = null,
    @SerializedName("tags")
    val tags: List<String>? = null,
    @SerializedName("url")
    val url: String? = null,
    @SerializedName("width")
    val width: Int? = null,
    @SerializedName("height")
    val height: Int? = null
) {
    fun getEffectiveId(): String {
        return id ?: idAlt ?: ""
    }

    fun isAnimatedGif(): Boolean {
        return mimetype?.contains("gif", ignoreCase = true) == true ||
                url?.contains(".gif", ignoreCase = true) == true
    }
}
