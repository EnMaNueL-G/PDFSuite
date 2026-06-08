package com.enmanuelgil.pdfsuite.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.pdfsuite.data.PdfTools
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.ToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedactScreen(
    uri       : Uri,
    pageCount : Int,
    vm        : ToolsViewModel,
    onDismiss : () -> Unit
) {
    val context = LocalContext.current
    val areas   = remember { mutableStateListOf<PdfTools.RedactArea>() }

    // Input state for new area
    var page    by remember { mutableStateOf("1") }
    var x       by remember { mutableStateOf("72") }
    var y       by remember { mutableStateOf("700") }
    var w       by remember { mutableStateOf("200") }
    var h       by remember { mutableStateOf("20") }
    var label   by remember { mutableStateOf("CONFIDENCIAL") }

    val validInput = (page.toIntOrNull() ?: 0) in 1..pageCount.coerceAtLeast(1) &&
                    (w.toFloatOrNull() ?: 0f) > 0 && (h.toFloatOrNull() ?: 0f) > 0

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
                    Text("Redactar / Censurar",
                        fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.weight(1f).padding(start = 4.dp))
                    Button(
                        onClick  = {
                            vm.redactAreas(context, uri, areas.toList())
                            onDismiss()
                        },
                        enabled  = areas.isNotEmpty(),
                        colors   = ButtonDefaults.buttonColors(containerColor = PdfRed),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.VisibilityOff, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Aplicar (${areas.size})")
                    }
                }
            }

            LazyColumn(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Add area form ─────────────────────────────────────────────
                item {
                    Surface(
                        shape           = RoundedCornerShape(14.dp),
                        color           = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp,
                        modifier        = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Nueva área a censurar",
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                            OutlinedTextField(
                                value = page, onValueChange = { page = it.filter { c -> c.isDigit() } },
                                label = { Text("Página (1–$pageCount)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                            )

                            Text("Posición (X, Y desde abajo-izquierda)",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = x, onValueChange = { x = it.filter { c -> c.isDigit() } },
                                    label = { Text("X") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                                )
                                OutlinedTextField(
                                    value = y, onValueChange = { y = it.filter { c -> c.isDigit() } },
                                    label = { Text("Y") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                                )
                            }

                            Text("Tamaño (ancho × alto)",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = w, onValueChange = { w = it.filter { c -> c.isDigit() } },
                                    label = { Text("Ancho pt") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                                )
                                OutlinedTextField(
                                    value = h, onValueChange = { h = it.filter { c -> c.isDigit() } },
                                    label = { Text("Alto pt") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                                )
                            }

                            OutlinedTextField(
                                value = label, onValueChange = { label = it },
                                label = { Text("Etiqueta (opcional)") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                            )

                            Button(
                                onClick = {
                                    if (validInput) {
                                        areas.add(PdfTools.RedactArea(
                                            pageNum = page.toInt(),
                                            x = x.toFloat(), y = y.toFloat(),
                                            w = w.toFloat(), h = h.toFloat(),
                                            label = label
                                        ))
                                    }
                                },
                                enabled  = validInput,
                                colors   = ButtonDefaults.buttonColors(containerColor = PdfRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Agregar área")
                            }
                        }
                    }
                }

                // ── Info hint ─────────────────────────────────────────────────
                item {
                    Surface(
                        color  = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape  = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Info, null, Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "El origen (0,0) está en la esquina inferior-izquierda. Una página A4 mide 595×842 pt. Ejemplo: un número de cédula a mitad de página sería X=200, Y=400, Ancho=150, Alto=15.",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ── Areas list ────────────────────────────────────────────────
                if (areas.isNotEmpty()) {
                    item {
                        Text("Áreas programadas (${areas.size})",
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    itemsIndexed(areas) { idx, area ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.VisibilityOff, null,
                                        Modifier.size(18.dp), tint = Color.White)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Pág. ${area.pageNum}  •  ${area.label}",
                                        fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text("X=${area.x.toInt()}, Y=${area.y.toInt()}, ${area.w.toInt()}×${area.h.toInt()} pt",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { areas.removeAt(idx) },
                                    modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, null,
                                        Modifier.size(18.dp), tint = PdfRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
