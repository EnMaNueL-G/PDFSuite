package com.enmanuelgil.pdfsuite.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.pdfsuite.data.PdfTools
import com.enmanuelgil.pdfsuite.model.ToolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ToolsViewModel : ViewModel() {
    private val _result    = MutableStateFlow<ToolResult?>(null)
    private val _isWorking = MutableStateFlow(false)

    val result    : StateFlow<ToolResult?> = _result.asStateFlow()
    val isWorking : StateFlow<Boolean>     = _isWorking.asStateFlow()

    fun clearResult() { _result.value = null }

    fun merge(context: Context, uris: List<Uri>) {
        run(context) { PdfTools.mergePdfs(context, uris) }
    }

    fun split(context: Context, uri: Uri, start: Int, end: Int) {
        run(context) { PdfTools.splitPdf(context, uri, start, end) }
    }

    fun compress(context: Context, uri: Uri) {
        run(context) { PdfTools.compressPdf(context, uri) }
    }

    fun rotateAll(context: Context, uri: Uri, degrees: Int) {
        run(context) { PdfTools.rotatePages(context, uri, degrees) }
    }

    fun setPassword(context: Context, uri: Uri, pass: String) {
        run(context) { PdfTools.setPassword(context, uri, pass) }
    }

    fun removePassword(context: Context, uri: Uri, pass: String) {
        run(context) { PdfTools.removePassword(context, uri, pass) }
    }

    private fun run(context: Context, block: suspend () -> ToolResult) {
        if (_isWorking.value) return
        viewModelScope.launch {
            _isWorking.value = true
            _result.value    = ToolResult.Loading
            _result.value    = block()
            _isWorking.value = false
        }
    }

    fun shareResult(context: Context) {
        val r = _result.value as? ToolResult.Success ?: return
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, r.outputUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir resultado"))
        } catch (e: Exception) { /* ignore */ }
    }
}
