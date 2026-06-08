package com.enmanuelgil.pdfreader.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.pdfreader.data.PdfTools
import com.enmanuelgil.pdfreader.model.ToolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ToolsViewModel : ViewModel() {

    private val _selectedUris = MutableStateFlow<List<Uri>>(emptyList())
    private val _toolResult   = MutableStateFlow<ToolResult?>(null)
    private val _isProcessing = MutableStateFlow(false)
    private val _metadata     = MutableStateFlow<Map<String,String>>(emptyMap())

    val selectedUris : StateFlow<List<Uri>>          = _selectedUris.asStateFlow()
    val toolResult   : StateFlow<ToolResult?>        = _toolResult.asStateFlow()
    val isProcessing : StateFlow<Boolean>            = _isProcessing.asStateFlow()
    val metadata     : StateFlow<Map<String,String>> = _metadata.asStateFlow()

    fun setUris(uris: List<Uri>) { _selectedUris.value = uris }
    fun clearResult() { _toolResult.value = null }

    private fun runTool(block: suspend (PdfTools) -> ToolResult) {
        val uris = _selectedUris.value
        viewModelScope.launch {
            _isProcessing.value = true
            // We need context — pass it per-operation since ViewModel shouldn't hold it
            // Context will be passed via each public function
        }
    }

    fun merge(context: Context) {
        viewModelScope.launch {
            _isProcessing.value = true
            val tools  = PdfTools(context)
            val result = tools.mergeFiles(_selectedUris.value, "combinado_${timestamp()}")
            _toolResult.value   = result
            _isProcessing.value = false
        }
    }

    fun split(context: Context, ranges: List<Pair<Int, Int>>) {
        val uri = _selectedUris.value.firstOrNull() ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            val tools  = PdfTools(context)
            val name   = uriBaseName(context, uri)
            val result = tools.splitFile(uri, ranges, name)
            _toolResult.value   = result
            _isProcessing.value = false
        }
    }

    fun extract(context: Context, pagesInput: String) {
        val uri = _selectedUris.value.firstOrNull() ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            val pages  = parsePageInput(pagesInput)
            val tools  = PdfTools(context)
            val name   = uriBaseName(context, uri)
            val result = tools.extractPages(uri, pages, "${name}_extraido")
            _toolResult.value   = result
            _isProcessing.value = false
        }
    }

    fun rotate(context: Context, pages: List<Int>, degrees: Int) {
        val uri = _selectedUris.value.firstOrNull() ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            val tools  = PdfTools(context)
            val name   = uriBaseName(context, uri)
            val result = tools.rotatePages(uri, pages, degrees, "${name}_rotado")
            _toolResult.value   = result
            _isProcessing.value = false
        }
    }

    fun compress(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            val tools  = PdfTools(context)
            val name   = uriBaseName(context, uri)
            val result = tools.compress(uri, name)
            _toolResult.value   = result
            _isProcessing.value = false
        }
    }

    fun exportImages(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _toolResult.value   = ToolResult.Error("Exportar a imágenes: próximamente")
            _isProcessing.value = false
        }
    }

    fun loadMetadata(context: Context, uri: Uri) {
        viewModelScope.launch {
            val tools = PdfTools(context)
            _metadata.value = tools.getMetadata(uri)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Parse "1,3,5-8" → [0,2,4,5,6,7] (0-based) */
    private fun parsePageInput(input: String): List<Int> {
        val pages = mutableSetOf<Int>()
        input.split(",").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val (from, to) = trimmed.split("-")
                val f = from.trim().toIntOrNull()?.minus(1) ?: return@forEach
                val t = to.trim().toIntOrNull()?.minus(1) ?: return@forEach
                (f..t).forEach { pages.add(it) }
            } else {
                trimmed.toIntOrNull()?.minus(1)?.let { pages.add(it) }
            }
        }
        return pages.sorted()
    }

    private fun uriBaseName(context: Context, uri: Uri): String {
        var name = "documento"
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = c.getString(idx)?.substringBeforeLast(".") ?: name
            }
        }
        return name
    }

    private fun timestamp() = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
        .format(java.util.Date())
}
