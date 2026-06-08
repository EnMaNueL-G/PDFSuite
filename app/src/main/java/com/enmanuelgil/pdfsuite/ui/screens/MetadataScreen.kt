package com.enmanuelgil.pdfsuite.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.pdfsuite.data.PdfTools
import com.enmanuelgil.pdfsuite.model.PdfMetadata
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.ToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataScreen(
    uri       : Uri,
    vm        : ToolsViewModel = viewModel(),
    onDismiss : () -> Unit
) {
    val context  = LocalContext.current
    val meta     by vm.metadata.collectAsState()

    LaunchedEffect(uri) {
        vm.clearMetadata()
        vm.loadMetadata(context, uri)
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
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Cerrar")
                    }
                    Text("Metadatos del PDF",
                        fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier   = Modifier.weight(1f).padding(start = 4.dp))
                }
            }

            if (meta == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PdfRed)
                }
            } else {
                val m = meta!!
                LazyColumn(contentPadding = PaddingValues(16.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)) {

                    // Summary card
                    item {
                        Card(
                            shape  = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = PdfRed.copy(alpha = 0.08f))
                        ) {
                            Row(Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment     = Alignment.CenterVertically) {
                                Icon(Icons.Default.PictureAsPdf, null,
                                    tint     = PdfRed,
                                    modifier = Modifier.size(40.dp))
                                Column {
                                    Text("${m.pageCount} página(s)",
                                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(PdfTools.fmtSize(m.sizeBytes),
                                        fontSize = 13.sp,
                                        color    = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (m.encrypted) {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.Lock, null,
                                                tint     = Color(0xFF8E24AA),
                                                modifier = Modifier.size(14.dp))
                                            Text("Cifrado (AES)",
                                                fontSize = 12.sp,
                                                color    = Color(0xFF8E24AA))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Metadata rows
                    item {
                        Card(shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(1.dp)) {
                            Column(Modifier.padding(4.dp)) {
                                MetaRow(Icons.Default.Title, "Título",
                                    m.title.ifBlank { "—" })
                                Divider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                MetaRow(Icons.Default.Person, "Autor",
                                    m.author.ifBlank { "—" })
                                Divider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                MetaRow(Icons.Default.Subject, "Tema",
                                    m.subject.ifBlank { "—" })
                                Divider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                MetaRow(Icons.Default.Build, "Creador",
                                    m.creator.ifBlank { "—" })
                                Divider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                MetaRow(Icons.Default.Print, "Productor",
                                    m.producer.ifBlank { "—" })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = PdfRed, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium)
            Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
