package com.shadaeiou.stitchcounter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shadaeiou.stitchcounter.data.db.entities.HistoryEntry
import com.shadaeiou.stitchcounter.data.db.entities.PageAnnotation
import com.shadaeiou.stitchcounter.data.db.entities.Project

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN patternHtml TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [Project::class, HistoryEntry::class, PageAnnotation::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun historyDao(): HistoryDao
    abstract fun annotationDao(): AnnotationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "stitch-counter.db",
            )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
