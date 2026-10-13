package com.catfeed.stream.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BufferedCatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BufferedCatEntity)

    @Query("DELETE FROM buffered_cats WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM buffered_cats ORDER BY timestamp ASC")
    suspend fun getAllBuffered(): List<BufferedCatEntity>

    @Query("SELECT COUNT(*) FROM buffered_cats")
    suspend fun getCount(): Int

    // Deletes the oldest :limit rows that are NOT in the liked set, freeing space
    @Query(
        "DELETE FROM buffered_cats WHERE id IN (" +
            "SELECT b.id FROM buffered_cats b " +
            "LEFT JOIN liked_cats l ON b.id = l.id " +
            "WHERE l.id IS NULL " +
            "ORDER BY b.timestamp ASC " +
            "LIMIT :limit)"
    )
    suspend fun deleteOldestNonLiked(limit: Int)
}
