package com.enmanuelgil.pdfsuite.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.ToolsViewModel

/** Annotation type: sticky note, free text overlay, or highlight region */
enum class AnnotationType(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    NOTE("Nota", Icons.Default.StickyNote2),
    FREETEXT("Texto libre", Icons.Default.TextFields),
    HIGHLIGHT("Resaltar región", Icons.Default.Highlight)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotateScreen(
    uri       : Uri,
    pageCount : Int,
    vm        : ToolsViewModel,
    onDismiss : () -> Unit
) {
    val context  = LocalContext.current

    var annotType   by remember { mutableStateOf(AnnotationType.NOTE) }
    var text        by remember { mutableStateOf("") }
    var pageNum     by remember { mutableStateOf("1") }
    var xPos        by remember { mutableStateOf("72") }
    var yPos        by remember { mutableStateOf("500") }
    var width       by remember { mutableStateOf("200") }
    var height      by remember { mutableStateOf("60") }
    var selectedColor by remember { mutableStateOf(0xFFFF00) }

    val colorOptions = listOf(
        0xFFFF00 to Color(0xFFFFFF00),   // Yellow
        0xFF7043 to Color(0xFFFF7043),   // Orange
        0xEF5350 to Color(0xFFEF5350),   // Red
        0x42A5F5 to Color(0xFF42A5F5),   // Blue
        0x66BB6A to Color(0xFF66BB6A),   // Green
        0xAB47BC to Color(0xFFAB47BC),   // Purple
    )

    val needsText = annotType != AnnotationType.HIGHLIGHT
    val canAdd    = (!needsText || text.isNotBlank()) &&
                   pageNum.toIntOrNull() in 1..pageCount.coerceAtLeast(1)

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
                    Text(
                        "Anotaciones",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        modifier   = Modifier.weight(1f).padding(start = 4.dp)
                    )
                    Button(
                        onClick  = {
                            val pg = pageNum.toIntOrNull()?.coerceIn(1, pageCount.coerceAtLeast(1)) ?: 1
                            val px = xPos.toFloatOrNull()   ?: 72f
                            val py = yPos.toFloatOrNull()   ?: 500f
                            val pw = width.toFloatOrNull()  ?: 200f
                            val ph = height.toFloatOrNull() ?: 60f
                            vm.addAnnotation(
                                context, uri,
                                type     = annotType.name.lowercase(),
                                text     = text,
                                pageNum  = pg,
                                x = px, y = py, width = pw, height = ph,
                                colorHex = selectedColor
                            )
                            onDismiss()
                        },
                        enabled  = canAdd,
                        colors   = ButtonDefaults.buttonColors(containerColor = PdfRed),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Añadir")
                    }
                }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Annotation type selector ──────────────────────────────────
                Text("Tipo de anotación", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnnotationType.values().forEach { type ->
                        val sel = type == annotType
                        FilterChip(
                            selected = sel,
                            onClick  = { annotType = type },
                            label    = { Text(type.label, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(type.icon, null, Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PdfRed.copy(alpha = 0.15f),
                                selectedLabelColor     = PdfRed
                            )
                        )
                    }
                }

                // ── Text field ────────────────────────────────────────────────
                if (annotType != AnnotationType.HIGHLIGHT) {
                    OutlinedTextField(
                        value         = text,
                        onValueChange = { text = it },
                        label         = {
                            Text(if (annotType == AnnotationType.NOTE)
                                "Contenido de la nota *" else "Texto a mostrar *")
                        },
                        modifier      = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        maxLines      = 5,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PdfRed
                        )
                    )
                }

                // ── Page number ───────────────────────────────────────────────
                OutlinedTextField(
                    value         = pageNum,
                    onValueChange = { if (it.length <= 4) pageNum = it.filter { c -> c.isDigit() } },
                    label         = { Text("Página (1–$pageCount)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                )

                // ── Position ──────────────────────────────────────────────────
                Text("Posición en la página (puntos PDF)",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = xPos, onValueChange = { xPos = it.filter { c -> c.isDigit() } },
                        label = { Text("X") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors   = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                    )
                    OutlinedTextField(
                        value = yPos, onValueChange = { yPos = it.filter { c -> c.isDigit() } },
                        label = { Text("Y") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors   = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                    )
                }

                // ── Size (for highlight / freetext) ───────────────────────────
                if (annotType != AnnotationType.NOTE) {
                    Text("Tamaño (ancho × alto)",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = width, onValueChange = { width = it.filter { c -> c.isDigit() } },
                            label = { Text("Ancho pt") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors   = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                        )
                        OutlinedTextField(
                            value = height, onValueChange = { height = it.filter { c -> c.isDigit() } },
                            label = { Text("Alto pt") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors   = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                        )
                    }
                }

                // ── Color picker ──────────────────────────────────────────────
                Text("Color", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colorOptions.forEach { (hex, color) ->
                        val sel = hex == selectedColor
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(color)
                                .then(
                                    if (sel) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface,
                                        RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (sel) Icon(Icons.Default.Check, null, Modifier.size(18.dp),
                                tint = if (hex == 0xFFFF00) Color.Black else Color.White)
                        }
                    }
                }

                // ── Info card ─────────────────────────────────────────────────
                Surface(
                    color  = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape  = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, null,
                            Modifier.size(16.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text(
                            when (annotType) {
                                AnnotationType.NOTE ->
                                    "La nota aparece como globo de comentario en el lector PDF."
                                AnnotationType.FREETEXT ->
                                    "El texto se muestra directamente sobre la página."
                                AnnotationType.HIGHLIGHT ->
                                    "Se dibuja un rectángulo semitransparente sobre la región indicada."
                            },
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
