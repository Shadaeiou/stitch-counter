package com.shadaeiou.stitchcounter

import android.app.Application
import com.shadaeiou.stitchcounter.data.db.AppDatabase
import com.shadaeiou.stitchcounter.data.prefs.UserPrefs
import com.shadaeiou.stitchcounter.data.repo.ProjectRepository

class StitchCounterApp : Application() {

    lateinit var prefs: UserPrefs
        private set
    lateinit var repository: ProjectRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = UserPrefs(this)
        val db = AppDatabase.get(this)
        repository = ProjectRepository(db.projectDao(), db.historyDao())
    }

    companion object {
        lateinit var instance: StitchCounterApp
            private set
    }
}
