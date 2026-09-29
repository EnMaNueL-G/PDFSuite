package com.enmanuelgil.pdfsuite.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.enmanuelgil.pdfsuite.model.PdfFormField
import com.enmanuelgil.pdfsuite.model.PdfMetadata
import com.enmanuelgil.pdfsuite.model.ToolResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Motor PDF de OptiSuite PDF sobre PdfBox-Android (Apache-2.0).
 *
 * Coordenadas: todas las posiciones que llegan de la interfaz están en PUNTOS, con origen abajo a la
 * izquierda, sobre la página TAL COMO SE VE (con su rotación y recorte aplicados). [overlay] las
 * transforma al sistema interno de la página, así funcionan también las páginas giradas.
 *
 * Guardado: por defecto cada herramienta crea un archivo NUEVO en Descargas/OptiSuite PDF. Solo se
 * sobrescribe el original si el usuario lo elige, y con copia de seguridad para no perderlo nunca.
 */
object PdfTools {

    private const val OUT_DIR = "OptiSuite PDF"

    // ── Arranque, carga y guardado ────────────────────────────────────────────

    @Volatile private var initialized = false
    private fun ensureInit(context: Context) {
        if (!initialized) synchronized(this) {
            if (!initialized) { PDFBoxResourceLoader.init(context.applicationContext); initialized = true }
        }
    }

    fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".provider", file)

    private fun memory() = MemoryUsageSetting.setupMixed(48L * 1024 * 1024)

    /** Abre un PDF. Lanza IOException con un mensaje claro si no se puede. */
    private fun load(context: Context, uri: Uri, password: String = ""): PDDocument {
        ensureInit(context)
        val inp = context.contentResolver.openInputStream(uri) ?: throw IOException("No se pudo abrir el PDF")
        return try {
            inp.use { PDDocument.load(it, password, memory()) }
        } catch (e: InvalidPasswordException) {
            throw IOException(if (password.isEmpty()) "Este PDF tiene contraseña: quítala primero con la herramienta «Contraseña»." else "Contraseña incorrecta")
        }
    }

    /** Los PDF cifrados solo se pueden modificar tras quitar la contraseña (para no perder la protección sin querer). */
    private fun requireEditable(doc: PDDocument) {
        if (doc.isEncrypted) {
            doc.close()
            throw IOException("Este PDF está protegido: quita la contraseña primero con la herramienta «Contraseña».")
        }
    }

    private fun displayName(context: Context, uri: Uri): String = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "documento.pdf"
    } catch (_: Exception) { "documento.pdf" }

    /** «contrato.pdf» + «firmado» → «contrato (firmado).pdf» */
    fun derivedName(context: Context, source: Uri?, suffix: String): String {
        val base = source?.let { displayName(context, it) }?.substringBeforeLast('.')?.ifBlank { null } ?: "documento"
        return "$base ($suffix).pdf"
    }

    private fun cleanName(name: String): String {
        val n = name.trim().replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "documento" }
        return if (n.endsWith(".pdf", true)) n else "$n.pdf"
    }

    private fun tempFile(context: Context): File =
        File(context.cacheDir, "work").apply { mkdirs() }.let { File(it, "${UUID.randomUUID()}.pdf") }

    /**
     * Guarda [doc] y lo cierra.
     *  - overwrite=false → archivo nuevo en Descargas/OptiSuite PDF (nombre [customName] o [defaultName]).
     *  - overwrite=true  → reemplaza [source] con copia de seguridad: si algo falla, el original se restaura.
     */
    private fun finish(
        context     : Context,
        doc         : PDDocument,
        source      : Uri?,
        overwrite   : Boolean,
        customName  : String,
        defaultName : String,
        message     : String
    ): ToolResult {
        val tmp = tempFile(context)
        try {
            doc.save(tmp)
        } finally { doc.close() }
        // Validar que el resultado se puede abrir antes de publicarlo (uno cifrado es válido aunque pida contraseña)
        try { PDDocument.load(tmp, memory()).close() } catch (_: InvalidPasswordException) { }

        if (overwrite && source != null) {
            val backup = File(context.cacheDir, "work/backup-${UUID.randomUUID()}.pdf")
            try {
                context.contentResolver.openInputStream(source)?.use { i -> backup.outputStream().use { i.copyTo(it) } }
                    ?: throw IOException("sin acceso")
                val os = context.contentResolver.openOutputStream(source, "wt") ?: throw IOException("sin permiso de escritura")
                try { os.use { o -> tmp.inputStream().use { it.copyTo(o) } } }
                catch (e: Exception) {
                    // Restaurar el original; si ni eso funciona, dejar la copia de seguridad en Descargas
                    val restored = runCatching { context.contentResolver.openOutputStream(source, "wt")?.use { o -> backup.inputStream().use { it.copyTo(o) } } }.isSuccess
                    if (!restored) runCatching { publish(context, backup, "COPIA DE SEGURIDAD - " + cleanName(displayName(context, source))) }
                    throw e
                }
                return ToolResult.Success(source, "$message\nOriginal sobrescrito: ${displayName(context, source)}")
            } catch (e: Exception) {
                // Sin permiso de escritura sobre el original: se guarda como archivo nuevo, avisando.
                val uri = publish(context, tmp, cleanName(customName.ifBlank { defaultName }))
                return ToolResult.Success(uri, "$message\nNo se pudo sobrescribir el original (${e.message}); se guardó una copia en ${outDirLabel(context)}.")
            } finally { backup.delete(); tmp.delete() }
        }
        val name = cleanName(customName.ifBlank { defaultName })
        val uri = publish(context, tmp, name)
        tmp.delete()
        return ToolResult.Success(uri, "$message\nGuardado en ${outDirLabel(context)}/$name")
    }

    /** Dónde quedan los archivos (en Android 8–9 no se puede escribir en Descargas sin permisos). */
    fun outDirLabel(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Descargas/$OUT_DIR"
        else "Android/data/${context.packageName}/files/Download/$OUT_DIR"

    /** Copia [file] a Descargas/OptiSuite PDF y devuelve su Uri. */
    private fun publish(context: Context, file: File, name: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$OUT_DIR")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("No se pudo crear el archivo en Descargas")
            try {
                resolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } } ?: throw IOException("No se pudo escribir en Descargas")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
            return uri
        }
        // Android 8–9: carpeta de la app (no requiere permisos). Se avisa de la ruta real en outLocation().
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: throw IOException("No hay almacenamiento disponible")
        val dir = File(base, OUT_DIR).apply { mkdirs() }
        var out = File(dir, name); var i = 1
        while (out.exists()) out = File(dir, name.removeSuffix(".pdf") + " (${i++}).pdf")
        file.copyTo(out)
        return fileUri(context, out)
    }

    private inline fun tool(label: String, block: () -> ToolResult): ToolResult = try { block() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: InvalidPasswordException) { ToolResult.Error("$label: el PDF tiene contraseña. Quítala primero con la herramienta «Contraseña».") }
        catch (e: OutOfMemoryError) { ToolResult.Error("$label: el documento es demasiado grande para la memoria del móvil") }
        catch (e: IOException) { ToolResult.Error(e.message ?: "$label: error de lectura/escritura") }
        catch (e: Exception) { ToolResult.Error("$label: ${e.message ?: e.javaClass.simpleName}") }

    // ── Geometría de página (lo que se ve ↔ sistema interno) ──────────────────

    data class PdfPageInfo(val widthPt: Float, val heightPt: Float)

    private fun rotationOf(page: PDPage) = ((page.rotation % 360) + 360) % 360

    private fun displaySize(page: PDPage): PdfPageInfo {
        val c = page.cropBox
        return if (rotationOf(page) == 90 || rotationOf(page) == 270) PdfPageInfo(c.height, c.width) else PdfPageInfo(c.width, c.height)
    }

    /** Matriz que convierte coordenadas «como se ve» en coordenadas internas de la página. */
    private fun displayMatrix(page: PDPage): Matrix {
        val c = page.cropBox
        val llx = c.lowerLeftX; val lly = c.lowerLeftY; val w = c.width; val h = c.height
        return when (rotationOf(page)) {
            90  -> Matrix(0f, 1f, -1f, 0f, llx + w, lly)
            180 -> Matrix(-1f, 0f, 0f, -1f, llx + w, lly + h)
            270 -> Matrix(0f, -1f, 1f, 0f, llx, lly + h)
            else -> Matrix(1f, 0f, 0f, 1f, llx, lly)
        }
    }

    private fun toUser(page: PDPage, x: Float, y: Float): Pair<Float, Float> {
        val c = page.cropBox
        val llx = c.lowerLeftX; val lly = c.lowerLeftY; val w = c.width; val h = c.height
        return when (rotationOf(page)) {
            90  -> Pair(llx + w - y, lly + x)
            180 -> Pair(llx + w - x, lly + h - y)
            270 -> Pair(llx + y, lly + h - x)
            else -> Pair(llx + x, lly + y)
        }
    }

    /** Dibuja encima del contenido existente, en coordenadas «como se ve». */
    private inline fun overlay(doc: PDDocument, page: PDPage, block: (PDPageContentStream) -> Unit) {
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.saveGraphicsState()
            cs.transform(displayMatrix(page))
            block(cs)
            cs.restoreGraphicsState()
        }
    }

    // ── Fuente con acentos, ñ, griego y cirílico (Noto Sans, OFL) ─────────────

    private fun font(context: Context, doc: PDDocument): PDFont = try {
        context.assets.open("fonts/NotoSans-Regular.ttf").use { PDType0Font.load(doc, it, true) }
    } catch (_: Exception) { PDType1Font.HELVETICA }

    /** Quita los caracteres que la fuente no puede dibujar (en vez de fallar todo el guardado). */
    private fun drawable(font: PDFont, text: String): String = buildString {
        for (ch in text.replace("\t", "    ")) {
            if (ch == '\n' || ch == '\r') continue
            try { font.encode(ch.toString()); append(ch) } catch (_: Exception) { append('?') }
        }
    }

    private fun rgb(colorHex: Int) = floatArrayOf(((colorHex shr 16) and 0xFF) / 255f, ((colorHex shr 8) and 0xFF) / 255f, (colorHex and 0xFF) / 255f)

    private fun PDPageContentStream.text(font: PDFont, size: Float, x: Float, y: Float, s: String, color: FloatArray) {
        setNonStrokingColor(color[0], color[1], color[2])
        beginText(); setFont(font, size); newLineAtOffset(x, y); showText(s); endText()
    }

    // ── Imágenes: decodificar sin agotar la memoria ───────────────────────────

    fun decodeSampled(context: Context, uri: Uri, maxDim: Int = 2400): Bitmap? = try {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b) }
        var sample = 1
        while (b.outWidth / sample > maxDim || b.outHeight / sample > maxDim) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (_: Throwable) { null }

    private fun scaledDown(bmp: Bitmap, maxDim: Int): Bitmap {
        val m = maxOf(bmp.width, bmp.height)
        if (m <= maxDim) return bmp
        val f = maxDim.toFloat() / m
        return Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
    }

    private fun imageXObject(doc: PDDocument, bmp: Bitmap, transparent: Boolean): PDImageXObject =
        if (transparent) LosslessFactory.createFromImage(doc, bmp) else JPEGFactory.createFromImage(doc, bmp, 0.88f)

    // ── Combinar / dividir / rotar / organizar ────────────────────────────────

    suspend fun mergePdfs(context: Context, uris: List<Uri>, outName: String = ""): ToolResult = withContext(Dispatchers.IO) {
        if (uris.size < 2) return@withContext ToolResult.Error("Selecciona al menos 2 PDFs")
        tool("Error al combinar") {
            ensureInit(context)
            val tmp = tempFile(context)
            val merger = PDFMergerUtility()
            val streams = uris.map { context.contentResolver.openInputStream(it) ?: throw IOException("No se pudo abrir uno de los PDF") }
            try {
                streams.forEach { merger.addSource(it) }
                merger.destinationFileName = tmp.absolutePath
                merger.mergeDocuments(memory())
            } finally { streams.forEach { runCatching { it.close() } } }
            val doc = PDDocument.load(tmp, memory())
            val r = finish(context, doc, null, false, outName, "combinado.pdf", "PDFs combinados: ${uris.size} archivos")
            tmp.delete(); r
        }
    }

    private fun copyPages(context: Context, src: PDDocument, pages: List<Int>): PDDocument {
        val out = PDDocument(memory())
        for (p in pages) {
            val srcPage = src.getPage(p - 1)
            val imported = out.importPage(srcPage)
            imported.rotation = srcPage.rotation
            // Las anotaciones copiadas apuntan (/P, enlaces, widgets) al documento original: si se dejan,
            // al guardar se arrastran DENTRO del archivo las páginas que se querían quitar.
            val kept = runCatching { imported.annotations }.getOrDefault(emptyList()).filter { a ->
                a !is com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget &&
                    a !is com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink &&
                    a !is com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationPopup
            }
            kept.forEach { a -> a.cosObject.removeItem(COSName.P); a.cosObject.removeItem(COSName.getPDFName("Popup")); a.cosObject.removeItem(COSName.getPDFName("IRT")) }
            imported.annotations = kept
            imported.cosObject.removeItem(COSName.STRUCT_PARENTS)
            imported.cosObject.removeItem(COSName.getPDFName("B"))
        }
        return out
    }

    suspend fun splitPdf(context: Context, uri: Uri, startPage: Int, endPage: Int, outName: String = ""): ToolResult = withContext(Dispatchers.IO) {
        tool("Error al dividir") {
            load(context, uri).use { src -> requireEditable(src)
                val total = src.numberOfPages
                val s = startPage.coerceIn(1, total); val e = endPage.coerceIn(s, total)
                finish(context, copyPages(context, src, (s..e).toList()), null, false, outName, derivedName(context, uri, "págs $s-$e"), "Páginas $s–$e extraídas (${e - s + 1} pág.)")
            }
        }
    }

    suspend fun rotatePages(context: Context, uri: Uri, degrees: Int, pageIndices: List<Int>? = null, outName: String = "",
                            overwrite: Boolean = false): ToolResult = withContext(Dispatchers.IO) {
        tool("Error al rotar") {
            val doc = load(context, uri); requireEditable(doc)
            val idx = pageIndices ?: (0 until doc.numberOfPages).toList()
            for (i in idx) { val p = doc.getPage(i); p.rotation = ((p.rotation + degrees) % 360 + 360) % 360 }
            finish(context, doc, uri, overwrite, outName, derivedName(context, uri, "rotado"), "${idx.size} página(s) rotadas ${degrees}°")
        }
    }

    suspend fun deletePagesList(context: Context, uri: Uri, pagesToDelete: Set<Int>, outName: String = ""): ToolResult = withContext(Dispatchers.IO) {
        tool("Error al organizar") {
            load(context, uri).use { src -> requireEditable(src)
            val keep = (1..src.numberOfPages).filter { it !in pagesToDelete }
            if (keep.isEmpty()) return@tool ToolResult.Error("No puedes eliminar todas las páginas")
            val out = copyPages(context, src, keep)
            val r = finish(context, out, null, false, outName, derivedName(context, uri, "organizado"), "${pagesToDelete.size} página(s) eliminada(s), ${keep.size} restantes")
            r }
        }
    }

    suspend fun reorderPages(context: Context, uri: Uri, newOrder: List<Int>, outName: String = ""): ToolResult = withContext(Dispatchers.IO) {
        tool("Error al reordenar") {
            load(context, uri).use { src -> requireEditable(src)
            val order = newOrder.filter { it in 1..src.numberOfPages }
            if (order.isEmpty()) return@tool ToolResult.Error("Orden inválido")
            val out = copyPages(context, src, order)
            val r = finish(context, out, null, false, outName, derivedName(context, uri, "reordenado"), "Páginas reordenadas")
            r }
        }
    }

    suspend fun getPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        try { load(context, uri).use { it.numberOfPages } } catch (_: Throwable) { 0 }
    }

    // ── Comprimir de verdad: recomprime las imágenes grandes ──────────────────

    suspend fun compressPdf(context: Context, uri: Uri, outName: String = ""): ToolResult = withContext(Dispatchers.IO) {
        tool("Error al comprimir") {
            val before = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            val doc = load(context, uri); requireEditable(doc)
            var recompressed = 0
            // Cada imagen se recomprime UNA vez aunque aparezca en muchas páginas (p. ej. un logo)
            val done = HashMap<com.tom_roush.pdfbox.cos.COSBase, PDImageXObject?>()
            for (page in doc.pages) {
                val res = page.resources ?: continue
                for (name in res.xObjectNames.toList()) {
                    try {
                        val x = res.getXObject(name) as? PDImageXObject ?: continue
                        if (done.containsKey(x.cosObject)) { done[x.cosObject]?.let { res.put(name, it) }; continue }
                        done[x.cosObject] = null
                        if (x.cosObject.containsKey(COSName.SMASK) || x.cosObject.containsKey(COSName.MASK) || x.isStencil) continue
                        if (x.width * x.height < 400_000) continue            // imágenes pequeñas: no merece la pena
                        val bmp = x.image ?: continue                          // (no se recicla: PdfBox la tiene en caché)
                        val small = scaledDown(bmp, 1700)
                        val jpg = JPEGFactory.createFromImage(doc, small, 0.62f)
                        if (small !== bmp) small.recycle()
                        if (jpg.cosObject.length < x.cosObject.length) { res.put(name, jpg); done[x.cosObject] = jpg; recompressed++ }
                    } catch (_: Throwable) { /* imagen no soportada: se deja igual */ }
                }
            }
            val tmp = tempFile(context)
            doc.save(tmp); doc.close()
            val after = tmp.length()
            if (before > 0 && after >= before) {
                tmp.delete()
                return@tool ToolResult.Error("Este PDF ya está optimizado: no se pudo reducir más (${fmtSize(before)}).")
            }
            val saved = if (before > 0) ((before - after) * 100.0 / before).toInt() else 0
            val r = finish(context, PDDocument.load(tmp, memory()), null, false, outName, derivedName(context, uri, "comprimido"),
                "✓ Reducción: $saved% · ${fmtSize(before)} → ${fmtSize(after)}\n$recompressed imagen(es) recomprimida(s)")
            tmp.delete(); r
        }
    }

    // ── Contraseña (AES-256) ──────────────────────────────────────────────────

    suspend fun setPassword(context: Context, uri: Uri, userPass: String, outName: String = ""): ToolResult = withContext(Dispatchers.IO) {
        if (userPass.length < 4) return@withContext ToolResult.Error("Usa una contraseña de al menos 4 caracteres")
        tool("Error al cifrar") {
            val doc = load(context, uri); requireEditable(doc)
            val perms = AccessPermission().apply { setCanPrint(true); setCanExtractContent(true) }
            // Contraseña de propietario aleatoria: nadie puede derivarla de la tuya.
            val policy = StandardProtectionPolicy(UUID.randomUUID().toString() + UUID.randomUUID(), userPass, perms)
            policy.encryptionKeyLength = 256
            doc.protect(policy)
            finish(context, doc, null, false, outName, derivedName(context, uri, "protegido"), "PDF protegido con contraseña (AES-256)")
        }
    }

    suspend fun removePassword(context: Context, uri: Uri, password: String, outName: String = ""): ToolResult = withContext(Dispatchers.IO) {
        tool("Error") {
            val doc = load(context, uri, password)
            if (!doc.isEncrypted) { doc.close(); return@tool ToolResult.Error("Este PDF no tiene contraseña") }
            doc.isAllSecurityToBeRemoved = true
            finish(context, doc, null, false, outName, derivedName(context, uri, "sin contraseña"), "Contraseña eliminada")
        }
    }

    // ── Metadatos ─────────────────────────────────────────────────────────────

    suspend fun getMetadata(context: Context, uri: Uri): PdfMetadata = withContext(Dispatchers.IO) {
        try {
            val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            load(context, uri).use { doc ->
                val i = doc.documentInformation
                PdfMetadata(i.title ?: "", i.author ?: "", i.subject ?: "", i.creator ?: "", i.producer ?: "",
                    doc.numberOfPages, size, doc.isEncrypted)
            }
        } catch (e: IOException) {
            PdfMetadata(encrypted = e.message?.contains("contraseña") == true,
                sizeBytes = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L)
        } catch (_: Throwable) { PdfMetadata() }
    }

    // ── Texto, firma e imágenes encima de la página ──────────────────────────

    suspend fun addTextOverlay(context: Context, uri: Uri, text: String, pageNum: Int = 1, x: Float = 72f, y: Float = 72f,
                               fontSize: Float = 14f, colorHex: Int = 0x000000, outName: String = "",
                               overwrite: Boolean = false): ToolResult = withContext(Dispatchers.IO) {
        tool("Error al añadir texto") {
            val doc = load(context, uri); requireEditable(doc)
            val page = doc.getPage(pageNum.coerceIn(1, doc.numberOfPages) - 1)
            val f = font(context, doc)
            overlay(doc, page) { cs ->
                text.split('\n').forEachIndexed { i, line ->
                    cs.text(f, fontSize, x, y - i * fontSize * 1.25f, drawable(f, line), rgb(colorHex))
                }
            }
            finish(context, doc, uri, overwrite, outName, derivedName(context, uri, "con texto"), "Texto añadido en la página $pageNum")
        }
    }

    suspend fun stampSignature(context: Context, uri: Uri, bitmap: Bitmap, pageNum: Int = 1, x: Float = 72f, y: Float = 72f,
                               width: Float = 200f, height: Float = 80f, outName: String = "",
                               overwrite: Boolean = false, customName: String = ""): ToolResult = withContext(Dispatchers.IO) {
        tool("Error al firmar") {
            val doc = load(context, uri); requireEditable(doc)
            val page = doc.getPage(pageNum.coerceIn(1, doc.numberOfPages) - 1)
            drawBitmapFit(doc, page, bitmap, x, y, width, height, transparent = true)
            finish(context, doc, uri, overwrite, customName, derivedName(context, uri, "firmado"), "Firma añadida en la página $pageNum")
        }
    }

    private fun drawBitmapFit(doc: PDDocument, page: PDPage, bmp: Bitmap, x: Float, y: Float, w: Float, h: Float, transparent: Boolean) {
        val img = imageXObject(doc, scaledDown(bmp, 2000), transparent)
        val s = minOf(w / img.width, h / img.height)
        val dw = img.width * s; val dh = img.height * s
        overlay(doc, page) { it.drawImage(img, x + (w - dw) / 2f, y + (h - dh) / 2f, dw, dh) }
    }

    suspend fun insertImageFromUri(context: Context, pdfUri: Uri, imageUri: Uri, pageNum: Int = 1, x: Float = 72f, y: Float = 400f,
                                   maxWidth: Float = 300f, maxHeight: Float = 300f, outName: String = "",
                                   overwrite: Boolean = false): ToolResult = withContext(Dispatchers.IO) {
        tool("Error al insertar imagen") {
            val bmp = decodeSampled(context, imageUri) ?: return@tool ToolResult.Error("No se pudo abrir la imagen")
            val doc = load(context, pdfUri); requireEditable(doc)
            val page = doc.getPage(pageNum.coerceIn(1, doc.numberOfPages) - 1)
            drawBitmapFit(doc, page, bmp, x, y, maxWidth, maxHeight, transparent = bmp.hasAlpha())
            finish(context, doc, pdfUri, overwrite, outName, derivedName(context, pdfUri, "con imagen"), "Imagen insertada en la página $pageNum")
        }
    }

    // ── Anotaciones ───────────────────────────────────────────────────────────

    /** type: "note"/"comment" → nota adhesiva · "freetext" → texto visible · "highlight" → resaltado */
    suspend fun addAnnotation(context: Context, uri: Uri, type: String, text: String, pageNum: Int = 1, x: Float = 72f,
                              y: Float = 500f, width: Float = 200f, height: Float = 80f, colorHex: Int = 0xFFFF00,
                              outName: String = "", overwrite: Boolean = false, customName: String = ""): ToolResult =
        withContext(Dispatchers.IO) {
            tool("Error al anotar") {
                val doc = load(context, uri); requireEditable(doc)
                val page = doc.getPage(pageNum.coerceIn(1, doc.numberOfPages) - 1)
                applyAnnotation(context, doc, page, type, text, x, y, width, height, colorHex)
                val label = when (type) { "note", "comment" -> "Nota"; "freetext" -> "Texto"; else -> "Resaltado" }
                finish(context, doc, uri, overwrite, customName, derivedName(context, uri, "anotado"), "$label añadido en la página $pageNum")
            }
        }

    private fun applyAnnotation(context: Context, doc: PDDocument, page: PDPage, type: String, text: String,
                                x: Float, y: Float, w: Float, h: Float, colorHex: Int) {
        val c = rgb(colorHex)
        when (type) {
            "note", "comment" -> {
                val (ux, uy) = toUser(page, x, y + h)
                val a = PDAnnotationText()
                a.contents = text
                a.titlePopup = "Nota"
                a.rectangle = PDRectangle(ux, uy - 24f, 24f, 24f)
                a.color = PDColor(c, PDDeviceRGB.INSTANCE)
                a.constructAppearances()
                page.annotations = page.annotations.apply { add(a) }
            }
            "freetext" -> {
                val f = font(context, doc)
                overlay(doc, page) { it.text(f, 12f, x, y, drawable(f, text), c) }
            }
            else -> overlay(doc, page) { cs ->
                val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.35f }
                cs.setGraphicsStateParameters(gs)
                cs.setNonStrokingColor(c[0], c[1], c[2])
                cs.addRect(x, y, w, h); cs.fill()
            }
        }
    }

    // ── Formularios ───────────────────────────────────────────────────────────

    suspend fun getFormFields(context: Context, uri: Uri): List<PdfFormField> = withContext(Dispatchers.IO) {
        try {
            load(context, uri).use { doc ->
                val acro = doc.documentCatalog.acroForm ?: return@use emptyList()
                acro.fieldTree.filterIsInstance<PDTerminalField>().map { f ->
                    val type = when (f) {
                        is PDCheckBox -> "checkbox"; is PDRadioButton -> "radio"; is PDComboBox -> "combo"
                        is PDListBox -> "list"; is PDSignatureField -> "signature"; else -> "text"
                    }
                    val value = if (f is PDCheckBox) (if (f.isChecked) "Yes" else "") else (f.valueAsString ?: "")
                    PdfFormField(f.fullyQualifiedName, value, type)
                }.sortedBy { it.name }
            }
        } catch (_: Throwable) { emptyList() }
    }

    private val TRUTHY = setOf("yes", "on", "true", "1", "x", "sí", "si")

    suspend fun fillFormFields(context: Context, uri: Uri, fields: Map<String, String>, outName: String = ""): ToolResult =
        withContext(Dispatchers.IO) {
            tool("Error al rellenar el formulario") {
                val doc = load(context, uri); requireEditable(doc)
                val acro = doc.documentCatalog.acroForm ?: run { doc.close(); return@tool ToolResult.Error("Este PDF no tiene formulario") }
                var ok = 0; val failed = mutableListOf<String>()
                for ((name, value) in fields) {
                    val f = acro.getField(name) ?: continue
                    try {
                        when (f) {
                            is PDCheckBox -> if (value.trim().lowercase() in TRUTHY) f.check() else f.unCheck()
                            is PDSignatureField -> continue
                            else -> f.setValue(value)
                        }
                        ok++
                    } catch (_: Exception) { failed += name }
                }
                runCatching { acro.refreshAppearances(); acro.flatten() }   // campos fijos, visibles en cualquier lector
                val warn = if (failed.isNotEmpty()) "\nNo se pudieron rellenar: ${failed.take(5).joinToString()}" else ""
                finish(context, doc, null, false, outName, derivedName(context, uri, "rellenado"), "$ok campo(s) rellenado(s)$warn")
            }
        }

    // ── Texto: extraer y buscar ───────────────────────────────────────────────

    suspend fun extractText(context: Context, uri: Uri): Pair<String, Int> = withContext(Dispatchers.IO) {
        try {
            load(context, uri).use { doc ->
                val st = PDFTextStripper().apply { sortByPosition = true }
                val sb = StringBuilder()
                for (p in 1..doc.numberOfPages) {
                    st.startPage = p; st.endPage = p
                    sb.appendLine("── Página $p ──")
                    sb.appendLine(runCatching { st.getText(doc) }.getOrDefault(""))
                }
                Pair(sb.toString(), doc.numberOfPages)
            }
        } catch (_: Throwable) { Pair("", 0) }
    }

    /** Todas las coincidencias: (página, fragmento). */
    suspend fun searchText(context: Context, uri: Uri, query: String, max: Int = 300): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            load(context, uri).use { doc ->
                val st = PDFTextStripper().apply { sortByPosition = true }
                val hits = mutableListOf<Pair<Int, String>>()
                val lq = query.lowercase()
                for (p in 1..doc.numberOfPages) {
                    st.startPage = p; st.endPage = p
                    val text = runCatching { st.getText(doc) }.getOrDefault("").replace(Regex("\\s+"), " ")
                    val low = text.lowercase()
                    var idx = low.indexOf(lq)
                    while (idx >= 0 && hits.size < max) {
                        val s = (idx - 50).coerceAtLeast(0); val e = (idx + query.length + 50).coerceAtMost(text.length)
                        hits.add(Pair(p, "…${text.substring(s, e)}…"))
                        idx = low.indexOf(lq, idx + lq.length)
                    }
                    if (hits.size >= max) break
                }
                hits
            }
        } catch (_: Throwable) { emptyList() }
    }

    /** Texto por página (para traducir). */
    suspend fun pageTexts(context: Context, uri: Uri, pages: IntRange? = null): List<String> = withContext(Dispatchers.IO) {
        try {
            load(context, uri).use { doc ->
                val st = PDFTextStripper().apply { sortByPosition = true }
                (pages ?: 1..doc.numberOfPages).filter { it in 1..doc.numberOfPages }.map { p ->
                    st.startPage = p; st.endPage = p; runCatching { st.getText(doc) }.getOrDefault("")
                }
            }
        } catch (_: Throwable) { emptyList() }
    }

    // ── Imágenes → PDF ────────────────────────────────────────────────────────

    private fun addImagePage(doc: PDDocument, bmp: Bitmap) {
        val page = PDPage(PDRectangle.A4); doc.addPage(page)
        val img = JPEGFactory.createFromImage(doc, scaledDown(bmp, 2400), 0.85f)
        val m = 20f
        val aw = page.mediaBox.width - 2 * m; val ah = page.mediaBox.height - 2 * m
        val s = minOf(aw / img.width, ah / img.height)
        val w = img.width * s; val h = img.height * s
        PDPageContentStream(doc, page).use { it.drawImage(img, m + (aw - w) / 2f, m + (ah - h) / 2f, w, h) }
    }

    suspend fun imagesToPdf(context: Context, bitmaps: List<Bitmap>, outName: String = ""): ToolResult = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) return@withContext ToolResult.Error("No hay imágenes")
        tool("Error al crear el PDF") {
            ensureInit(context)
            val doc = PDDocument(memory())
            bitmaps.forEach { addImagePage(doc, it) }
            finish(context, doc, null, false, outName, "escaneado.pdf", "${bitmaps.size} página(s) → PDF")
        }
    }

    suspend fun urisToPdf(context: Context, imageUris: List<Uri>, outName: String = ""): ToolResult = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) return@withContext ToolResult.Error("No se seleccionaron imágenes")
        tool("Error al convertir imágenes") {
            ensureInit(context)
            val doc = PDDocument(memory())
            var n = 0
            for (u in imageUris) { val b = decodeSampled(context, u) ?: continue; addImagePage(doc, b); b.recycle(); n++ }
            if (n == 0) { doc.close(); return@tool ToolResult.Error("Ninguna imagen se pudo abrir") }
            finish(context, doc, null, false, outName, "imagenes.pdf", "$n imagen(es) → PDF")
        }
    }

    // ── Censura REAL ──────────────────────────────────────────────────────────

    data class RedactArea(val pageNum: Int, val x: Float, val y: Float, val w: Float, val h: Float, val label: String = "")

    /**
     * Censura de verdad: cada página afectada se convierte en imagen con las zonas en negro y
     * sustituye a la original. El texto, las imágenes y las anotaciones de debajo DESAPARECEN
     * (no se pueden copiar ni buscar). Contrapartida: esa página deja de tener texto seleccionable.
     */
    suspend fun redactAreas(context: Context, uri: Uri, areas: List<RedactArea>, outName: String = "",
                            overwrite: Boolean = false, customName: String = ""): ToolResult = withContext(Dispatchers.IO) {
        if (areas.isEmpty()) return@withContext ToolResult.Error("No hay zonas para censurar")
        tool("Error al censurar") {
            val srcFile = tempFile(context)
            context.contentResolver.openInputStream(uri)?.use { i -> srcFile.outputStream().use { i.copyTo(it) } } ?: throw IOException("No se pudo abrir el PDF")
            ensureInit(context)
            val doc = PDDocument.load(srcFile, memory())
            requireEditable(doc)
            val pages = redactInto(doc, srcFile, areas)
            val r = finish(context, doc, uri, overwrite, customName, derivedName(context, uri, "censurado"),
                "${areas.size} zona(s) censurada(s) de forma permanente en $pages página(s)")
            srcFile.delete(); r
        }
    }

    /** Sustituye en [doc] cada página afectada por su imagen censurada. [srcFile] = mismo contenido que [doc] en disco. */
    private fun redactInto(doc: PDDocument, srcFile: File, areas: List<RedactArea>): Int {
        val byPage = areas.groupBy { it.pageNum.coerceIn(1, doc.numberOfPages) }
        ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for ((pageNum, list) in byPage) {
                    val old = doc.getPage(pageNum - 1)
                    val ds = displaySize(old)
                    val scale = (200f / 72f).coerceAtMost(3000f / maxOf(ds.widthPt, ds.heightPt))   // ~200 ppp
                    val bmp = Bitmap.createBitmap((ds.widthPt * scale).toInt(), (ds.heightPt * scale).toInt(), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    renderer.openPage(pageNum - 1).use { it.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT) }
                    val canvas = Canvas(bmp)
                    val black = Paint().apply { color = Color.BLACK }
                    for (a in list) {
                        canvas.drawRect(a.x * scale, (ds.heightPt - a.y - a.h) * scale, (a.x + a.w) * scale, (ds.heightPt - a.y) * scale, black)
                    }
                    // Se VACÍA la misma página (no se sustituye): así nada del archivo puede seguir
                    // apuntando al contenido antiguo (marcadores, enlaces, etiquetas de accesibilidad…).
                    val box = PDRectangle(ds.widthPt, ds.heightPt)
                    val d = old.cosObject
                    for (k in listOf(COSName.ANNOTS, COSName.STRUCT_PARENTS, COSName.GROUP, COSName.THUMB,
                            COSName.getPDFName("PieceInfo"), COSName.getPDFName("B"), COSName.METADATA,
                            COSName.getPDFName("BleedBox"), COSName.getPDFName("TrimBox"), COSName.getPDFName("ArtBox"))) d.removeItem(k)
                    old.resources = com.tom_roush.pdfbox.pdmodel.PDResources()
                    old.mediaBox = box; old.cropBox = box; old.rotation = 0
                    val img = JPEGFactory.createFromImage(doc, bmp, 0.9f)
                    PDPageContentStream(doc, old, PDPageContentStream.AppendMode.OVERWRITE, true).use {
                        it.drawImage(img, 0f, 0f, ds.widthPt, ds.heightPt)
                    }
                    bmp.recycle()
                }
            }
        }
        stripDocumentLevelCopies(doc)
        return byPage.size
    }

    /**
     * Quita del documento lo que puede guardar copias del texto de páginas censuradas:
     * etiquetas de accesibilidad (árbol de estructura) y los campos de formulario sin widget visible.
     */
    private fun stripDocumentLevelCopies(doc: PDDocument) {
        val cat = doc.documentCatalog.cosObject
        cat.removeItem(COSName.STRUCT_TREE_ROOT)
        cat.removeItem(COSName.MARK_INFO)
        val acro = doc.documentCatalog.acroForm ?: return
        // Campos cuyos widgets ya no están en ninguna página → fuera (su valor podía ser el dato censurado)
        val liveWidgets = HashSet<com.tom_roush.pdfbox.cos.COSBase>()
        for (p in doc.pages) runCatching { p.annotations.forEach { liveWidgets.add(it.cosObject) } }
        val keep = acro.fields.filter { f ->
            val widgets = runCatching { (f as? PDTerminalField)?.widgets ?: emptyList() }.getOrDefault(emptyList())
            f !is PDTerminalField || widgets.any { it.cosObject in liveWidgets }
        }
        if (keep.size != acro.fields.size) acro.fields = keep
    }

    // ── Editor directo: tamaño de página y bloques de texto ──────────────────

    suspend fun getPdfPageInfo(context: Context, uri: Uri, pageNum: Int): PdfPageInfo = withContext(Dispatchers.IO) {
        try { load(context, uri).use { displaySize(it.getPage((pageNum - 1).coerceIn(0, it.numberOfPages - 1))) } }
        catch (_: Throwable) { PdfPageInfo(595f, 842f) }
    }

    data class PdfTextBlock(
        val text    : String,
        val x       : Float,   // puntos, origen abajo-izquierda de la página tal como se ve
        val y       : Float,
        val width   : Float,
        val height  : Float,
        val fontSize: Float
    )

    private class BlockStripper(private val pageH: Float) : PDFTextStripper() {
        val raw = mutableListOf<PdfTextBlock>()
        override fun writeString(text: String, positions: MutableList<TextPosition>) {
            if (text.isBlank() || positions.isEmpty()) return
            val first = positions.first(); val last = positions.last()
            val fs = positions.maxOf { maxOf(it.fontSizeInPt, it.heightDir) }.coerceIn(4f, 96f)
            val x = first.xDirAdj
            val right = last.xDirAdj + last.widthDirAdj
            val baselineFromTop = first.yDirAdj
            val bottom = pageH - baselineFromTop - fs * 0.22f
            raw.add(PdfTextBlock(text, x, bottom, (right - x).coerceAtLeast(8f), fs * 1.2f, fs))
        }
    }

    suspend fun extractTextBlocks(context: Context, uri: Uri, pageNum: Int): List<PdfTextBlock> = withContext(Dispatchers.IO) {
        try {
            load(context, uri).use { doc ->
                val page = doc.getPage(pageNum - 1)
                val st = BlockStripper(displaySize(page).heightPt).apply { sortByPosition = true; startPage = pageNum; endPage = pageNum }
                st.getText(doc)
                st.raw
            }
        } catch (_: Throwable) { emptyList() }
    }

    /**
     * Sustituye visualmente bloques de texto: tapa el original con blanco y escribe el nuevo.
     * Nota honesta: el texto original sigue dentro del archivo, debajo. Para ELIMINAR datos usa Censurar.
     */
    suspend fun applyWysiwygEdits(context: Context, uri: Uri, edits: List<Pair<PdfTextBlock, String>>, pageNum: Int,
                                  outName: String = "", overwrite: Boolean = false, customName: String = ""): ToolResult =
        applyEditorChanges(context, uri, pageNum, EditorChanges(textEdits = edits), overwrite, customName)

    /** Todo lo que el editor directo puede aplicar sobre una página, en UNA sola pasada. */
    data class EditorChanges(
        val textEdits : List<Pair<PdfTextBlock, String>> = emptyList(),
        val images    : List<ImageStamp> = emptyList(),
        val notes     : List<NoteStamp>  = emptyList(),
        val highlights: List<RectStamp>  = emptyList(),
        val redactions: List<RectStamp>  = emptyList()
    ) {
        val isEmpty get() = textEdits.isEmpty() && images.isEmpty() && notes.isEmpty() && highlights.isEmpty() && redactions.isEmpty()
    }
    data class ImageStamp(val bitmap: Bitmap, val x: Float, val y: Float, val w: Float, val h: Float, val transparent: Boolean)
    data class NoteStamp(val text: String, val x: Float, val y: Float, val w: Float, val h: Float)
    data class RectStamp(val x: Float, val y: Float, val w: Float, val h: Float)

    suspend fun applyEditorChanges(context: Context, uri: Uri, pageNum: Int, ch: EditorChanges,
                                   overwrite: Boolean = false, customName: String = ""): ToolResult = withContext(Dispatchers.IO) {
        val changedText = ch.textEdits.filter { (b, t) -> t.trim() != b.text.trim() }
        val c2 = ch.copy(textEdits = changedText)
        if (c2.isEmpty) return@withContext ToolResult.Error("No hay cambios que guardar")
        tool("Error al guardar") {
            val doc = load(context, uri); requireEditable(doc)
            val page = doc.getPage(pageNum.coerceIn(1, doc.numberOfPages) - 1)
            if (changedText.isNotEmpty()) {
                val f = font(context, doc)
                overlay(doc, page) { cs ->
                    for ((b, t) in changedText) {
                        cs.setNonStrokingColor(1f, 1f, 1f)
                        cs.addRect(b.x - 1f, b.y - 1f, b.width + 2f, b.height + 2f); cs.fill()
                        if (t.isNotBlank()) cs.text(f, b.fontSize.coerceIn(5f, 72f), b.x, b.y + b.height * 0.2f, drawable(f, t), floatArrayOf(0f, 0f, 0f))
                    }
                }
            }
            for (im in c2.images) drawBitmapFit(doc, page, im.bitmap, im.x, im.y, im.w, im.h, im.transparent)
            for (h in c2.highlights) applyAnnotation(context, doc, page, "highlight", "", h.x, h.y, h.w, h.h, 0xFFEB3B)
            // Las notas son anotaciones: si hay censura se añaden DESPUÉS (la censura elimina las anotaciones de la página)
            if (c2.redactions.isEmpty()) for (n in c2.notes) applyAnnotation(context, doc, page, "note", n.text, n.x, n.y, n.w, n.h, 0xFFD54F)
            val parts = listOfNotNull(
                changedText.size.takeIf { it > 0 }?.let { "$it texto(s)" },
                c2.images.size.takeIf { it > 0 }?.let { "$it imagen(es)/firma(s)" },
                c2.notes.size.takeIf { it > 0 }?.let { "$it nota(s)" },
                c2.highlights.size.takeIf { it > 0 }?.let { "$it resaltado(s)" },
            )
            val summary = (parts + listOfNotNull(c2.redactions.size.takeIf { it > 0 }?.let { "$it zona(s) censurada(s)" })).joinToString(", ")
            if (c2.redactions.isNotEmpty()) {
                // Censura sobre el resultado con todo lo anterior ya aplicado
                val tmp = tempFile(context); doc.save(tmp); doc.close()
                val d2 = PDDocument.load(tmp, memory())
                redactInto(d2, tmp, c2.redactions.map { RedactArea(pageNum, it.x, it.y, it.w, it.h) })
                val p2 = d2.getPage(pageNum.coerceIn(1, d2.numberOfPages) - 1)
                for (n in c2.notes) applyAnnotation(context, d2, p2, "note", n.text, n.x, n.y, n.w, n.h, 0xFFD54F)
                val r = finish(context, d2, uri, overwrite, customName, derivedName(context, uri, "editado"), "Guardado: $summary en la página $pageNum")
                tmp.delete()
                return@tool r
            }
            finish(context, doc, uri, overwrite, customName, derivedName(context, uri, "editado"), "Guardado: $summary en la página $pageNum")
        }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    /** Guarda en Descargas/OptiSuite PDF un PDF ya hecho (p. ej. el del escáner) y devuelve su Uri. */
    suspend fun saveCopy(context: Context, source: Uri, name: String): Uri = withContext(Dispatchers.IO) {
        val tmp = tempFile(context)
        context.contentResolver.openInputStream(source)?.use { i -> tmp.outputStream().use { i.copyTo(it) } } ?: throw IOException("No se pudo leer el escaneo")
        try { publish(context, tmp, cleanName(name)) } finally { tmp.delete() }
    }

    suspend fun countAnnotations(context: Context, uri: Uri, pageNum: Int): Int = withContext(Dispatchers.IO) {
        try { load(context, uri).use { it.getPage(pageNum - 1).annotations.size } } catch (_: Throwable) { -1 }
    }

    fun fmtSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
    }
}
