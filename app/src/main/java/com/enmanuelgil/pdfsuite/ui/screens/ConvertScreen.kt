package com.enmanuelgil.pdfsuite.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.ToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(
    vm        : ToolsViewModel,
    onDismiss : () -> Unit
) {
    val context = LocalContext.current
    var tab     by remember { mutableStateOf(0) }   // 0 = Img→PDF  1 = Office→PDF hint

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                        Text("Conversor de formatos",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            modifier = Modifier.weight(1f).padding(start = 4.dp))
                    }
                    TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                        Tab(selected = tab == 0, onClick = { tab = 0 },
                            text = { Text("Imágenes → PDF", fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Image, null, Modifier.size(18.dp)) })
                        Tab(selected = tab == 1, onClick = { tab = 1 },
                            text = { Text("Documentos Office", fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Description, null, Modifier.size(18.dp)) })
                    }
                }
            }

            when (tab) {
                0 -> ImagesToPdfTab(vm, onDismiss)
                1 -> OfficeToPdfTab(context)
            }
        }
    }
}

// ── Tab 1: Images → PDF ───────────────────────────────────────────────────────

@Composable
private fun ImagesToPdfTab(vm: ToolsViewModel, onDismiss: () -> Unit) {
    val context    = LocalContext.current
    val images     = remember { mutableStateListOf<Uri>() }
    var outName    by remember { mutableStateOf("imagenes.pdf") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> images.addAll(uris) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pick button
        Button(
            onClick  = { picker.launch(arrayOf("image/*", "image/jpeg", "image/png", "image/webp")) },
            colors   = ButtonDefaults.buttonColors(containerColor = PdfRed),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Seleccionar imágenes")
        }

        if (images.isNotEmpty()) {
            Text("${images.size} imagen(es) seleccionada(s)",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Scrollable list
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(images) { idx, uri ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Image, null, Modifier.size(20.dp),
                                tint = PdfRed)
                            Text(
                                uri.lastPathSegment?.substringAfterLast('/') ?: "Imagen ${idx+1}",
                                fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1
                            )
                            IconButton(onClick = { images.removeAt(idx) },
                                modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, "Quitar", Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value         = outName,
                onValueChange = { outName = it },
                label         = { Text("Nombre del archivo de salida") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                trailingIcon  = { Text(".pdf", fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
            )

            Button(
                onClick  = {
                    val name = if (outName.endsWith(".pdf")) outName else "$outName.pdf"
                    vm.imagesToPdf(context, images.toList(), name)
                    onDismiss()
                },
                enabled  = images.isNotEmpty() && outName.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = PdfRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Convertir a PDF")
            }
        } else {
            // Empty state
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AddPhotoAlternate, null,
                        Modifier.size(64.dp), tint = PdfRed.copy(alpha = 0.4f))
                    Text("Agrega imágenes JPG, PNG o WEBP",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text("Se convertirán en un PDF de una imagen por página",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Tab 2: Office documents → PDF (guidance) ─────────────────────────────────

@Composable
private fun OfficeToPdfTab(context: android.content.Context) {
    val steps = listOf(
        Triple(Icons.Default.FolderOpen,  "Abre el archivo",
            "Toca el botón de abajo para abrir tu documento Word, Excel o PowerPoint con la app de Office correspondiente."),
        Triple(Icons.Default.Print,        "Imprimir a PDF",
            "Dentro de la app de Office: Menú → Imprimir → Selecciona 'Guardar como PDF' o 'PDF virtual'."),
        Triple(Icons.Default.Save,         "Guardar y usar",
            "El PDF generado por Office quedará en tu almacenamiento. Ábrelo con PDFSuite para editarlo.")
    )

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color  = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape  = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, null, Modifier.size(18.dp).padding(top = 2.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "La conversión de DOCX / XLSX / PPTX a PDF requiere Microsoft Office, Google Docs o WPS Office instalado en el dispositivo.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(steps.size) { idx ->
            val (icon, title, desc) = steps[idx]
            Surface(
                shape           = RoundedCornerShape(12.dp),
                color           = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier        = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                            .background(PdfRed.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${idx+1}", fontWeight = FontWeight.Bold, color = PdfRed, fontSize = 14.sp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(desc, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            // Open file browser
            Button(
                onClick  = {
                    try {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-powerpoint",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                            ))
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) { /* no office app */ }
                },
                colors   = ButtonDefaults.buttonColors(containerColor = PdfRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Abrir documento Office")
            }
        }
    }
}
