package com.enmanuelgil.pdfsuite.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.ToolsViewModel

enum class TextToolTab { SEARCH, EXTRACT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToolScreen(
    uri       : Uri,
    vm        : ToolsViewModel = viewModel(),
    onDismiss : () -> Unit
) {
    val context   = LocalContext.current
    val isWorking by vm.isWorking.collectAsState()
    val searchHits by vm.searchHits.collectAsState()
    val extracted  by vm.extractedText.collectAsState()

    var tab       by remember { mutableStateOf(TextToolTab.SEARCH) }
    var query     by remember { mutableStateOf("") }
    val keyboard  = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        vm.clearSearchHits()
        vm.clearExtractedText()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Cerrar")
                        }
                        Text("Texto del PDF",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            modifier   = Modifier.weight(1f).padding(start = 4.dp))
                    }
                    TabRow(
                        selectedTabIndex = tab.ordinal,
                        containerColor   = MaterialTheme.colorScheme.surface,
                        contentColor     = PdfRed
                    ) {
                        Tab(selected = tab == TextToolTab.SEARCH,
                            onClick  = { tab = TextToolTab.SEARCH; vm.clearSearchHits() },
                            text     = { Text("Buscar") },
                            icon     = { Icon(Icons.Default.Search, null) })
                        Tab(selected = tab == TextToolTab.EXTRACT,
                            onClick  = {
                                tab = TextToolTab.EXTRACT
                                if (extracted.isEmpty()) vm.extractText(context, uri)
                            },
                            text     = { Text("Extraer todo") },
                            icon     = { Icon(Icons.Default.TextFields, null) })
                    }
                }
            }

            // ── Content by tab ────────────────────────────────────────────────
            when (tab) {

                TextToolTab.SEARCH -> {
                    // Search bar
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value         = query,
                            onValueChange = { query = it },
                            modifier      = Modifier.weight(1f),
                            placeholder   = { Text("Buscar en el documento…") },
                            leadingIcon   = { Icon(Icons.Default.Search, null) },
                            trailingIcon  = if (query.isNotEmpty()) {{
                                IconButton(onClick = { query = ""; vm.clearSearchHits() }) {
                                    Icon(Icons.Default.Clear, "Limpiar")
                                }
                            }} else null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboard?.hide()
                                if (query.isNotBlank()) vm.searchText(context, uri, query)
                            }),
                            singleLine    = true,
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PdfRed
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Button(
                            onClick = {
                                keyboard?.hide()
                                if (query.isNotBlank()) vm.searchText(context, uri, query)
                            },
                            colors  = ButtonDefaults.buttonColors(containerColor = PdfRed),
                            enabled = query.isNotBlank() && !isWorking
                        ) {
                            Text("Buscar")
                        }
                    }

                    if (isWorking) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PdfRed)
                                Spacer(Modifier.height(8.dp))
                                Text("Buscando…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else if (searchHits.isEmpty() && query.isNotEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SearchOff, null,
                                    tint     = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Sin resultados para \"$query\"",
                                    fontSize = 15.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Prueba con otra palabra clave",
                                    fontSize = 13.sp,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else if (searchHits.isNotEmpty()) {
                        Text(
                            "${searchHits.size} resultado(s) para \"$query\"",
                            modifier  = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            fontSize  = 13.sp,
                            color     = PdfRed,
                            fontWeight= FontWeight.Medium
                        )
                        LazyColumn(
                            contentPadding      = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchHits) { (page, snippet) ->
                                SearchHitCard(page = page, snippet = snippet, query = query)
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ManageSearch, null,
                                    tint     = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(72.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Busca palabras o frases", fontSize = 15.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Funciona con PDFs que contengan texto seleccionable",
                                    fontSize  = 13.sp,
                                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier  = Modifier.padding(horizontal = 32.dp))
                            }
                        }
                    }
                }

                TextToolTab.EXTRACT -> {
                    if (isWorking) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PdfRed)
                                Spacer(Modifier.height(8.dp))
                                Text("Extrayendo texto…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else if (extracted.isBlank()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.TextSnippet, null,
                                    tint     = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No hay texto extraíble", fontSize = 15.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Este PDF puede ser una imagen escaneada.\n" +
                                    "Usa un OCR externo para reconocer el texto.",
                                    fontSize  = 13.sp,
                                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier  = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    } else {
                        Column {
                            // Action bar
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { vm.shareText(context, extracted) }) {
                                    Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Exportar texto")
                                }
                            }
                            // Text content
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp, bottom = 32.dp)
                            ) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 1.dp
                                    ) {
                                        Text(
                                            extracted,
                                            modifier   = Modifier.padding(16.dp).fillMaxWidth(),
                                            fontSize   = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color      = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHitCard(page: Int, snippet: String, query: String) {
    Card(
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.background(PdfRed, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Pág. $page", color = Color.White,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.FindInPage, null,
                    tint     = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(6.dp))
            // Highlight query in snippet
            val lq     = query.lowercase()
            val lsnip  = snippet.lowercase()
            val idx    = lsnip.indexOf(lq)
            val annotated = if (idx >= 0) buildAnnotatedString {
                append(snippet.substring(0, idx))
                withStyle(SpanStyle(
                    background = PdfRed.copy(alpha = 0.2f),
                    fontWeight = FontWeight.Bold,
                    color      = PdfRed
                )) {
                    append(snippet.substring(idx, idx + query.length))
                }
                append(snippet.substring(idx + query.length))
            } else buildAnnotatedString { append(snippet) }

            Text(annotated, fontSize = 13.sp, lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
