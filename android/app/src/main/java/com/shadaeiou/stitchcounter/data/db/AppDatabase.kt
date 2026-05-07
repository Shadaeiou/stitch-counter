package com.shadaeiou.stitchcounter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shadaeiou.stitchcounter.data.db.entities.HistoryEntry
import com.shadaeiou.stitchcounter.data.db.entities.KnitProject
import com.shadaeiou.stitchcounter.data.db.entities.PageAnnotation
import com.shadaeiou.stitchcounter.data.db.entities.Project

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN patternHtml TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN patternHighlightRange TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS knit_projects (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                craftType TEXT NOT NULL DEFAULT 'knit',
                status TEXT NOT NULL DEFAULT 'queue',
                yarnBought INTEGER NOT NULL DEFAULT 0,
                needleSize TEXT NOT NULL DEFAULT '',
                patternSource TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                photoPath TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE knit_projects ADD COLUMN patternSecured INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE knit_projects ADD COLUMN yarnWeight TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE knit_projects ADD COLUMN projectPdfPath TEXT")
    }
}

@Database(
    entities = [Project::class, HistoryEntry::class, PageAnnotation::class, KnitProject::class],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun historyDao(): HistoryDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun knitProjectDao(): KnitProjectDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "stitch-counter.db",
            )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
