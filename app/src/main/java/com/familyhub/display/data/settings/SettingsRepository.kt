package com.familyhub.display.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "family_hub_settings")

data class AppSettings(
    val calendarIdleTimeoutMinutes: Int = 5,
    val defaultPhotoDurationSeconds: Int = 10,
    val keepScreenOn: Boolean = true,
    val cloudBaseUrl: String = "",
    val cloudApiKey: String = "",
    val syncIntervalHours: Int = 6,
    val lastSyncEpochMillis: Long = 0L,
    val driveFolderId: String = "",
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val calendarIdleTimeoutMinutes = intPreferencesKey("calendar_idle_timeout_minutes")
        val defaultPhotoDurationSeconds = intPreferencesKey("default_photo_duration_seconds")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val cloudBaseUrl = stringPreferencesKey("cloud_base_url")
        val cloudApiKey = stringPreferencesKey("cloud_api_key")
        val syncIntervalHours = intPreferencesKey("sync_interval_hours")
        val lastSyncEpochMillis = longPreferencesKey("last_sync_epoch_millis")
        val driveFolderId = stringPreferencesKey("drive_folder_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            calendarIdleTimeoutMinutes = prefs[Keys.calendarIdleTimeoutMinutes] ?: 5,
            defaultPhotoDurationSeconds = prefs[Keys.defaultPhotoDurationSeconds] ?: 10,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            cloudBaseUrl = prefs[Keys.cloudBaseUrl].orEmpty(),
            cloudApiKey = prefs[Keys.cloudApiKey].orEmpty(),
            syncIntervalHours = prefs[Keys.syncIntervalHours] ?: 6,
            lastSyncEpochMillis = prefs[Keys.lastSyncEpochMillis] ?: 0L,
            driveFolderId = prefs[Keys.driveFolderId].orEmpty(),
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                calendarIdleTimeoutMinutes = prefs[Keys.calendarIdleTimeoutMinutes] ?: 5,
                defaultPhotoDurationSeconds = prefs[Keys.defaultPhotoDurationSeconds] ?: 10,
                keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
                cloudBaseUrl = prefs[Keys.cloudBaseUrl].orEmpty(),
                cloudApiKey = prefs[Keys.cloudApiKey].orEmpty(),
                syncIntervalHours = prefs[Keys.syncIntervalHours] ?: 6,
                lastSyncEpochMillis = prefs[Keys.lastSyncEpochMillis] ?: 0L,
                driveFolderId = prefs[Keys.driveFolderId].orEmpty(),
            )
            val updated = transform(current)
            prefs[Keys.calendarIdleTimeoutMinutes] = updated.calendarIdleTimeoutMinutes
            prefs[Keys.defaultPhotoDurationSeconds] = updated.defaultPhotoDurationSeconds
            prefs[Keys.keepScreenOn] = updated.keepScreenOn
            prefs[Keys.cloudBaseUrl] = updated.cloudBaseUrl
            prefs[Keys.cloudApiKey] = updated.cloudApiKey
            prefs[Keys.syncIntervalHours] = updated.syncIntervalHours
            prefs[Keys.lastSyncEpochMillis] = updated.lastSyncEpochMillis
            prefs[Keys.driveFolderId] = updated.driveFolderId
        }
    }
}
