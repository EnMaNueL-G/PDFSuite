package com.enmanuelgil.pdfsuite.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.pdfsuite.data.PdfRepository
import com.enmanuelgil.pdfsuite.data.PdfTools
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileOutputStream

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
    private val _openError   = MutableStateFlow("")
    private val _hits        = MutableStateFlow<List<Pair<Int, String>>>(emptyList())
    private val _hitIndex    = MutableStateFlow(-1)
    private val _searching   = MutableStateFlow(false)

    val pageCount   : StateFlow<Int>     = _pageCount.asStateFlow()
    val currentPage : StateFlow<Int>     = _currentPage.asStateFlow()
    val isLoading   : StateFlow<Boolean> = _isLoading.asStateFlow()
    val nightMode   : StateFlow<Boolean> = _nightMode.asStateFlow()
    val showBars    : StateFlow<Boolean> = _showBars.asStateFlow()
    val showSearch  : StateFlow<Boolean> = _showSearch.asStateFlow()
    val searchQuery : StateFlow<String>  = _searchQuery.asStateFlow()
    val targetPage  : StateFlow<Int>     = _targetPage.asStateFlow()
    val fileName    : StateFlow<String>  = _fileName.asStateFlow()
    val openError   : StateFlow<String>  = _openError.asStateFlow()
    /** Coincidencias de la búsqueda: (página 1..n, fragmento). */
    val hits        : StateFlow<List<Pair<Int, String>>> = _hits.asStateFlow()
    val hitIndex    : StateFlow<Int>     = _hitIndex.asStateFlow()
    val searching   : StateFlow<Boolean> = _searching.asStateFlow()

    // Caché de páginas limitada por MEMORIA (1/8 de la disponible), no por número de páginas
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    private var uri: Uri? = null
    private var prefs: android.content.SharedPreferences? = null

    fun open(context: Context, uri: Uri) {
        this.uri = uri
        val p = context.getSharedPreferences("reader", Context.MODE_PRIVATE).also { prefs = it }
        _nightMode.value = p.getBoolean("night", false)
        // Nuevo documento: olvidar la búsqueda del anterior
        searchJob?.cancel()
        _showSearch.value = false; _searchQuery.value = ""; _hits.value = emptyList(); _hitIndex.value = -1; _searching.value = false
        viewModelScope.launch {
            _isLoading.value = true
            _openError.value = ""
            cache.evictAll()
            _currentPage.value = 0
            val count = PdfRepository.getPageCount(context, uri)
            _pageCount.value  = count
            if (count == 0) {
                val m = PdfTools.getMetadata(context, uri)
                _openError.value = if (m.encrypted) "Este PDF tiene contraseña. Quítala con Herramientas → Contraseña y ábrelo de nuevo."
                                   else "No se pudo abrir el PDF (puede estar dañado)."
            }
            _isLoading.value  = false
            // Volver a la página en la que se quedó
            val last = p.getInt("page:$uri", 0)
            if (count > 0 && last in 1 until count) _targetPage.value = last
            try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) _fileName.value = it.getString(0) ?: ""
                }
            } catch (e: Exception) { }
            if (_fileName.value.isBlank()) _fileName.value = uri.lastPathSegment?.substringAfterLast('/') ?: "documento.pdf"
        }
    }

    suspend fun getPage(context: Context, uri: Uri, pageIndex: Int, nightMode: Boolean): Bitmap? {
        val key = "${uri}#${pageIndex}"
        cache.get(key)?.let { return it }
        val metrics = context.resources.displayMetrics
        val bmp = PdfRepository.renderPage(context, uri, pageIndex, metrics.widthPixels)
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    fun setPage(page: Int) {
        _currentPage.value = page
        uri?.let { prefs?.edit()?.putInt("page:$it", page)?.apply() }
    }
    fun goToPage(page: Int)   { if (_pageCount.value > 0) _targetPage.value = page.coerceIn(0, _pageCount.value - 1) }
    fun clearTargetPage()     { _targetPage.value  = -1   }
    fun toggleBars()          { _showBars.value    = !_showBars.value    }
    fun toggleNightMode()     { _nightMode.value = !_nightMode.value; prefs?.edit()?.putBoolean("night", _nightMode.value)?.apply() }
    fun toggleSearch()        { _showSearch.value  = !_showSearch.value; if (!_showSearch.value) setSearch(null, "") }

    private var searchJob: Job? = null
    /** Busca en todo el documento (con una pequeña espera mientras escribes). */
    fun setSearch(context: Context?, q: String) {
        _searchQuery.value = q
        searchJob?.cancel()
        if (q.trim().length < 2 || context == null) { _hits.value = emptyList(); _hitIndex.value = -1; _searching.value = false; return }
        val u = uri ?: return
        searchJob = viewModelScope.launch {
            delay(450)
            _searching.value = true
            val h = PdfTools.searchText(context, u, q.trim())
            _hits.value = h
            _hitIndex.value = if (h.isEmpty()) -1 else 0
            _searching.value = false
            if (h.isNotEmpty()) goToPage(h[0].first - 1)
        }
    }
    fun nextHit() { val h = _hits.value; if (h.isEmpty()) return; _hitIndex.value = (_hitIndex.value + 1) % h.size; goToPage(h[_hitIndex.value].first - 1) }
    fun prevHit() { val h = _hits.value; if (h.isEmpty()) return; _hitIndex.value = (_hitIndex.value - 1 + h.size) % h.size; goToPage(h[_hitIndex.value].first - 1) }

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

    /** Imprimir con el sistema de impresión de Android (el PDF se envía tal cual). */
    fun print(context: Context, uri: Uri, name: String) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?,
                                  cb: LayoutResultCallback, extras: Bundle?) {
                if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
                cb.onLayoutFinished(PrintDocumentInfo.Builder(name.ifBlank { "documento.pdf" })
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }
            override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?,
                                 cb: WriteResultCallback) {
                // Copia en segundo plano (un PDF grande o en la nube congelaría la app)
                Thread {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { i -> FileOutputStream(dest.fileDescriptor).use { i.copyTo(it) } }
                        if (cancel?.isCanceled == true) cb.onWriteCancelled() else cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) { cb.onWriteFailed(e.message) }
                }.start()
            }
        }
        try { pm.print(name.ifBlank { "OptiSuite PDF" }, adapter, null) } catch (_: Exception) {}
    }
}
