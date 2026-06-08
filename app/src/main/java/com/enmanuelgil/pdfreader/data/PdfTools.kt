package com.enmanuelgil.pdfreader.data

import android.content.Context
import android.net.Uri
import com.enmanuelgil.pdfreader.model.ToolResult

/**
 * PDF document manipulation tools.
 * v1.0: Core viewer only (PdfRenderer native API).
 * v1.1: Will add merge/split/rotate/compress via embedded library.
 *
 * All operations return stub results until v1.1 integration.
 */
class PdfTools(private val context: Context) {

    // ── Merge ─────────────────────────────────────────────────────────────────
    suspend fun mergeFiles(uris: List<Uri>, outputName: String): ToolResult =
        ToolResult.Error("Combinar PDFs: disponible en v1.1")

    // ── Split ─────────────────────────────────────────────────────────────────
    suspend fun splitFile(uri: Uri, ranges: List<Pair<Int, Int>> = emptyList(), baseName: String = "documento"): ToolResult =
        ToolResult.Error("Dividir PDF: disponible en v1.1")

    // ── Extract ───────────────────────────────────────────────────────────────
    suspend fun extractPages(uri: Uri, pages: List<Int>, outputName: String): ToolResult =
        ToolResult.Error("Extraer páginas: disponible en v1.1")

    // ── Rotate ────────────────────────────────────────────────────────────────
    suspend fun rotatePages(uri: Uri, pages: List<Int>, degrees: Int, outputName: String): ToolResult =
        ToolResult.Error("Rotar páginas: disponible en v1.1")

    // ── Metadata ──────────────────────────────────────────────────────────────
    suspend fun getMetadata(uri: Uri): Map<String, String> {
        // Use Android's basic content resolver metadata
        val result = mutableMapOf<String, String>()
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx >= 0) result["Archivo"] = c.getString(nameIdx) ?: ""
                    if (sizeIdx >= 0) {
                        val bytes = c.getLong(sizeIdx)
                        result["Tamaño"] = when {
                            bytes < 1024        -> "$bytes B"
                            bytes < 1_048_576   -> "${bytes / 1024} KB"
                            else                -> "%.1f MB".format(bytes / 1_048_576.0)
                        }
                    }
                }
            }
            // Page count via PdfRenderer
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { r ->
                    result["Páginas"] = r.pageCount.toString()
                }
            }
        } catch (_: Exception) {}
        return result
    }

    suspend fun setMetadata(uri: Uri, fields: Map<String, String>, outputName: String): ToolResult =
        ToolResult.Error("Editar metadatos: disponible en v1.1")

    // ── Compress ──────────────────────────────────────────────────────────────
    suspend fun compress(uri: Uri, outputName: String): ToolResult =
        ToolResult.Error("Comprimir: disponible en v1.1")
}
