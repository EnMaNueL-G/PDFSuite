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

    /** Render a single page to Bitmap at the given width (height auto from aspect ratio) */
    suspend fun renderPage(
        context   : Context,
        uri       : Uri,
        pageIndex : Int,
        width     : Int,
        nightMode : Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            pfd.use {
                val renderer = PdfRenderer(pfd)
                renderer.use { r ->
                    if (pageIndex < 0 || pageIndex >= r.pageCount) return@withContext null
                    r.openPage(pageIndex).use { page ->
                        val ratio  = page.height.toFloat() / page.width.toFloat()
                        val height = (width * ratio).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(if (nightMode) Color.rgb(18, 18, 18) else Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        if (nightMode) applyNightFilter(bmp)
                        bmp
                    }
                }
            }
        } catch (e: Exception) { null }
    }

    private fun applyNightFilter(bmp: Bitmap) {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = 255 - Color.red(p)
            val g = 255 - Color.green(p)
            val b = 255 - Color.blue(p)
            // Sepia-tinted inversion (easier on eyes)
            pixels[i] = Color.argb(Color.alpha(p),
                (r * 0.93 + g * 0.03 + b * 0.04).toInt().coerceIn(0, 255),
                (r * 0.04 + g * 0.90 + b * 0.06).toInt().coerceIn(0, 255),
                (r * 0.08 + g * 0.10 + b * 0.82).toInt().coerceIn(0, 255)
            )
        }
        bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
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
