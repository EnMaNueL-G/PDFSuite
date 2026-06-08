package com.enmanuelgil.pdfsuite.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.pdfsuite.data.PdfRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReaderViewModel : ViewModel() {
    private val _pageCount   = MutableStateFlow(0)
    private val _currentPage = MutableStateFlow(0)
    private val _isLoading   = MutableStateFlow(false)
    private val _nightMode   = MutableStateFlow(false)
    private val _showBars    = MutableStateFlow(true)
    private val _showSearch  = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _targetPage  = MutableStateFlow(-1)
    private val _fileName    = MutableStateFlow("")

    val pageCount   : StateFlow<Int>     = _pageCount.asStateFlow()
    val currentPage : StateFlow<Int>     = _currentPage.asStateFlow()
    val isLoading   : StateFlow<Boolean> = _isLoading.asStateFlow()
    val nightMode   : StateFlow<Boolean> = _nightMode.asStateFlow()
    val showBars    : StateFlow<Boolean> = _showBars.asStateFlow()
    val showSearch  : StateFlow<Boolean> = _showSearch.asStateFlow()
    val searchQuery : StateFlow<String>  = _searchQuery.asStateFlow()
    val targetPage  : StateFlow<Int>     = _targetPage.asStateFlow()
    val fileName    : StateFlow<String>  = _fileName.asStateFlow()

    // LruCache: max 20 pages (approx 20 × ~2MB = 40MB max)
    private val cache = LruCache<String, Bitmap>(20)

    fun open(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            cache.evictAll()
            _currentPage.value = 0
            val count = PdfRepository.getPageCount(context, uri)
            _pageCount.value  = count
            _isLoading.value  = false
            // Resolve display name
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) _fileName.value = it.getString(idx) ?: ""
                    }
                }
            } catch (e: Exception) { _fileName.value = uri.lastPathSegment ?: "" }
        }
    }

    suspend fun getPage(context: Context, uri: Uri, pageIndex: Int, nightMode: Boolean): Bitmap? {
        // nightMode filter is applied at UI layer — cache by uri+index only
        val key = "${uri}#${pageIndex}"
        cache.get(key)?.let { return it }
        val metrics = context.resources.displayMetrics
        val bmp = PdfRepository.renderPage(context, uri, pageIndex, metrics.widthPixels)
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    fun setPage(page: Int)    { _currentPage.value = page }
    fun goToPage(page: Int)   { _targetPage.value  = page.coerceIn(0, _pageCount.value - 1) }
    fun clearTargetPage()     { _targetPage.value  = -1   }
    fun toggleBars()          { _showBars.value    = !_showBars.value    }
    fun toggleNightMode()     { _nightMode.value   = !_nightMode.value }
    fun toggleSearch()        { _showSearch.value  = !_showSearch.value  }
    fun setSearch(context: Context, q: String) { _searchQuery.value = q  }

    fun share(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir PDF"))
        } catch (e: Exception) { /* ignore */ }
    }
}
