package dev.sudoloser.ecodash.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dashboard_settings")

class LayoutStore(private val context: Context) {
    private val gson = Gson()

    companion object {
        val WIDGET_LAYOUT_KEY = stringPreferencesKey("widget_layout")
        val REFRESH_INTERVAL_KEY = stringPreferencesKey("refresh_interval")
    }

    val widgetLayoutFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val json = preferences[WIDGET_LAYOUT_KEY]
        if (json != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                defaultLayout()
            }
        } else {
            defaultLayout()
        }
    }

    val refreshIntervalFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[REFRESH_INTERVAL_KEY]?.toIntOrNull() ?: 30 // default 30 seconds
    }

    suspend fun saveWidgetLayout(layout: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[WIDGET_LAYOUT_KEY] = gson.toJson(layout)
        }
    }

    suspend fun saveRefreshInterval(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[REFRESH_INTERVAL_KEY] = seconds.toString()
        }
    }

    private fun defaultLayout(): List<String> {
        return listOf("minecraft", "network", "media_server")
    }
}
