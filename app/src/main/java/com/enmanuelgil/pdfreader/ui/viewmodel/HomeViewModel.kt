package com.enmanuelgil.pdfreader.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.pdfreader.data.PdfRepository
import com.enmanuelgil.pdfreader.data.RecentFilesStore
import com.enmanuelgil.pdfreader.model.PdfEntry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _recents   = MutableStateFlow<List<PdfEntry>>(emptyList())
    private val _favorites = MutableStateFlow<List<PdfEntry>>(emptyList())
    private val _isLoading = MutableStateFlow(false)

    val recents   : StateFlow<List<PdfEntry>> = _recents.asStateFlow()
    val favorites : StateFlow<List<PdfEntry>> = _favorites.asStateFlow()
    val isLoading : StateFlow<Boolean>        = _isLoading.asStateFlow()

    fun load(context: Context) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val store  = RecentFilesStore(context)
            val repo   = PdfRepository(context)

            // Collect both flows
            combine(store.recentUris, store.favoriteUris) { recUris, favUriSet ->
                Pair(recUris, favUriSet)
            }.collectLatest { (recUris, favUriSet) ->
                // Load entries for recents (most recent first)
                val recentEntries = recUris.reversed().mapNotNull { uriStr ->
                    try {
                        val uri = Uri.parse(uriStr)
                        repo.loadEntry(uri, favUriSet)
                    } catch (_: Exception) { null }
                }

                // Load entries for favorites
                val favEntries = favUriSet.mapNotNull { uriStr ->
                    try {
                        val uri = Uri.parse(uriStr)
                        if (uriStr !in recUris) repo.loadEntry(uri, favUriSet)
                        else recentEntries.find { it.uri.toString() == uriStr }
                    } catch (_: Exception) { null }
                }

                _recents.value   = recentEntries
                _favorites.value = favEntries
                _isLoading.value = false
            }
        }
    }

    fun addRecent(context: Context, uri: Uri) {
        viewModelScope.launch {
            RecentFilesStore(context).addRecent(uri)
        }
    }

    fun removeRecent(context: Context, uri: Uri) {
        viewModelScope.launch {
            RecentFilesStore(context).removeRecent(uri)
        }
    }

    fun toggleFavorite(context: Context, uri: Uri) {
        viewModelScope.launch {
            RecentFilesStore(context).toggleFavorite(uri)
        }
    }
}
