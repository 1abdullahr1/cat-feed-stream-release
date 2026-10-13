package com.catfeed.stream.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "buffered_cats")
data class BufferedCatEntity(
    @PrimaryKey
    val id: String,
    val localFilePath: String,
    val originalUrl: String,
    val isGif: Boolean,
    val tagsString: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
