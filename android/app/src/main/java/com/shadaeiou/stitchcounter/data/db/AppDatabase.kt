package com.shadaeiou.stitchcounter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shadaeiou.stitchcounter.data.db.entities.HistoryEntry
import com.shadaeiou.stitchcounter.data.db.entities.Project

@Database(
    entities = [Project::class, HistoryEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "stitch-counter.db",
            ).build().also { INSTANCE = it }
        }
    }
}
