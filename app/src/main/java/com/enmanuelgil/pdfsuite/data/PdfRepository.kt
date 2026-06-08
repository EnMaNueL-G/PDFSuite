package com.enmanuelgil.pdfsuite.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfRepository {

    /**
     * Render a single page to Bitmap at the given width (height auto-scaled).
     * Always renders on WHITE background — night-mode color filter is applied
     * at the UI layer via ColorMatrix so we only need one cached bitmap per page.
     */
    suspend fun renderPage(
        context   : Context,
        uri       : Uri,
        pageIndex : Int,
        width     : Int,
        nightMode : Boolean = false   // kept for API compat, no longer used here
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            pfd.use {
                PdfRenderer(pfd).use { r ->
                    if (pageIndex < 0 || pageIndex >= r.pageCount) return@withContext null
                    r.openPage(pageIndex).use { page ->
                        val ratio  = page.height.toFloat() / page.width.toFloat()
                        val height = (width * ratio).toInt().coerceAtLeast(1)
                        val bmp    = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        android.graphics.Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        } catch (e: Exception) { null }
    }

    /** Generate a thumbnail for the first page */
    suspend fun getThumbnail(context: Context, uri: Uri, size: Int = 200): Bitmap? =
        renderPage(context, uri, 0, size)

    /** Get page count without rendering */
    suspend fun getPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            } ?: 0
        } catch (e: Exception) { 0 }
    }

    /** Get page dimensions (width x height in points) */
    suspend fun getPageSize(context: Context, uri: Uri, pageIndex: Int = 0): Pair<Int, Int> =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { r ->
                        r.openPage(pageIndex).use { Pair(it.width, it.height) }
                    }
                } ?: Pair(0, 0)
            } catch (e: Exception) { Pair(0, 0) }
        }
}
