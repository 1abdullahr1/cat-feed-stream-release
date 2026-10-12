package com.catfeed.stream.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liked_cats")
data class LikedCatEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val isGif: Boolean,
    val tagsString: String,
    val timestamp: Long = System.currentTimeMillis()
)
