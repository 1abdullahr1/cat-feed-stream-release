package com.catfeed.stream.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LikedCatEntity::class, BufferedCatEntity::class],
    version = 2,
    exportSchema = false
)
abstract class CatDatabase : RoomDatabase() {

    abstract fun likedCatDao(): LikedCatDao
    abstract fun bufferedCatDao(): BufferedCatDao

    companion object {
        @Volatile
        private var INSTANCE: CatDatabase? = null

        fun getInstance(context: Context): CatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CatDatabase::class.java,
                    "purr_feed_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
