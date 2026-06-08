package com.enmanuelgil.pdfreader.model

import android.net.Uri

/** Represents a PDF file entry in the library */
data class PdfEntry(
    val uri        : Uri,
    val name       : String,       // filename without extension
    val displayName: String,       // filename with extension
    val sizeBytes  : Long,
    val pageCount  : Int = 0,
    val lastOpened : Long = 0L,    // epoch millis
    val isFavorite : Boolean = false,
    val thumbnail  : android.graphics.Bitmap? = null
) {
    val sizeLabel: String get() = when {
        sizeBytes < 1_024          -> "$sizeBytes B"
        sizeBytes < 1_048_576      -> "${sizeBytes / 1_024} KB"
        else                       -> "%.1f MB".format(sizeBytes / 1_048_576.0)
    }
}

/** Result of a PDF tool operation */
sealed class ToolResult {
    data class Success(val outputUri: Uri, val message: String) : ToolResult()
    data class Error(val message: String)                       : ToolResult()
    object Loading                                              : ToolResult()
}

/** Reader display mode */
enum class ReadMode { VERTICAL, HORIZONTAL }

/** Annotation type */
enum class AnnotationType { HIGHLIGHT, UNDERLINE, NOTE, FREEHAND }
