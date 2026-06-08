package com.enmanuelgil.pdfreader.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.pdfreader.data.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel : ViewModel() {

    private val _pageCount    = MutableStateFlow(0)
    private val _currentPage  = MutableStateFlow(0)
    private val _isLoading    = MutableStateFlow(false)
    private val _nightMode    = MutableStateFlow(false)
    private val _showBars     = MutableStateFlow(true)
    private val _showSearch   = MutableStateFlow(false)
    private val _searchQuery  = MutableStateFlow("")
    private val _targetPage   = MutableStateFlow(-1)
    val fileName              = MutableStateFlow("PDF")

    val pageCount  : StateFlow<Int>     = _pageCount.asStateFlow()
    val currentPage: StateFlow<Int>     = _currentPage.asStateFlow()
    val isLoading  : StateFlow<Boolean> = _isLoading.asStateFlow()
    val nightMode  : StateFlow<Boolean> = _nightMode.asStateFlow()
    val showBars   : StateFlow<Boolean> = _showBars.asStateFlow()
    val showSearch : StateFlow<Boolean> = _showSearch.asStateFlow()
    val searchQuery: StateFlow<String>  = _searchQuery.asStateFlow()
    val targetPage : StateFlow<Int>     = _targetPage.asStateFlow()

    // Page bitmap cache (max 20 pages ≈ ~80MB at full resolution)
    private val pageCache = LruCache<String, Bitmap>(20)

    private var currentUri: Uri? = null

    fun open(context: Context, uri: Uri) {
        if (currentUri == uri) return
        currentUri = uri
        viewModelScope.launch {
            _isLoading.value = true
            pageCache.evictAll()
            _currentPage.value = 0

            // Load file name
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) fileName.value = c.getString(idx) ?: "PDF"
                }
            }

            val repo = PdfRepository(context)
            _pageCount.value = repo.countPages(uri)
            _isLoading.value = false
        }
    }

    /** Get (or render) a page bitmap. Uses cache. */
    suspend fun getPage(
        context   : Context,
        uri       : Uri,
        pageIndex : Int,
        nightMode : Boolean
    ): Bitmap? {
        val key = "$uri#$pageIndex#$nightMode"
        pageCache.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val repo = PdfRepository(context)
                // Get screen width for max quality
                val metrics = context.resources.displayMetrics
                val width   = metrics.widthPixels
                val dims    = repo.getPageDimensions(uri, pageIndex)
                val height  = if (dims != null && dims.first > 0) {
                    (width * dims.second.toFloat() / dims.first).toInt()
                } else {
                    (width * 1.414).toInt() // A4 ratio fallback
                }
                val bmp = repo.renderPage(uri, pageIndex, width, height, nightMode)
                bmp?.let { pageCache.put(key, it) }
                bmp
            } catch (_: Exception) { null }
        }
    }

    fun setPage(page: Int) {
        _currentPage.value = page.coerceIn(0, (_pageCount.value - 1).coerceAtLeast(0))
    }

    fun goToPage(page: Int) {
        val clamped = page.coerceIn(0, (_pageCount.value - 1).coerceAtLeast(0))
        _targetPage.value = clamped
        _currentPage.value = clamped
    }

    fun clearTargetPage() {
        _targetPage.value = -1
    }

    fun toggleBars() {
        _showBars.value = !_showBars.value
    }

    fun toggleNightMode() {
        _nightMode.value = !_nightMode.value
        pageCache.evictAll() // Re-render pages with new mode
    }

    fun toggleSearch() {
        _showSearch.value = !_showSearch.value
        if (!_showSearch.value) _searchQuery.value = ""
    }

    fun setSearch(context: Context, query: String) {
        _searchQuery.value = query
        // TODO: implement text search with PDFBox text stripper
        // For now, just updates the query display
    }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir PDF"))
    }
}
