package com.enmanuelgil.pdfreader.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.enmanuelgil.pdfreader.model.PdfEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfRepository(private val context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    /** Load metadata for a PDF URI (name, size, page count) */
    suspend fun loadEntry(uri: Uri, favorites: Set<String> = emptySet()): PdfEntry? =
        withContext(Dispatchers.IO) {
            try {
                var name = uri.lastPathSegment ?: "document.pdf"
                var size = 0L

                // Query content resolver for display name and size
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                        if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                    }
                }

                val nameWithoutExt = if (name.endsWith(".pdf", ignoreCase = true))
                    name.dropLast(4) else name

                // Count pages
                val pageCount = countPages(uri)

                // Generate thumbnail (first page, 120x160)
                val thumb = renderThumbnail(uri, width = 120, height = 160)

                PdfEntry(
                    uri         = uri,
                    name        = nameWithoutExt,
                    displayName = name,
                    sizeBytes   = size,
                    pageCount   = pageCount,
                    lastOpened  = System.currentTimeMillis(),
                    isFavorite  = uri.toString() in favorites,
                    thumbnail   = thumb
                )
            } catch (e: Exception) {
                null
            }
        }

    /** Count pages using PdfRenderer */
    suspend fun countPages(uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            } ?: 0
        } catch (_: Exception) { 0 }
    }

    /** Render a single page to Bitmap */
    suspend fun renderPage(
        uri       : Uri,
        pageIndex : Int,
        width     : Int,
        height    : Int,
        nightMode : Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex >= renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(if (nightMode) Color.BLACK else Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        } catch (_: Exception) { null }
    }

    /** Generate a small thumbnail of the first page */
    suspend fun renderThumbnail(uri: Uri, width: Int = 120, height: Int = 160): Bitmap? =
        renderPage(uri, 0, width, height)

    /** Get page dimensions (in points) */
    suspend fun getPageDimensions(uri: Uri, pageIndex: Int): Pair<Int, Int>? =
        withContext(Dispatchers.IO) {
            try {
                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        renderer.openPage(pageIndex).use { page ->
                            Pair(page.width, page.height)
                        }
                    }
                }
            } catch (_: Exception) { null }
        }
}
