package com.enmanuelgil.pdfsuite.model

import android.graphics.Bitmap
import android.net.Uri

data class PdfEntry(
    val uri        : Uri,
    val name       : String,
    val displayName: String,
    val sizeBytes  : Long,
    val pageCount  : Int     = 0,
    val lastOpened : Long    = System.currentTimeMillis(),
    val isFavorite : Boolean = false,
    val thumbnail  : Bitmap? = null
) {
    val sizeLabel: String get() = when {
        sizeBytes < 1024       -> "${sizeBytes} B"
        sizeBytes < 1024*1024  -> "${"%.1f".format(sizeBytes/1024.0)} KB"
        else                   -> "${"%.1f".format(sizeBytes/1024.0/1024.0)} MB"
    }
}

data class PdfMetadata(
    val title     : String  = "",
    val author    : String  = "",
    val subject   : String  = "",
    val creator   : String  = "",
    val producer  : String  = "",
    val pageCount : Int     = 0,
    val sizeBytes : Long    = 0,
    val encrypted : Boolean = false
)

data class PdfFormField(
    val name  : String,
    val value : String,
    val type  : String   // "text", "checkbox", "combo", "list", "radio", "signature"
)

sealed class ToolResult {
    data class Success(val outputUri: Uri, val message: String) : ToolResult()
    data class Error(val message: String)                       : ToolResult()
    object Loading                                              : ToolResult()
}

enum class ReadMode { VERTICAL, HORIZONTAL }
