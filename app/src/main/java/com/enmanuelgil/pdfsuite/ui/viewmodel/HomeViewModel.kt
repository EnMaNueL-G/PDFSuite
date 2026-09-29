package com.enmanuelgil.pdfsuite.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.pdfsuite.data.PdfRepository
import com.enmanuelgil.pdfsuite.data.RecentFilesStore
import com.enmanuelgil.pdfsuite.model.PdfEntry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val _recents   = MutableStateFlow<List<PdfEntry>>(emptyList())
    private val _favorites = MutableStateFlow<List<PdfEntry>>(emptyList())
    private val _isLoading = MutableStateFlow(false)

    val recents   : StateFlow<List<PdfEntry>> = _recents.asStateFlow()
    val favorites : StateFlow<List<PdfEntry>> = _favorites.asStateFlow()
    val isLoading : StateFlow<Boolean>        = _isLoading.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null
    private val thumbs = HashMap<String, PdfEntry>()   // miniaturas ya calculadas, por uri+tamaño (se renuevan si el archivo cambia)
    private fun sizeOf(context: Context, uri: Uri): Long = try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L
    } catch (_: Exception) { -1L }

    fun load(context: Context) {
        if (loadJob?.isActive == true) return          // una sola suscripción, aunque se llame en cada entrada
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            combine(
                RecentFilesStore.getRecents(context),
                RecentFilesStore.getFavorites(context)
            ) { recentUris, favUris ->
                Pair(recentUris, favUris)
            }.collect { (recentUris, favUris) ->
                val entries = recentUris.mapNotNull { uri ->
                    val key = "$uri|${sizeOf(context, uri)}"
                    (thumbs[key] ?: buildEntry(context, uri, favUris)?.also { thumbs[key] = it })
                        ?.copy(isFavorite = favUris.contains(uri))
                }
                _recents.value   = entries
                _favorites.value = entries.filter { it.isFavorite }
                _isLoading.value = false
            }
        }
    }

    private suspend fun buildEntry(context: Context, uri: Uri, favUris: Set<Uri>): PdfEntry? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var name = uri.lastPathSegment ?: "documento.pdf"
            var size = 0L
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = it.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = it.getLong(sizeIdx)
                }
            }
            val pages = PdfRepository.getPageCount(context, uri)
            val thumb = PdfRepository.getThumbnail(context, uri, 160)
            PdfEntry(
                uri         = uri,
                name        = name,
                displayName = name.removeSuffix(".pdf").removeSuffix(".PDF"),
                sizeBytes   = size,
                pageCount   = pages,
                isFavorite  = favUris.contains(uri),
                thumbnail   = thumb
            )
        } catch (e: Exception) { null }
    }

    fun addRecent(context: Context, uri: Uri) {
        viewModelScope.launch { RecentFilesStore.addRecent(context, uri) }
    }

    fun removeRecent(context: Context, uri: Uri) {
        viewModelScope.launch { RecentFilesStore.removeRecent(context, uri) }
    }

    fun toggleFavorite(context: Context, uri: Uri) {
        viewModelScope.launch { RecentFilesStore.toggleFavorite(context, uri) }
    }
}
