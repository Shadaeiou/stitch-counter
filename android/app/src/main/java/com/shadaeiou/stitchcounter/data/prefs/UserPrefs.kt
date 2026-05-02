package com.shadaeiou.stitchcounter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPrefs(private val context: Context) {

    private val keyAutoUpdate = booleanPreferencesKey("auto_update_enabled")
    private val keyVolumeKeys = booleanPreferencesKey("volume_keys_enabled")
    private val keyCurrentProject = longPreferencesKey("current_project_id")
    private val keyCounterBg = longPreferencesKey("counter_bg_argb")
    private val keyCounterView = intPreferencesKey("counter_view")

    val autoUpdateFlow: Flow<Boolean> = context.dataStore.data.map { it[keyAutoUpdate] ?: true }
    val volumeKeysFlow: Flow<Boolean> = context.dataStore.data.map { it[keyVolumeKeys] ?: true }
    val currentProjectIdFlow: Flow<Long> = context.dataStore.data.map { it[keyCurrentProject] ?: 0L }
    val counterBackgroundFlow: Flow<Long> = context.dataStore.data.map {
        it[keyCounterBg] ?: DEFAULT_COUNTER_BG
    }
    val counterViewFlow: Flow<Int> = context.dataStore.data.map { it[keyCounterView] ?: 0 }

    suspend fun setAutoUpdate(value: Boolean) {
        context.dataStore.edit { it[keyAutoUpdate] = value }
    }
    suspend fun setVolumeKeys(value: Boolean) {
        context.dataStore.edit { it[keyVolumeKeys] = value }
    }
    suspend fun setCurrentProjectId(id: Long) {
        context.dataStore.edit { it[keyCurrentProject] = id }
    }
    suspend fun setCounterBackground(argb: Long) {
        context.dataStore.edit { it[keyCounterBg] = argb }
    }
    suspend fun setCounterView(v: Int) {
        context.dataStore.edit { it[keyCounterView] = v }
    }

    companion object {
        const val DEFAULT_COUNTER_BG: Long = 0xFF0A0A0AL
    }
}
