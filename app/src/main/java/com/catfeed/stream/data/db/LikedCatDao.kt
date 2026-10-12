package com.catfeed.stream.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedCatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedCat(cat: LikedCatEntity)

    @Query("DELETE FROM liked_cats WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM liked_cats ORDER BY timestamp DESC")
    fun getAllLikedCats(): Flow<List<LikedCatEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_cats WHERE id = :id)")
    suspend fun isLiked(id: String): Boolean

    @Query("SELECT id FROM liked_cats")
    suspend fun getAllLikedIds(): List<String>
}
