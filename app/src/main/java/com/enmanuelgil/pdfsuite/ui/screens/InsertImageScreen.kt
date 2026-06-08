package com.enmanuelgil.pdfsuite.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.ToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsertImageScreen(
    pdfUri    : Uri,
    pageCount : Int,
    vm        : ToolsViewModel,
    onDismiss : () -> Unit
) {
    val context = LocalContext.current

    var imageUri  by remember { mutableStateOf<Uri?>(null) }
    var pageNum   by remember { mutableStateOf("1") }
    var xPos      by remember { mutableStateOf("72") }
    var yPos      by remember { mutableStateOf("200") }
    var maxWidth  by remember { mutableStateOf("300") }
    var maxHeight by remember { mutableStateOf("300") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { imageUri = it } }

    val canInsert = imageUri != null &&
                   (pageNum.toIntOrNull() ?: 0) in 1..pageCount.coerceAtLeast(1)

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
                        "Insertar imagen",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        modifier   = Modifier.weight(1f).padding(start = 4.dp)
                    )
                    Button(
                        onClick  = {
                            val img = imageUri ?: return@Button
                            val pg  = pageNum.toIntOrNull()?.coerceIn(1, pageCount.coerceAtLeast(1)) ?: 1
                            val px  = xPos.toFloatOrNull()      ?: 72f
                            val py  = yPos.toFloatOrNull()      ?: 200f
                            val pw  = maxWidth.toFloatOrNull()  ?: 300f
                            val ph  = maxHeight.toFloatOrNull() ?: 300f
                            vm.insertImageFromUri(context, pdfUri, img, pg, px, py, pw, ph)
                            onDismiss()
                        },
                        enabled  = canInsert,
                        colors   = ButtonDefaults.buttonColors(containerColor = PdfRed),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Insertar")
                    }
                }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Image picker ──────────────────────────────────────────────
                Text("Imagen a insertar", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                if (imageUri == null) {
                    // Pick image button
                    Surface(
                        onClick         = { imagePicker.launch("image/*") },
                        shape           = RoundedCornerShape(12.dp),
                        color           = MaterialTheme.colorScheme.surfaceVariant,
                        modifier        = Modifier.fillMaxWidth().height(160.dp)
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement   = Arrangement.Center,
                            horizontalAlignment   = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, null,
                                Modifier.size(48.dp), tint = PdfRed)
                            Spacer(Modifier.height(8.dp))
                            Text("Toca para seleccionar imagen",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    // Preview + change button
                    Box(Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model              = imageUri,
                            contentDescription = "Vista previa",
                            contentScale       = ContentScale.Fit,
                            modifier           = Modifier.fillMaxWidth().height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        IconButton(
                            onClick  = { imagePicker.launch("image/*") },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            ) {
                                Icon(Icons.Default.Edit, "Cambiar imagen",
                                    Modifier.padding(6.dp).size(20.dp), tint = PdfRed)
                            }
                        }
                    }
                }

                // ── Page number ───────────────────────────────────────────────
                OutlinedTextField(
                    value         = pageNum,
                    onValueChange = { pageNum = it.filter { c -> c.isDigit() } },
                    label         = { Text("Página (1–$pageCount)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                )

                // ── Position ──────────────────────────────────────────────────
                Text("Posición inferior-izquierda (puntos PDF)",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("El origen (0,0) está en la esquina inferior-izquierda de la página.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = xPos, onValueChange = { xPos = it.filter { c -> c.isDigit() } },
                        label = { Text("X (desde izq.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors   = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                    )
                    OutlinedTextField(
                        value = yPos, onValueChange = { yPos = it.filter { c -> c.isDigit() } },
                        label = { Text("Y (desde abajo)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors   = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                    )
                }

                // ── Max size ──────────────────────────────────────────────────
                Text("Tamaño máximo (proporción preservada)",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = maxWidth, onValueChange = { maxWidth = it.filter { c -> c.isDigit() } },
                        label = { Text("Max ancho pt") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors   = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                    )
                    OutlinedTextField(
                        value = maxHeight, onValueChange = { maxHeight = it.filter { c -> c.isDigit() } },
                        label = { Text("Max alto pt") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors   = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                    )
                }

                // ── Hint ──────────────────────────────────────────────────────
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
                            "Una página A4 mide 595 × 842 puntos. Ejemplo: X=72, Y=400 coloca la imagen a ~2.5 cm de los bordes.",
                            fontSize = 12.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
