package com.enmanuelgil.pdfsuite.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.pdfsuite.model.PdfFormField
import com.enmanuelgil.pdfsuite.model.ToolResult
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.ToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormFillScreen(
    uri       : Uri,
    vm        : ToolsViewModel = viewModel(),
    onDismiss : () -> Unit
) {
    val context    = LocalContext.current
    val fields     by vm.formFields.collectAsState()
    val result     by vm.result.collectAsState()
    val isWorking  by vm.isWorking.collectAsState()

    // Mutable map of field name → current value
    val values = remember { mutableStateMapOf<String, String>() }

    // Load fields on first composition
    LaunchedEffect(uri) {
        vm.clearFormFields()
        vm.loadFormFields(context, uri)
    }

    // When fields load, populate values map
    LaunchedEffect(fields) {
        for (f in fields) {
            if (!values.containsKey(f.name)) values[f.name] = f.value
        }
    }

    // Show success/error dialog
    if (result is ToolResult.Success || result is ToolResult.Error) {
        AlertDialog(
            onDismissRequest = { vm.clearResult(); onDismiss() },
            icon = {
                Icon(
                    if (result is ToolResult.Success) Icons.Default.CheckCircle else Icons.Default.Error,
                    null,
                    tint = if (result is ToolResult.Success) Color(0xFF43A047)
                           else MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(if (result is ToolResult.Success) "¡Formulario guardado!" else "Error")
            },
            text = {
                Text(
                    when (result) {
                        is ToolResult.Success -> (result as ToolResult.Success).message
                        is ToolResult.Error   -> (result as ToolResult.Error).message
                        else                  -> ""
                    }
                )
            },
            confirmButton = {
                if (result is ToolResult.Success) {
                    Button(
                        onClick = { vm.shareResult(context); vm.clearResult(); onDismiss() },
                        colors  = ButtonDefaults.buttonColors(containerColor = PdfRed)
                    ) { Text("Compartir") }
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
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Cerrar")
                    }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("Rellenar formulario", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (fields.isNotEmpty())
                            Text("${fields.size} campo(s) detectado(s)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            vm.fillFormFields(context, uri, values.toMap())
                        },
                        enabled = !isWorking && fields.isNotEmpty(),
                        colors  = ButtonDefaults.buttonColors(containerColor = PdfRed),
                        modifier= Modifier.padding(end = 8.dp)
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(Modifier.size(16.dp),
                                color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Guardar")
                        }
                    }
                }
            }

            // ── Content ───────────────────────────────────────────────────────
            when {
                fields.isEmpty() && !isWorking -> {
                    // No fields found
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Assignment, null,
                                tint     = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Sin campos de formulario",
                                fontWeight = FontWeight.Medium,
                                fontSize   = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Este PDF no contiene formularios AcroForm rellenables.\n" +
                                "Usa la herramienta «Agregar texto» para escribir sobre el PDF.",
                                fontSize = 13.sp,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign= androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = PdfRed.copy(alpha = 0.08f)
                                )
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Info, null,
                                        tint     = PdfRed,
                                        modifier = Modifier.size(18.dp))
                                    Text(
                                        "El PDF resultante tendrá los campos integrados (flattened) para mayor compatibilidad.",
                                        fontSize = 12.sp,
                                        color    = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        items(fields) { field ->
                            FormFieldItem(
                                field    = field,
                                value    = values[field.name] ?: field.value,
                                onChange = { values[field.name] = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormFieldItem(
    field    : PdfFormField,
    value    : String,
    onChange : (String) -> Unit
) {
    val icon = when (field.type) {
        "checkbox"  -> Icons.Default.CheckBox
        "combo",
        "list"      -> Icons.Default.ArrowDropDown
        "signature" -> Icons.Default.Draw
        else        -> Icons.Default.Edit
    }

    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation= CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = PdfRed, modifier = Modifier.size(18.dp))
                Text(field.name,
                    fontWeight = FontWeight.Medium,
                    fontSize   = 14.sp,
                    modifier   = Modifier.weight(1f))
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(field.type,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color    = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.height(8.dp))
            when (field.type) {
                "checkbox" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = value.lowercase() in listOf("true", "yes", "on", "1"),
                            onCheckedChange = { onChange(if (it) "Yes" else "Off") },
                            colors = CheckboxDefaults.colors(checkedColor = PdfRed)
                        )
                        Text(if (value.lowercase() in listOf("true", "yes", "on", "1"))
                            "Marcado" else "Sin marcar",
                            fontSize = 14.sp)
                    }
                }
                else -> {
                    OutlinedTextField(
                        value         = value,
                        onValueChange = onChange,
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = { Text("Escribe aquí…", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PdfRed
                        ),
                        singleLine = field.type != "text"
                    )
                }
            }
        }
    }
}
