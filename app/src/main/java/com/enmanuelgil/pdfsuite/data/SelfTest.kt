package com.enmanuelgil.pdfsuite.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import com.enmanuelgil.pdfsuite.model.ToolResult
import java.io.File

/**
 * Autotest del motor PDF — SOLO en compilaciones de depuración.
 *   adb push *.pdf /sdcard/Android/data/<paquete>/files/tests/
 *   adb shell am start -n <paquete>/com.enmanuelgil.pdfsuite.MainActivity --ez selftest true
 *   adb logcat -s OptiPdfTest
 */
object SelfTest {
    private const val TAG = "OptiPdfTest"

    fun enabled(context: Context) = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    suspend fun run(context: Context) {
        if (!enabled(context)) return
        val dir = File(context.getExternalFilesDir(null), "tests")
        fun u(name: String) = Uri.fromFile(File(dir, name))
        var ok = 0; var bad = 0
        suspend fun check(name: String, r: ToolResult, extra: suspend (Uri) -> String? = { null }) {
            if (r is ToolResult.Success) {
                val pages = PdfTools.getPageCount(context, r.outputUri)
                val encrypted = PdfTools.getMetadata(context, r.outputUri).encrypted
                val problem = if (pages <= 0 && !encrypted) "el resultado no se puede abrir" else extra(r.outputUri)
                if (problem == null) { ok++; Log.e(TAG, "OK   $name → $pages pág · ${r.message.replace('\n', ' ')}") }
                else { bad++; Log.e(TAG, "FALLO $name: $problem") }
            } else { bad++; Log.e(TAG, "FALLO $name: ${(r as? ToolResult.Error)?.message}") }
        }
        Log.e(TAG, "=== Autotest del motor PDF ===")
        val texto = u("prueba-texto.pdf"); val rotada = u("prueba-rotada.pdf")
        val form = u("prueba-formulario.pdf"); val imagen = u("prueba-imagen.pdf")

        check("combinar", PdfTools.mergePdfs(context, listOf(texto, rotada))) { uri -> if (PdfTools.getPageCount(context, uri) == 6) null else "esperaba 6 páginas" }
        check("dividir 2-3", PdfTools.splitPdf(context, texto, 2, 3)) { uri -> if (PdfTools.getPageCount(context, uri) == 2) null else "esperaba 2 páginas" }
        check("rotar", PdfTools.rotatePages(context, texto, 90))
        check("comprimir", PdfTools.compressPdf(context, imagen))
        val prot = PdfTools.setPassword(context, texto, "clave123")
        check("contraseña: poner", prot) { uri -> if (PdfTools.getMetadata(context, uri).encrypted) null else "no quedó cifrado" }
        if (prot is ToolResult.Success) {
            check("contraseña: quitar", PdfTools.removePassword(context, prot.outputUri, "clave123")) { uri ->
                if (!PdfTools.getMetadata(context, uri).encrypted) null else "sigue cifrado" }
            val wrong = PdfTools.removePassword(context, prot.outputUri, "mala")
            if (wrong is ToolResult.Error) { ok++; Log.e(TAG, "OK   contraseña incorrecta rechazada: ${wrong.message}") } else { bad++; Log.e(TAG, "FALLO aceptó una contraseña incorrecta") }
        }
        val meta = PdfTools.getMetadata(context, texto)
        if (meta.pageCount == 3) { ok++; Log.e(TAG, "OK   metadatos: ${meta.pageCount} pág, ${PdfTools.fmtSize(meta.sizeBytes)}") } else { bad++; Log.e(TAG, "FALLO metadatos: $meta") }
        check("texto con acentos/ñ/griego/cirílico", PdfTools.addTextOverlay(context, texto, "Ñandú · Ωmega · Жизнь", 1, 72f, 100f, 16f, 0x1565C0)) { uri ->
            val (t, _) = PdfTools.extractText(context, uri); if (t.contains("Ñandú") && t.contains("Жизнь")) null else "el texto no aparece al extraer" }
        check("texto en página rotada", PdfTools.addTextOverlay(context, rotada, "Texto en rotada", 2, 72f, 72f, 18f, 0))
        val sig = Bitmap.createBitmap(400, 160, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawLine(10f, 120f, 390f, 40f, Paint().apply { color = Color.BLUE; strokeWidth = 8f }) }
        check("firma", PdfTools.stampSignature(context, texto, sig, 1, 300f, 60f, 200f, 80f))
        check("nota", PdfTools.addAnnotation(context, texto, "note", "Revisar esta cláusula", 1, 400f, 700f, 24f, 24f, 0xFFD54F))
        check("resaltado", PdfTools.addAnnotation(context, texto, "highlight", "", 1, 70f, 735f, 250f, 18f, 0xFFEB3B))
        val fields = PdfTools.getFormFields(context, form)
        if (fields.map { it.name }.containsAll(listOf("nombre", "acepto"))) { ok++; Log.e(TAG, "OK   campos: ${fields.joinToString { it.name + ":" + it.type }}") }
        else { bad++; Log.e(TAG, "FALLO campos: $fields") }
        check("rellenar formulario", PdfTools.fillFormFields(context, form, mapOf("nombre" to "José Muñoz", "acepto" to "Sí"))) { uri ->
            val (t, _) = PdfTools.extractText(context, uri); if (t.contains("José Muñoz")) null else "el valor no quedó en el PDF" }
        val (txt, n) = PdfTools.extractText(context, texto)
        if (n == 3 && txt.contains("arrendamiento")) { ok++; Log.e(TAG, "OK   extraer texto (${txt.length} caracteres)") } else { bad++; Log.e(TAG, "FALLO extraer texto") }
        val hits = PdfTools.searchText(context, texto, "arrendamiento")
        if (hits.size >= 60) { ok++; Log.e(TAG, "OK   buscar: ${hits.size} coincidencias") } else { bad++; Log.e(TAG, "FALLO buscar: ${hits.size}") }
        check("imágenes → PDF", PdfTools.urisToPdf(context, listOf(u("foto.png"))))
        check("eliminar página 2", PdfTools.deletePagesList(context, texto, setOf(2))) { uri -> if (PdfTools.getPageCount(context, uri) == 2) null else "esperaba 2" }
        check("reordenar 3-1-2", PdfTools.reorderPages(context, texto, listOf(3, 1, 2)))
        // Censura REAL: la cédula no debe poder encontrarse después
        check("censura real", PdfTools.redactAreas(context, texto, listOf(PdfTools.RedactArea(1, 60f, 732f, 300f, 22f)))) { uri ->
            val left = PdfTools.searchText(context, uri, "1.098.765.432").count { it.first == 1 }
            val other = PdfTools.searchText(context, uri, "1.098.765.432").count { it.first == 2 }
            if (left == 0 && other == 1) null else "la cédula sigue en la página 1 ($left) o se tocó la 2 ($other)" }
        val info = PdfTools.getPdfPageInfo(context, rotada, 2)
        if (info.widthPt > info.heightPt) { ok++; Log.e(TAG, "OK   tamaño visible de página rotada: ${info.widthPt}×${info.heightPt}") } else { bad++; Log.e(TAG, "FALLO página rotada: $info") }
        val blocks = PdfTools.extractTextBlocks(context, texto, 1)
        if (blocks.any { it.text.contains("Cedula") }) { ok++; Log.e(TAG, "OK   bloques de texto: ${blocks.size}") } else { bad++; Log.e(TAG, "FALLO bloques: ${blocks.size}") }
        val first = blocks.firstOrNull { it.text.contains("Contrato") }
        check("editor: todo en una pasada", PdfTools.applyEditorChanges(context, texto, 1, PdfTools.EditorChanges(
            textEdits = listOfNotNull(first?.let { it to "Contrato MODIFICADO" }),
            images = listOf(PdfTools.ImageStamp(sig, 350f, 100f, 150f, 60f, true)),
            notes = listOf(PdfTools.NoteStamp("nota 1", 500f, 780f, 24f, 24f), PdfTools.NoteStamp("nota 2", 500f, 740f, 24f, 24f)),
            highlights = listOf(PdfTools.RectStamp(70f, 700f, 200f, 14f)),
            redactions = listOf(PdfTools.RectStamp(60f, 732f, 300f, 22f)),
        ))) { uri ->
            val t = PdfTools.extractText(context, uri).first
            val notes = PdfTools.countAnnotations(context, uri, 1)
            if (PdfTools.searchText(context, uri, "1.098.765.432").any { it.first == 1 }) "la censura del editor no borró el dato"
            else if (notes != 2) "esperaba 2 notas y hay $notes" else null }
        // PDF «trampa» con enlaces, marcadores y un campo de formulario que contienen el dato:
        // se revisan después los BYTES del archivo desde el PC (no solo el texto visible).
        val trampa = u("prueba-enlaces.pdf")
        if (File(dir, "prueba-enlaces.pdf").exists()) {
            check("trampa: censurar página 1", PdfTools.redactAreas(context, trampa,
                listOf(PdfTools.RedactArea(1, 60f, 732f, 320f, 24f), PdfTools.RedactArea(1, 60f, 515f, 260f, 34f)),
                customName = "trampa-censurada.pdf"))
            check("trampa: eliminar página 1", PdfTools.deletePagesList(context, trampa, setOf(1), outName = "trampa-sin-pag1.pdf")) { uri ->
                if (PdfTools.getPageCount(context, uri) == 2) null else "esperaba 2 páginas" }
        }
        Log.e(TAG, "=== RESULTADO: $ok OK · $bad FALLOS ===")
    }
}
