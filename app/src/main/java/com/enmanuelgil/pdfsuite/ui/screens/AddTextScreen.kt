package com.enmanuelgil.pdfsuite.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.pdfsuite.model.ToolResult
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.ToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTextScreen(
    uri       : Uri,
    pageCount : Int,
    vm        : ToolsViewModel = viewModel(),
    onDismiss : () -> Unit
) {
    val context   = LocalContext.current
    val result    by vm.result.collectAsState()
    val isWorking by vm.isWorking.collectAsState()

    var text      by remember { mutableStateOf("") }
    var pageNum   by remember { mutableStateOf("1") }
    var xPos      by remember { mutableStateOf("72") }
    var yPos      by remember { mutableStateOf("72") }
    var fontSize  by remember { mutableStateOf("14") }
    var colorHex  by remember { mutableStateOf(0x000000) }

    val colorOptions = listOf(
        0x000000 to "Negro",
        0x1565C0 to "Azul",
        0x388E3C to "Verde",
        0xD32F2F to "Rojo",
        0xF57F17 to "Naranja",
        0x6A1B9A to "Morado"
    )

    // Result dialog
    if (result is ToolResult.Success || result is ToolResult.Error) {
        AlertDialog(
            onDismissRequest = { vm.clearResult(); onDismiss() },
            icon = {
                Icon(
                    if (result is ToolResult.Success) Icons.Default.CheckCircle
                    else Icons.Default.Error, null,
                    tint = if (result is ToolResult.Success) Color(0xFF43A047)
                           else MaterialTheme.colorScheme.error
                )
            },
            title = { Text(if (result is ToolResult.Success) "Texto añadido" else "Error") },
            text  = {
                Text(when (result) {
                    is ToolResult.Success -> (result as ToolResult.Success).message
                    is ToolResult.Error   -> (result as ToolResult.Error).message
                    else -> ""
                })
            },
            confirmButton = {
                if (result is ToolResult.Success) {
                    Button(onClick = { vm.shareResult(context); vm.clearResult(); onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = PdfRed)) {
                        Text("Compartir")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.clearResult(); if (result is ToolResult.Success) onDismiss() }) {
                    Text("Cerrar")
                }
            }
        )
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
                    Text("Agregar texto", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.weight(1f).padding(start = 4.dp))
                    Button(
                        onClick = {
                            if (text.isNotBlank()) {
                                vm.addTextOverlay(
                                    context  = context,
                                    uri      = uri,
                                    text     = text,
                                    pageNum  = pageNum.toIntOrNull() ?: 1,
                                    x        = xPos.toFloatOrNull() ?: 72f,
                                    y        = yPos.toFloatOrNull() ?: 72f,
                                    fontSize = fontSize.toFloatOrNull() ?: 14f,
                                    colorHex = colorHex
                                )
                            }
                        },
                        enabled = text.isNotBlank() && !isWorking,
                        colors  = ButtonDefaults.buttonColors(containerColor = PdfRed),
                        modifier= Modifier.padding(end = 8.dp)
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(Modifier.size(16.dp),
                                color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AddCircle, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Añadir")
                        }
                    }
                }
            }

            // ── Form ──────────────────────────────────────────────────────────
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info card
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, null,
                            tint     = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp).padding(top = 1.dp))
                        Text(
                            "El texto se añade en coordenadas absolutas (puntos PDF). " +
                            "El origen (0,0) está en la esquina inferior-izquierda de la página.",
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Text input
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    label         = { Text("Texto a añadir *") },
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text("Escribe el texto aquí…") },
                    minLines      = 3,
                    maxLines      = 6,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PdfRed,
                        focusedLabelColor  = PdfRed
                    )
                )

                // Page number
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value         = pageNum,
                        onValueChange = { pageNum = it.filter { c -> c.isDigit() } },
                        label         = { Text("Página") },
                        suffix        = { Text("/ $pageCount") },
                        modifier      = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                    )
                    OutlinedTextField(
                        value         = fontSize,
                        onValueChange = { fontSize = it.filter { c -> c.isDigit() } },
                        label         = { Text("Tamaño") },
                        suffix        = { Text("pt") },
                        modifier      = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                    )
                }

                // Position
                Text("Posición", fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value         = xPos,
                        onValueChange = { xPos = it.filter { c -> c.isDigit() } },
                        label         = { Text("X (desde izquierda)") },
                        suffix        = { Text("pt") },
                        modifier      = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                    )
                    OutlinedTextField(
                        value         = yPos,
                        onValueChange = { yPos = it.filter { c -> c.isDigit() } },
                        label         = { Text("Y (desde abajo)") },
                        suffix        = { Text("pt") },
                        modifier      = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                    )
                }

                // Color picker
                Text("Color del texto", fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colorOptions.forEach { (hex, label) ->
                        val isSelected = hex == colorHex
                        val c = Color(0xFF000000.or(hex.toLong()))
                        Surface(
                            onClick  = { colorHex = hex },
                            shape    = RoundedCornerShape(8.dp),
                            color    = c,
                            modifier = Modifier.size(40.dp),
                            border   = if (isSelected)
                                androidx.compose.foundation.BorderStroke(3.dp, Color.Black.copy(0.3f))
                            else null
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, label,
                                        tint     = Color.White,
                                        modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                // Preview
                if (text.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Vista previa", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text     = text,
                                fontSize = (fontSize.toIntOrNull() ?: 14).coerceIn(8, 40).sp,
                                color    = Color(0xFF000000.or(colorHex.toLong()))
                            )
                        }
                    }
                }
            }
        }
    }
}
