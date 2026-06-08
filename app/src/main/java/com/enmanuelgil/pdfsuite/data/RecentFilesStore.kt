package com.enmanuelgil.pdfsuite.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pdfsuite_prefs")

object RecentFilesStore {
    private val KEY_RECENTS   = stringSetPreferencesKey("recent_uris")
    private val KEY_FAVORITES = stringSetPreferencesKey("favorite_uris")
    private const val MAX_RECENTS = 20

    fun getRecents(context: Context): Flow<List<Uri>> =
        context.dataStore.data.map { prefs ->
            (prefs[KEY_RECENTS] ?: emptySet()).map { Uri.parse(it) }
        }

    fun getFavorites(context: Context): Flow<Set<Uri>> =
        context.dataStore.data.map { prefs ->
            (prefs[KEY_FAVORITES] ?: emptySet()).map { Uri.parse(it) }.toSet()
        }

    suspend fun addRecent(context: Context, uri: Uri) {
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_RECENTS] ?: emptySet()).toMutableList()
            val str = uri.toString()
            current.remove(str)
            current.add(0, str)
            prefs[KEY_RECENTS] = current.take(MAX_RECENTS).toSet()
        }
    }

    suspend fun removeRecent(context: Context, uri: Uri) {
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_RECENTS] ?: emptySet()).toMutableSet()
            current.remove(uri.toString())
            prefs[KEY_RECENTS] = current
        }
    }

    suspend fun toggleFavorite(context: Context, uri: Uri): Boolean {
        var isFav = false
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_FAVORITES] ?: emptySet()).toMutableSet()
            val str = uri.toString()
            if (current.contains(str)) { current.remove(str); isFav = false }
            else                       { current.add(str);    isFav = true  }
            prefs[KEY_FAVORITES] = current
        }
        return isFav
    }
}
