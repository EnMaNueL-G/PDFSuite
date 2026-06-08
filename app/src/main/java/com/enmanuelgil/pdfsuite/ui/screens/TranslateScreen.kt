package com.enmanuelgil.pdfsuite.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.pdfsuite.data.PdfTools
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(
    uri       : Uri,
    onDismiss : () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var text       by remember { mutableStateOf("") }
    var loading    by remember { mutableStateOf(false) }
    var pageRange  by remember { mutableStateOf("") }
    var sourceLang by remember { mutableStateOf("auto") }
    var targetLang by remember { mutableStateOf("es") }
    var showLangFrom by remember { mutableStateOf(false) }
    var showLangTo   by remember { mutableStateOf(false) }

    val languages = listOf(
        "auto" to "Detectar automáticamente",
        "es"   to "Español",
        "en"   to "Inglés",
        "fr"   to "Francés",
        "de"   to "Alemán",
        "it"   to "Italiano",
        "pt"   to "Portugués",
        "zh"   to "Chino",
        "ja"   to "Japonés",
        "ar"   to "Árabe",
        "ru"   to "Ruso",
        "ko"   to "Coreano",
    )
    fun langLabel(code: String) = languages.find { it.first == code }?.second ?: code

    // Extract text on open
    LaunchedEffect(uri) {
        loading = true
        text    = extractText(context, uri, null)
        loading = false
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                    Text("Extraer y traducir",
                        fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.weight(1f).padding(start = 4.dp))
                }
            }

            // ── Language selector ─────────────────────────────────────────────
            if (showLangFrom) {
                DropdownMenu(expanded = true, onDismissRequest = { showLangFrom = false }) {
                    languages.forEach { (code, name) ->
                        DropdownMenuItem(
                            text    = { Text(name, fontSize = 13.sp) },
                            onClick = { sourceLang = code; showLangFrom = false }
                        )
                    }
                }
            }
            if (showLangTo) {
                DropdownMenu(expanded = true, onDismissRequest = { showLangTo = false }) {
                    languages.filter { it.first != "auto" }.forEach { (code, name) ->
                        DropdownMenuItem(
                            text    = { Text(name, fontSize = 13.sp) },
                            onClick = { targetLang = code; showLangTo = false }
                        )
                    }
                }
            }
            Surface(
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Source language chip
                    OutlinedButton(
                        onClick  = { showLangFrom = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(langLabel(sourceLang), fontSize = 11.sp, maxLines = 1)
                    }
                    Icon(Icons.Default.ArrowForward, null,
                        Modifier.size(16.dp), tint = PdfRed)
                    // Target language chip
                    Button(
                        onClick  = { showLangTo = true },
                        colors   = ButtonDefaults.buttonColors(containerColor = PdfRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(langLabel(targetLang), fontSize = 11.sp, maxLines = 1)
                    }
                }
            }

            // ── Action buttons ────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Copy
                OutlinedButton(
                    onClick  = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Texto PDF", text))
                        Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
                    },
                    enabled  = text.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copiar", fontSize = 12.sp)
                }

                // Google Translate (with target language hint)
                Button(
                    onClick  = { openGoogleTranslate(context, text, targetLang) },
                    enabled  = text.isNotBlank(),
                    colors   = ButtonDefaults.buttonColors(containerColor = PdfRed),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Translate, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Traducir", fontSize = 12.sp)
                }
            }

            // ── Page filter ───────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = pageRange,
                    onValueChange = { pageRange = it },
                    label         = { Text("Páginas (ej: 1-3, vacío = todas)") },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                )
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            val range = parseRange(pageRange)
                            text    = extractText(context, uri, range)
                            loading = false
                        }
                    },
                    colors   = ButtonDefaults.buttonColors(containerColor = PdfRed)
                ) {
                    Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Text content ──────────────────────────────────────────────────
            Surface(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                shape           = RoundedCornerShape(12.dp),
                color           = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = PdfRed)
                    }
                    text.isBlank() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.TextSnippet, null,
                                Modifier.size(48.dp), tint = PdfRed.copy(0.4f))
                            Text("No se encontró texto extraíble",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("El PDF puede ser basado en imágenes (escaneado).",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> SelectionContainer {
                        Text(
                            text     = text,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

private suspend fun extractText(context: Context, uri: Uri, pages: IntRange?): String =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val inp    = context.contentResolver.openInputStream(uri) ?: return@withContext ""
            val reader = com.itextpdf.text.pdf.PdfReader(inp)
            val total  = reader.numberOfPages
            val range  = pages ?: (1..total)
            val sb     = StringBuilder()
            for (p in range) {
                if (p < 1 || p > total) continue
                val pageText = com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(reader, p)
                if (pageText.isNotBlank()) {
                    sb.append("── Página $p ──\n")
                    sb.append(pageText.trim())
                    sb.append("\n\n")
                }
            }
            reader.close()
            inp.close()
            sb.toString().trim()
        } catch (e: Exception) { "" }
    }

private fun parseRange(input: String): IntRange? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    return try {
        if ('-' in trimmed) {
            val parts = trimmed.split('-')
            parts[0].trim().toInt()..parts[1].trim().toInt()
        } else {
            val n = trimmed.toInt()
            n..n
        }
    } catch (e: Exception) { null }
}

private fun openGoogleTranslate(context: Context, text: String, targetLang: String = "es") {
    val chunk = text.take(4900)  // Google Translate URL limit ~5000 chars
    // Try Google Translate app first
    val appIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, chunk)
        setPackage("com.google.android.apps.translate")
    }
    try {
        context.startActivity(appIntent)
        return
    } catch (_: Exception) {}

    // Fallback: share to any translation app
    val shareIntent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, chunk)
            putExtra(Intent.EXTRA_TITLE, "Traducir texto PDF")
        }, "Traducir con…"
    )
    try {
        context.startActivity(shareIntent)
    } catch (_: Exception) {}
}
