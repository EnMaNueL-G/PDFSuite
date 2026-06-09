package com.enmanuelgil.pdfsuite.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmanuelgil.pdfsuite.data.PdfTools
import com.enmanuelgil.pdfsuite.model.PdfFormField
import com.enmanuelgil.pdfsuite.model.PdfMetadata
import com.enmanuelgil.pdfsuite.model.ToolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ToolsViewModel : ViewModel() {

    private val _result     = MutableStateFlow<ToolResult?>(null)
    private val _isWorking  = MutableStateFlow(false)
    private val _metadata   = MutableStateFlow<PdfMetadata?>(null)
    private val _formFields = MutableStateFlow<List<PdfFormField>>(emptyList())
    private val _searchHits = MutableStateFlow<List<Pair<Int, String>>>(emptyList())
    private val _extractedText = MutableStateFlow<String>("")
    private val _pageCount  = MutableStateFlow(0)

    val result        : StateFlow<ToolResult?>          = _result.asStateFlow()
    val isWorking     : StateFlow<Boolean>              = _isWorking.asStateFlow()
    val metadata      : StateFlow<PdfMetadata?>         = _metadata.asStateFlow()
    val formFields    : StateFlow<List<PdfFormField>>   = _formFields.asStateFlow()
    val searchHits    : StateFlow<List<Pair<Int, String>>> = _searchHits.asStateFlow()
    val extractedText : StateFlow<String>               = _extractedText.asStateFlow()
    val pageCount     : StateFlow<Int>                  = _pageCount.asStateFlow()

    fun clearResult()        { _result.value = null }
    fun clearMetadata()      { _metadata.value = null }
    fun clearFormFields()    { _formFields.value = emptyList() }
    fun clearExtractedText() { _extractedText.value = "" }
    fun clearSearchHits()    { _searchHits.value = emptyList() }

    // ── Existing tools ────────────────────────────────────────────────────────

    fun merge(context: Context, uris: List<Uri>) =
        run { PdfTools.mergePdfs(context, uris) }

    fun split(context: Context, uri: Uri, start: Int, end: Int) =
        run { PdfTools.splitPdf(context, uri, start, end) }

    fun compress(context: Context, uri: Uri) =
        run { PdfTools.compressPdf(context, uri) }

    fun rotateAll(context: Context, uri: Uri, degrees: Int) =
        run { PdfTools.rotatePages(context, uri, degrees) }

    fun setPassword(context: Context, uri: Uri, pass: String) =
        run { PdfTools.setPassword(context, uri, pass) }

    fun removePassword(context: Context, uri: Uri, pass: String) =
        run { PdfTools.removePassword(context, uri, pass) }

    // ── New tools v1.1 ────────────────────────────────────────────────────────

    fun addTextOverlay(
        context : Context,
        uri     : Uri,
        text    : String,
        pageNum : Int,
        x       : Float,
        y       : Float,
        fontSize: Float,
        colorHex: Int
    ) = run { PdfTools.addTextOverlay(context, uri, text, pageNum, x, y, fontSize, colorHex) }

    fun stampSignature(
        context    : Context,
        uri        : Uri,
        bitmap     : Bitmap,
        pageNum    : Int,
        x          : Float,
        y          : Float,
        w          : Float,
        h          : Float,
        overwrite  : Boolean = true,
        customName : String  = ""
    ) = run { PdfTools.stampSignature(context, uri, bitmap, pageNum, x, y, w, h,
            overwrite = overwrite, customName = customName) }

    fun fillFormFields(context: Context, uri: Uri, fields: Map<String, String>) =
        run { PdfTools.fillFormFields(context, uri, fields) }

    fun imagesToPdf(context: Context, bitmaps: List<Bitmap>) =
        run { PdfTools.imagesToPdf(context, bitmaps) }

    fun loadMetadata(context: Context, uri: Uri) {
        viewModelScope.launch {
            _metadata.value = PdfTools.getMetadata(context, uri)
        }
    }

    fun loadFormFields(context: Context, uri: Uri) {
        viewModelScope.launch {
            _formFields.value = PdfTools.getFormFields(context, uri)
        }
    }

    fun loadPageCount(context: Context, uri: Uri) {
        viewModelScope.launch {
            _pageCount.value = PdfTools.getPageCount(context, uri)
        }
    }

    fun extractText(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isWorking.value = true
            val (text, _) = PdfTools.extractText(context, uri)
            _extractedText.value = text
            _isWorking.value = false
        }
    }

    fun searchText(context: Context, uri: Uri, query: String) {
        viewModelScope.launch {
            _isWorking.value = true
            _searchHits.value = PdfTools.searchText(context, uri, query)
            _isWorking.value = false
        }
    }

    // ── New tools v1.2 ────────────────────────────────────────────────────────

    fun addAnnotation(
        context    : Context,
        uri        : Uri,
        type       : String,
        text       : String,
        pageNum    : Int,
        x          : Float,
        y          : Float,
        width      : Float,
        height     : Float,
        colorHex   : Int,
        overwrite  : Boolean = true,
        customName : String  = ""
    ) = run { PdfTools.addAnnotation(context, uri, type, text, pageNum, x, y, width, height, colorHex,
            overwrite = overwrite, customName = customName) }

    fun insertImageFromUri(
        context  : Context,
        pdfUri   : Uri,
        imageUri : Uri,
        pageNum  : Int,
        x        : Float,
        y        : Float,
        maxWidth : Float,
        maxHeight: Float
    ) = run { PdfTools.insertImageFromUri(context, pdfUri, imageUri, pageNum, x, y, maxWidth, maxHeight) }

    fun deletePages(context: Context, uri: Uri, pages: Set<Int>) =
        run { PdfTools.deletePagesList(context, uri, pages) }

    fun reorderPages(context: Context, uri: Uri, newOrder: List<Int>) =
        run { PdfTools.reorderPages(context, uri, newOrder) }

    // ── New tools v1.3 ────────────────────────────────────────────────────────

    fun imagesToPdf(context: Context, imageUris: List<Uri>, outName: String = "imagenes.pdf") =
        run { PdfTools.urisToPdf(context, imageUris, outName) }

    fun redactAreas(
        context    : Context,
        uri        : Uri,
        areas      : List<PdfTools.RedactArea>,
        overwrite  : Boolean = true,
        customName : String  = ""
    ) = run { PdfTools.redactAreas(context, uri, areas,
            overwrite = overwrite, customName = customName) }

    // ── WYSIWYG text editing ──────────────────────────────────────────────────

    fun applyWysiwygEdits(
        context    : Context,
        uri        : Uri,
        edits      : List<Pair<PdfTools.PdfTextBlock, String>>,
        pageNum    : Int,
        overwrite  : Boolean = true,
        customName : String  = ""
    ) = run { PdfTools.applyWysiwygEdits(context, uri, edits, pageNum,
            overwrite = overwrite, customName = customName) }

    suspend fun extractTextBlocks(context: Context, uri: Uri, pageNum: Int) =
        PdfTools.extractTextBlocks(context, uri, pageNum)

    suspend fun getPdfPageInfo(context: Context, uri: Uri, pageNum: Int) =
        PdfTools.getPdfPageInfo(context, uri, pageNum)

    // ── Share result ──────────────────────────────────────────────────────────

    fun shareResult(context: Context) {
        val r = _result.value as? ToolResult.Success ?: return
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, r.outputUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir resultado"))
        } catch (_: Exception) {}
    }

    fun shareText(context: Context, text: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "Exportar texto"))
        } catch (_: Exception) {}
    }

    // ── Internal runner ───────────────────────────────────────────────────────

    private fun run(block: suspend () -> ToolResult) {
        if (_isWorking.value) return
        viewModelScope.launch {
            _isWorking.value = true
            _result.value    = ToolResult.Loading
            _result.value    = block()
            _isWorking.value = false
        }
    }
}
