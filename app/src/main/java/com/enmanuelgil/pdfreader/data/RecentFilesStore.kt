package com.enmanuelgil.pdfreader.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.enmanuelgil.pdfreader.model.PdfEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("pdf_reader_prefs")

class RecentFilesStore(private val context: Context) {

    companion object {
        private val KEY_RECENTS    = stringSetPreferencesKey("recent_uris")
        private val KEY_FAVORITES  = stringSetPreferencesKey("favorite_uris")
        private const val MAX_RECENTS = 20
    }

    // ── Recent URIs ──────────────────────────────────────────────────────────

    val recentUris: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_RECENTS]?.toList()?.reversed() ?: emptyList()
    }

    suspend fun addRecent(uri: Uri) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_RECENTS]?.toMutableSet() ?: mutableSetOf()
            // Remove if already present (to re-add at "latest" position via time)
            val list = current.toMutableList()
            val uriStr = uri.toString()
            list.remove(uriStr)
            list.add(uriStr)
            // Keep only last MAX_RECENTS
            if (list.size > MAX_RECENTS) list.removeAt(0)
            prefs[KEY_RECENTS] = list.toSet()
        }
    }

    suspend fun removeRecent(uri: Uri) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_RECENTS]?.toMutableSet() ?: mutableSetOf()
            current.remove(uri.toString())
            prefs[KEY_RECENTS] = current
        }
    }

    // ── Favorites ────────────────────────────────────────────────────────────

    val favoriteUris: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_FAVORITES] ?: emptySet()
    }

    suspend fun toggleFavorite(uri: Uri): Boolean {
        var isNowFavorite = false
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES]?.toMutableSet() ?: mutableSetOf()
            val uriStr = uri.toString()
            if (uriStr in current) {
                current.remove(uriStr)
                isNowFavorite = false
            } else {
                current.add(uriStr)
                isNowFavorite = true
            }
            prefs[KEY_FAVORITES] = current
        }
        return isNowFavorite
    }

    suspend fun isFavorite(uri: Uri): Boolean {
        val current = context.dataStore.data.map { it[KEY_FAVORITES] ?: emptySet() }
        var result = false
        current.collect { result = uri.toString() in it }
        return result
    }
}
