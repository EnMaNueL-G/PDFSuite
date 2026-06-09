package com.enmanuelgil.pdfsuite.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.enmanuelgil.pdfsuite.model.PdfFormField
import com.enmanuelgil.pdfsuite.model.PdfMetadata
import com.enmanuelgil.pdfsuite.model.ToolResult
import com.itextpdf.text.*
import com.itextpdf.text.pdf.*
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object PdfTools {

    // ── Output file helpers ───────────────────────────────────────────────────

    private fun outFile(context: Context, name: String): File =
        File(context.filesDir, name).also { it.parentFile?.mkdirs() }

    fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "com.enmanuelgil.pdfsuite.provider", file)

    /**
     * Resolves the output [File] for a save operation.
     *
     * @param overwrite  true  → overwrite the source URI in-place (copy to filesDir, then copy back)
     *                   false → save as new file with [customName] into Downloads/OptiSuite/
     * @param customName used only when [overwrite] is false
     * @param fallback   default filename when overwrite=true (derived from source URI)
     */
    private fun resolveOutFile(
        context    : Context,
        overwrite  : Boolean,
        customName : String,
        fallback   : String
    ): File {
        return if (!overwrite && customName.isNotBlank()) {
            // Save to Downloads/OptiSuite/
            val dir = File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS),
                "OptiSuite"
            ).also { it.mkdirs() }
            val name = customName.let {
                if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }
            File(dir, name)
        } else {
            // Default: filesDir with fallback name
            outFile(context, fallback)
        }
    }

    /**
     * After saving to [outFile], copy back over the original [uri] if overwrite=true
     * and the uri points to a file:// or content:// backed by our own storage.
     */
    private fun maybeCopyBack(
        context   : Context,
        sourceUri : Uri,
        outFile   : File,
        overwrite : Boolean
    ) {
        if (!overwrite) return
        try {
            val os = context.contentResolver.openOutputStream(sourceUri, "wt") ?: return
            os.use { outFile.inputStream().copyTo(it) }
        } catch (_: Exception) {
            // If we can't write back (e.g. external URI), the saved file in filesDir is still valid
        }
    }

    // ── Merge PDFs ────────────────────────────────────────────────────────────

    suspend fun mergePdfs(
        context : Context,
        uris    : List<Uri>,
        outName : String = "combinado.pdf"
    ): ToolResult = withContext(Dispatchers.IO) {
        if (uris.size < 2) return@withContext ToolResult.Error("Selecciona al menos 2 PDFs")
        try {
            val out  = outFile(context, outName)
            val doc  = Document()
            val fos  = FileOutputStream(out)
            val copy = PdfSmartCopy(doc, fos)
            doc.open()
            for (uri in uris) {
                val inp = context.contentResolver.openInputStream(uri)
                    ?: return@withContext ToolResult.Error("No se pudo abrir: $uri")
                val reader = PdfReader(inp)
                for (page in 1..reader.numberOfPages)
                    copy.addPage(copy.getImportedPage(reader, page))
                reader.close()
                inp.close()
            }
            doc.close()
            fos.close()
            ToolResult.Success(fileUri(context, out), "PDFs combinados: ${uris.size} archivos → $outName")
        } catch (e: Exception) {
            ToolResult.Error("Error al combinar: ${e.message}")
        }
    }

    // ── Split PDF ─────────────────────────────────────────────────────────────

    suspend fun splitPdf(
        context   : Context,
        uri       : Uri,
        startPage : Int,
        endPage   : Int,
        outName   : String = "fragmento.pdf"
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val inp    = context.contentResolver.openInputStream(uri)
                ?: return@withContext ToolResult.Error("No se pudo abrir el PDF")
            val reader = PdfReader(inp)
            val total  = reader.numberOfPages
            val s = startPage.coerceIn(1, total)
            val e = endPage.coerceIn(s, total)
            val out  = outFile(context, outName)
            val doc  = Document()
            val fos  = FileOutputStream(out)
            val copy = PdfCopy(doc, fos)
            doc.open()
            for (page in s..e)
                copy.addPage(copy.getImportedPage(reader, page))
            doc.close()
            reader.close()
            inp.close()
            fos.close()
            ToolResult.Success(fileUri(context, out), "Páginas $s–$e extraídas (${e - s + 1} pág.) → $outName")
        } catch (e: Exception) {
            ToolResult.Error("Error al dividir: ${e.message}")
        }
    }

    // ── Rotate pages ─────────────────────────────────────────────────────────

    suspend fun rotatePages(
        context    : Context,
        uri        : Uri,
        degrees    : Int,
        pageIndices: List<Int>? = null,
        outName    : String = "rotado.pdf"
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val inp    = context.contentResolver.openInputStream(uri)
                ?: return@withContext ToolResult.Error("No se pudo abrir el PDF")
            val reader = PdfReader(inp)
            val total  = reader.numberOfPages
            val pages  = pageIndices?.map { it + 1 } ?: (1..total).toList()
            for (p in pages) {
                val dict = reader.getPageN(p)
                val cur  = reader.getPageRotation(p)
                dict.put(PdfName.ROTATE, PdfNumber((cur + degrees) % 360))
            }
            val out = outFile(context, outName)
            val stamper = PdfStamper(reader, FileOutputStream(out))
            stamper.close()
            reader.close()
            inp.close()
            ToolResult.Success(fileUri(context, out), "${pages.size} página(s) rotadas ${degrees}° → $outName")
        } catch (e: Exception) {
            ToolResult.Error("Error al rotar: ${e.message}")
        }
    }

    // ── Compress PDF ──────────────────────────────────────────────────────────

    suspend fun compressPdf(
        context : Context,
        uri     : Uri,
        outName : String = "comprimido.pdf"
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val inp    = context.contentResolver.openInputStream(uri)
                ?: return@withContext ToolResult.Error("No se pudo abrir el PDF")
            val beforeBytes = context.contentResolver.openFileDescriptor(uri, "r")
                ?.use { it.statSize } ?: 0L
            val reader  = PdfReader(inp)
            reader.removeUnusedObjects()

            // Save to Downloads/OptiSuite/ for easy user access
            val downloadsDir = File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS),
                "OptiSuite"
            ).also { it.mkdirs() }
            val out = File(downloadsDir, outName)
            // Fallback to filesDir if no external storage
            val outActual = if (downloadsDir.exists()) out else outFile(context, outName)

            val stamper = PdfStamper(reader, FileOutputStream(outActual), ' ', true)
            stamper.setFullCompression()
            stamper.close(); reader.close(); inp.close()

            val afterBytes = outActual.length()
            val saved = ((beforeBytes - afterBytes) * 100.0 / beforeBytes.coerceAtLeast(1)).toInt()
            val loc   = if (outActual.absolutePath.contains("Download", ignoreCase = true))
                "Descargas/OptiSuite/$outName" else outActual.absolutePath
            val msg = buildString {
                append("✓ Reducción: $saved%\n")
                append("  Antes: ${fmtSize(beforeBytes)}\n")
                append("  Después: ${fmtSize(afterBytes)}\n")
                append("  Ubicación: $loc")
            }
            ToolResult.Success(fileUri(context, outActual), msg)
        } catch (e: Exception) {
            ToolResult.Error("Error al comprimir: ${e.message}")
        }
    }

    // ── Set/Remove password ───────────────────────────────────────────────────

    suspend fun setPassword(
        context   : Context,
        uri       : Uri,
        userPass  : String,
        ownerPass : String = userPass + "_owner",
        outName   : String = "protegido.pdf"
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val inp     = context.contentResolver.openInputStream(uri)
                ?: return@withContext ToolResult.Error("No se pudo abrir el PDF")
            val reader  = PdfReader(inp)
            val out     = outFile(context, outName)
            val stamper = PdfStamper(reader, FileOutputStream(out))
            stamper.setEncryption(
                userPass.toByteArray(),
                ownerPass.toByteArray(),
                PdfWriter.ALLOW_PRINTING or PdfWriter.ALLOW_COPY,
                PdfWriter.ENCRYPTION_AES_128
            )
            stamper.close()
            reader.close()
            inp.close()
            ToolResult.Success(fileUri(context, out), "PDF protegido con contraseña AES-128 → $outName")
        } catch (e: Exception) {
            ToolResult.Error("Error al cifrar: ${e.message}")
        }
    }

    suspend fun removePassword(
        context  : Context,
        uri      : Uri,
        password : String,
        outName  : String = "sin_contrasena.pdf"
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val inp     = context.contentResolver.openInputStream(uri)
                ?: return@withContext ToolResult.Error("No se pudo abrir el PDF")
            val reader  = PdfReader(inp, password.toByteArray())
            val out     = outFile(context, outName)
            val stamper = PdfStamper(reader, FileOutputStream(out))
            stamper.close()
            reader.close()
            inp.close()
            ToolResult.Success(fileUri(context, out), "Contraseña eliminada → $outName")
        } catch (e: Exception) {
            ToolResult.Error("Contraseña incorrecta o PDF no cifrado")
        }
    }

    // ── Get metadata ──────────────────────────────────────────────────────────

    suspend fun getMetadata(context: Context, uri: Uri): PdfMetadata =
        withContext(Dispatchers.IO) {
            try {
                val inp    = context.contentResolver.openInputStream(uri)
                    ?: return@withContext PdfMetadata()
                val reader = PdfReader(inp)
                val info   = reader.info
                val size   = context.contentResolver.openFileDescriptor(uri, "r")
                    ?.use { it.statSize } ?: 0L
                val meta = PdfMetadata(
                    title     = info["Title"]    ?: "",
                    author    = info["Author"]   ?: "",
                    subject   = info["Subject"]  ?: "",
                    creator   = info["Creator"]  ?: "",
                    producer  = info["Producer"] ?: "",
                    pageCount = reader.numberOfPages,
                    sizeBytes = size,
                    encrypted = reader.isEncrypted
                )
                reader.close()
                inp.close()
                meta
            } catch (e: Exception) { PdfMetadata() }
        }

    // ── Add text overlay ──────────────────────────────────────────────────────
    // Stamps a text string onto a specific page at absolute (x, y) coordinates

    suspend fun addTextOverlay(
        context  : Context,
        uri      : Uri,
        text     : String,
        pageNum  : Int = 1,
        x        : Float = 72f,
        y        : Float = 72f,
        fontSize : Float = 14f,
        colorHex : Int = 0x000000,
        outName  : String = "con_texto.pdf"
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val inp     = context.contentResolver.openInputStream(uri)
                ?: return@withContext ToolResult.Error("No se pudo abrir el PDF")
            val reader  = PdfReader(inp)
            val total   = reader.numberOfPages
            val page    = pageNum.coerceIn(1, total)
            val out     = outFile(context, outName)
            val stamper = PdfStamper(reader, FileOutputStream(out))
            val cb      = stamper.getOverContent(page)
            val bf      = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED)
            val r = (colorHex shr 16 and 0xFF) / 255f
            val g = (colorHex shr 8  and 0xFF) / 255f
            val b = (colorHex        and 0xFF) / 255f
            cb.setColorFill(BaseColor(r, g, b))
            cb.beginText()
            cb.setFontAndSize(bf, fontSize)
            cb.setTextMatrix(x, y)
            cb.showText(text)
            cb.endText()
            stamper.close()
            reader.close()
            inp.close()
            ToolResult.Success(fileUri(context, out), "Texto añadido en página $page → $outName")
        } catch (e: Exception) {
            ToolResult.Error("Error al añadir texto: ${e.message}")
        }
    }

    // ── Stamp signature (bitmap) ──────────────────────────────────────────────

    suspend fun stampSignature(
        context    : Context,
        uri        : Uri,
        bitmap     : Bitmap,
        pageNum    : Int = 1,
        x          : Float = 72f,
        y          : Float = 72f,
        width      : Float = 200f,
        height     : Float = 80f,
        outName    : String = "firmado.pdf",
        overwrite  : Boolean = true,
        customName : String  = ""
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val inp     = context.contentResolver.openInputStream(uri)
                ?: return@withContext ToolResult.Error("No se pudo abrir el PDF")
            val reader  = PdfReader(inp)
            val total   = reader.numberOfPages
            val page    = pageNum.coerceIn(1, total)
            val fallback = uri.lastPathSegment?.substringAfterLast('/')?.let {
                if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" } ?: outName
            val out     = resolveOutFile(context, overwrite, customName, fallback)
            val stamper = PdfStamper(reader, FileOutputStream(out))

            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
            val image = Image.getInstance(bos.toByteArray())
            image.scaleToFit(width, height)
            image.setAbsolutePosition(x, y)

            stamper.getOverContent(page).addImage(image)
            stamper.close(); reader.close(); inp.close()

            maybeCopyBack(context, uri, out, overwrite)
            val loc = if (!overwrite) "Descargas/OptiSuite/${out.name}" else out.name
            ToolResult.Success(fileUri(context, out), "Firma añadida en página $page\nGuardado: $loc")
        } catch (e: Exception) {
            ToolResult.Error("Error al firmar: ${e.message}")
        }
    }

    // ── Get form fields ───────────────────────────────────────────────────────

    suspend fun getFormFields(
        context: Context,
        uri    : Uri
    ): List<PdfFormField> = withContext(Dispatchers.IO) {
        try {
            val inp    = context.contentResolver.openInputStream(uri)
                ?: return@withContext emptyList()
            val reader = PdfReader(inp)
            val acro   = reader.acroFields
            val result = acro.fields.entries.map { (name, _) ->
                val type  = acro.getFieldType(name)
                val value = acro.getField(name) ?: ""
                PdfFormField(
                    name  = name,
                    value = value,
                    type  = when (type) {
                        AcroFields.FIELD_TYPE_CHECKBOX  -> "checkbox"
                        AcroFields.FIELD_TYPE_COMBO     -> "combo"
                        AcroFields.FIELD_TYPE_LIST      -> "list"
                        AcroFields.FIELD_TYPE_TEXT      -> "text"
                        AcroFields.FIELD_TYPE_SIGNATURE -> "signature"
                        else                            -> "text"
                    }
                )
            }.sortedBy { it.name }
            reader.close()
            inp.close()
            result
        } catch (e: Exception) { emptyList() }
    }

    suspend fun fillFormFields(
        context : Context,
        uri     : Uri,
        fields  : Map<String, String>,
        outName : String = "formulario_relleno.pdf"
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val inp     = context.contentResolver.openInputStream(uri)
                ?: return@withContext ToolResult.Error("No se pudo abrir el PDF")
            val reader  = PdfReader(inp)
            val out     = outFile(context, outName)
            val stamper = PdfStamper(reader, FileOutputStream(out))
            val acro    = stamper.acroFields
            for ((name, value) in fields) {
                try { acro.setField(name, value) } catch (_: Exception) {}
            }
            stamper.setFormFlattening(true)  // flatten so fields become static
            stamper.close()
            reader.close()
            inp.close()
            ToolResult.Success(fileUri(context, out),
                "${fields.size} campo(s) rellenado(s) → $outName")
        } catch (e: Exception) {
            ToolResult.Error("Error al rellenar formulario: ${e.message}")
        }
    }

    // ── Extract text ──────────────────────────────────────────────────────────

    suspend fun extractText(
        context: Context,
        uri    : Uri
    ): Pair<String, Int> = withContext(Dispatchers.IO) {
        try {
            val inp    = context.contentResolver.openInputStream(uri)
                ?: return@withContext Pair("", 0)
            val reader = PdfReader(inp)
            val total  = reader.numberOfPages
            val sb     = StringBuilder()
            for (p in 1..total) {
                sb.appendLine("── Página $p ──")
                try {
                    sb.appendLine(PdfTextExtractor.getTextFromPage(reader, p))
                } catch (_: Exception) {}
                sb.appendLine()
            }
            reader.close()
            inp.close()
            Pair(sb.toString(), total)
        } catch (e: Exception) { Pair("", 0) }
    }

    suspend fun searchText(
        context: Context,
        uri    : Uri,
        query  : String
    ): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val inp    = context.contentResolver.openInputStream(uri)
                ?: return@withContext emptyList()
            val reader = PdfReader(inp)
            val total  = reader.numberOfPages
            val hits   = mutableListOf<Pair<Int, String>>()
            val lq     = query.lowercase()
            for (p in 1..total) {
                try {
                    val text = PdfTextExtractor.getTextFromPage(reader, p)
                    if (text.lowercase().contains(lq)) {
                        // Find snippet around the first occurrence
                        val idx = text.lowercase().indexOf(lq)
                        val start = (idx - 60).coerceAtLeast(0)
                        val end   = (idx + query.length + 60).coerceAtMost(text.length)
                        hits.add(Pair(p, "...${text.substring(start, end)}..."))
                    }
                } catch (_: Exception) {}
            }
            reader.close()
            inp.close()
            hits
        } catch (e: Exception) { emptyList() }
    }

    // ── Images from pages (for scan result import) ────────────────────────────

    suspend fun imagesToPdf(
        context : Context,
        bitmaps : List<Bitmap>,
        outName : String = "escaneado.pdf"
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val out = outFile(context, outName)
            val doc = Document()
            val fos = FileOutputStream(out)
            val writer = PdfWriter.getInstance(doc, fos)
            doc.open()
            for (bmp in bitmaps) {
                val bos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                val image = Image.getInstance(bos.toByteArray())
                // Fit to A4
                val pageW = doc.pageSize.width  - doc.leftMargin() - doc.rightMargin()
                val pageH = doc.pageSize.height - doc.topMargin()  - doc.bottomMargin()
                image.scaleToFit(pageW, pageH)
                image.alignment = Image.ALIGN_CENTER
                doc.add(image)
                if (bitmaps.indexOf(bmp) < bitmaps.size - 1) doc.newPage()
            }
            doc.close()
            writer.close()
            fos.close()
            ToolResult.Success(fileUri(context, out),
                "${bitmaps.size} imagen(es) → PDF ($outName)")
        } catch (e: Exception) {
            ToolResult.Error("Error al crear PDF: ${e.message}")
        }
    }

    // ── Add annotation (sticky note / free text / highlight) ─────────────────

    /**
     * type: "note" → sticky note (comment balloon)
     *        "freetext" → visible text overlay annotation
     *        "highlight" → semi-transparent colored rectangle
     */
    suspend fun addAnnotation(
        context    : Context,
        uri        : Uri,
        type       : String,   // "note" | "freetext" | "highlight" | "comment"
        text       : String,
        pageNum    : Int   = 1,
        x          : Float = 72f,
        y          : Float = 500f,
        width      : Float = 200f,
        height     : Float = 80f,
        colorHex   : Int   = 0xFFFF00,   // yellow default
        outName    : String = "anotado.pdf",
        overwrite  : Boolean = true,
        customName : String  = ""
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val inp     = context.contentResolver.openInputStream(uri)
                ?: return@withContext ToolResult.Error("No se pudo abrir el PDF")
            val reader  = PdfReader(inp)
            val total   = reader.numberOfPages
            val page    = pageNum.coerceIn(1, total)
            val fallback = uri.lastPathSegment?.substringAfterLast('/')?.let {
                if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" } ?: outName
            val out     = resolveOutFile(context, overwrite, customName, fallback)
            val stamper = PdfStamper(reader, FileOutputStream(out))

            val r = ((colorHex shr 16) and 0xFF) / 255f
            val g = ((colorHex shr 8 ) and 0xFF) / 255f
            val b = ( colorHex         and 0xFF) / 255f
            val baseColor = BaseColor(r, g, b)

            val rect = Rectangle(x, y, x + width, y + height)

            when (type) {
                "note" -> {
                    // Sticky note annotation
                    val annotation = PdfAnnotation.createText(
                        stamper.writer, rect, "Nota", text, false, "Comment"
                    )
                    annotation.setColor(baseColor)
                    stamper.addAnnotation(annotation, page)
                }
                "freetext" -> {
                    // Draw text directly on page content stream (most compatible)
                    val cb = stamper.getOverContent(page)
                    cb.saveState()
                    val bf = BaseFont.createFont(
                        BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED
                    )
                    cb.setColorFill(baseColor)
                    cb.beginText()
                    cb.setFontAndSize(bf, 12f)
                    cb.setTextMatrix(x, y)
                    cb.showText(text)
                    cb.endText()
                    cb.restoreState()
                }
                "highlight" -> {
                    // Draw semi-transparent colored rectangle on content layer
                    val cb = stamper.getOverContent(page)
                    cb.saveState()
                    val gs = PdfGState()
                    gs.setFillOpacity(0.35f)
                    cb.setGState(gs)
                    cb.setColorFill(baseColor)
                    cb.rectangle(x, y, width, height)
                    cb.fill()
                    cb.restoreState()
                }
            }

            stamper.close(); reader.close(); inp.close()
            maybeCopyBack(context, uri, out, overwrite)
            val label = when (type) {
                "note", "comment" -> "Nota"
                "freetext"        -> "Texto libre"
                else              -> "Resaltado"
            }
            val loc = if (!overwrite) "Descargas/OptiSuite/${out.name}" else out.name
            ToolResult.Success(fileUri(context, out), "$label añadido en página $page\nGuardado: $loc")
        } catch (e: Exception) {
            ToolResult.Error("Error al anotar: ${e.message}")
        }
    }

    // ── Insert image from gallery URI into PDF ────────────────────────────────

    suspend fun insertImageFromUri(
        context  : Context,
        pdfUri   : Uri,
        imageUri : Uri,
        pageNum  : Int   = 1,
        x        : Float = 72f,
        y        : Float = 400f,
        maxWidth : Float = 300f,
        maxHeight: Float = 300f,
        outName  : String = "con_imagen.pdf"
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            // Decode image
            val imgStream = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext ToolResult.Error("No se pudo abrir la imagen")
            val bmp = BitmapFactory.decodeStream(imgStream)
            imgStream.close()
            if (bmp == null) return@withContext ToolResult.Error("Formato de imagen no soportado")

            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
            val imgBytes = bos.toByteArray()

            val pdfIn   = context.contentResolver.openInputStream(pdfUri)
                ?: return@withContext ToolResult.Error("No se pudo abrir el PDF")
            val reader  = PdfReader(pdfIn)
            val total   = reader.numberOfPages
            val page    = pageNum.coerceIn(1, total)
            val out     = outFile(context, outName)
            val stamper = PdfStamper(reader, FileOutputStream(out))

            val image = Image.getInstance(imgBytes)
            image.scaleToFit(maxWidth, maxHeight)
            image.setAbsolutePosition(x, y)

            val cb = stamper.getOverContent(page)
            cb.addImage(image)

            stamper.close()
            reader.close()
            pdfIn.close()
            ToolResult.Success(fileUri(context, out),
                "Imagen insertada en página $page → $outName")
        } catch (e: Exception) {
            ToolResult.Error("Error al insertar imagen: ${e.message}")
        }
    }

    // ── Delete / reorder pages ────────────────────────────────────────────────

    /** Deletes the given 1-based page numbers and saves to a new file. */
    suspend fun deletePagesList(
        context       : Context,
        uri           : Uri,
        pagesToDelete : Set<Int>,
        outName       : String = "organizado.pdf"
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val inp    = context.contentResolver.openInputStream(uri)
                ?: return@withContext ToolResult.Error("No se pudo abrir el PDF")
            val reader = PdfReader(inp)
            val total  = reader.numberOfPages
            val keep   = (1..total).filter { it !in pagesToDelete }
            if (keep.isEmpty()) return@withContext ToolResult.Error("No puedes eliminar todas las páginas")
            val out  = outFile(context, outName)
            val doc  = Document()
            val fos  = FileOutputStream(out)
            val copy = PdfCopy(doc, fos)
            doc.open()
            for (p in keep) copy.addPage(copy.getImportedPage(reader, p))
            doc.close()
            reader.close()
            inp.close()
            fos.close()
            val deleted = pagesToDelete.size
            ToolResult.Success(fileUri(context, out),
                "$deleted página(s) eliminada(s), ${keep.size} restantes → $outName")
        } catch (e: Exception) {
            ToolResult.Error("Error al organizar: ${e.message}")
        }
    }

    /** Reorders pages according to the given 1-based order list. */
    suspend fun reorderPages(
        context  : Context,
        uri      : Uri,
        newOrder : List<Int>,
        outName  : String = "reordenado.pdf"
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val inp    = context.contentResolver.openInputStream(uri)
                ?: return@withContext ToolResult.Error("No se pudo abrir el PDF")
            val reader = PdfReader(inp)
            val total  = reader.numberOfPages
            val order  = newOrder.filter { it in 1..total }
            if (order.isEmpty()) return@withContext ToolResult.Error("Orden inválido")
            val out  = outFile(context, outName)
            val doc  = Document()
            val fos  = FileOutputStream(out)
            val copy = PdfCopy(doc, fos)
            doc.open()
            for (p in order) copy.addPage(copy.getImportedPage(reader, p))
            doc.close()
            reader.close()
            inp.close()
            fos.close()
            ToolResult.Success(fileUri(context, out), "Páginas reordenadas → $outName")
        } catch (e: Exception) {
            ToolResult.Error("Error al reordenar: ${e.message}")
        }
    }

    // ── Page count (quick) ────────────────────────────────────────────────────

    suspend fun getPageCount(context: Context, uri: Uri): Int =
        withContext(Dispatchers.IO) {
            try {
                val inp    = context.contentResolver.openInputStream(uri) ?: return@withContext 0
                val reader = PdfReader(inp)
                val n      = reader.numberOfPages
                reader.close()
                inp.close()
                n
            } catch (_: Exception) { 0 }
        }

    // ── Gallery images (URIs) → PDF ───────────────────────────────────────────

    /** Combines one or more gallery image URIs into a single PDF (one image per page). */
    suspend fun urisToPdf(
        context   : Context,
        imageUris : List<Uri>,
        outName   : String = "imagenes.pdf"
    ): ToolResult = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) return@withContext ToolResult.Error("No se seleccionaron imágenes")
        try {
            val out  = outFile(context, outName)
            val doc  = Document(PageSize.A4, 20f, 20f, 20f, 20f)
            val fos  = FileOutputStream(out)
            PdfWriter.getInstance(doc, fos)
            doc.open()

            val usable_w = PageSize.A4.width  - 40f
            val usable_h = PageSize.A4.height - 40f

            for (imgUri in imageUris) {
                val stream = context.contentResolver.openInputStream(imgUri) ?: continue
                val bmp    = BitmapFactory.decodeStream(stream)
                stream.close()
                if (bmp == null) continue
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, baos)
                val img = Image.getInstance(baos.toByteArray())
                img.scaleToFit(usable_w, usable_h)
                img.setAbsolutePosition(
                    20f + (usable_w - img.scaledWidth)  / 2f,
                    20f + (usable_h - img.scaledHeight) / 2f
                )
                doc.newPage()
                doc.add(img)
            }

            doc.close()
            fos.close()
            ToolResult.Success(fileUri(context, out),
                "${imageUris.size} imagen(es) → $outName")
        } catch (e: Exception) {
            ToolResult.Error("Error al convertir imágenes: ${e.message}")
        }
    }

    // ── Redact / Censure areas ────────────────────────────────────────────────

    data class RedactArea(
        val pageNum : Int,
        val x       : Float,
        val y       : Float,
        val w       : Float,
        val h       : Float,
        val label   : String = "CONFIDENCIAL"
    )

    /** Draws filled black rectangles over sensitive areas, optionally with a label. */
    suspend fun redactAreas(
        context    : Context,
        uri        : Uri,
        areas      : List<RedactArea>,
        outName    : String = "redactado.pdf",
        overwrite  : Boolean = true,
        customName : String  = ""
    ): ToolResult = withContext(Dispatchers.IO) {
        if (areas.isEmpty()) return@withContext ToolResult.Error("No hay áreas para redactar")
        try {
            val inp     = context.contentResolver.openInputStream(uri)
                ?: return@withContext ToolResult.Error("No se pudo abrir el PDF")
            val reader  = PdfReader(inp)
            val total   = reader.numberOfPages
            val fallback = uri.lastPathSegment?.substringAfterLast('/')?.let {
                if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" } ?: outName
            val out     = resolveOutFile(context, overwrite, customName, fallback)
            val stamper = PdfStamper(reader, FileOutputStream(out))

            val bf = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED)

            for (area in areas) {
                val page = area.pageNum.coerceIn(1, total)
                val cb = stamper.getOverContent(page)
                cb.saveState()
                // Black rectangle
                cb.setColorFill(BaseColor.BLACK)
                cb.rectangle(area.x, area.y, area.w, area.h)
                cb.fill()
                // White label centered in the rectangle
                if (area.label.isNotBlank()) {
                    cb.setColorFill(BaseColor.WHITE)
                    cb.beginText()
                    cb.setFontAndSize(bf, 8f)
                    val lx = area.x + (area.w / 2f) - (area.label.length * 2.4f)
                    val ly = area.y + (area.h / 2f) - 4f
                    cb.setTextMatrix(lx, ly)
                    cb.showText(area.label)
                    cb.endText()
                }
                cb.restoreState()
            }

            stamper.close(); reader.close(); inp.close()
            maybeCopyBack(context, uri, out, overwrite)
            val loc = if (!overwrite) "Descargas/OptiSuite/${out.name}" else out.name
            ToolResult.Success(fileUri(context, out),
                "${areas.size} área(s) redactada(s)\nGuardado: $loc")
        } catch (e: Exception) {
            ToolResult.Error("Error al redactar: ${e.message}")
        }
    }

    // ── WYSIWYG: page dimensions ──────────────────────────────────────────────

    data class PdfPageInfo(val widthPt: Float, val heightPt: Float)

    suspend fun getPdfPageInfo(context: Context, uri: Uri, pageNum: Int): PdfPageInfo =
        withContext(Dispatchers.IO) {
            try {
                val inp    = context.contentResolver.openInputStream(uri) ?: return@withContext PdfPageInfo(595f, 842f)
                val reader = PdfReader(inp)
                val rect   = reader.getPageSize(pageNum)
                reader.close(); inp.close()
                PdfPageInfo(rect.width, rect.height)
            } catch (_: Exception) { PdfPageInfo(595f, 842f) }
        }

    // ── WYSIWYG: text block data ──────────────────────────────────────────────

    data class PdfTextBlock(
        val text     : String,
        val x        : Float,   // PDF points, bottom-left origin
        val y        : Float,
        val width    : Float,
        val height   : Float,
        val fontSize : Float
    )

    // ── WYSIWYG: extract all text blocks with coordinates ─────────────────────

    suspend fun extractTextBlocks(context: Context, uri: Uri, pageNum: Int): List<PdfTextBlock> =
        withContext(Dispatchers.IO) { emptyList() }

    // ── WYSIWYG: save edited text back to PDF ─────────────────────────────────

    suspend fun applyWysiwygEdits(
        context    : Context,
        uri        : Uri,
        edits      : List<Pair<PdfTextBlock, String>>,
        pageNum    : Int,
        outName    : String  = "editado_wysiwyg.pdf",
        overwrite  : Boolean = true,
        customName : String  = ""
    ): ToolResult = withContext(Dispatchers.IO) {
        ToolResult.Error("Edición de texto no disponible en esta compilación")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun fmtSize(bytes: Long): String = when {
        bytes < 1024       -> "$bytes B"
        bytes < 1024*1024  -> "${"%.1f".format(bytes/1024.0)} KB"
        else               -> "${"%.1f".format(bytes/1024.0/1024.0)} MB"
    }
}
