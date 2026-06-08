package com.enmanuelgil.pdfreader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.pdfreader.model.ToolResult
import com.enmanuelgil.pdfreader.ui.viewmodel.ToolsViewModel

@Composable
fun ToolsScreen(vm: ToolsViewModel = viewModel()) {
    val context      = LocalContext.current
    val toolResult   by vm.toolResult.collectAsState()
    val isProcessing by vm.isProcessing.collectAsState()

    val tools = remember {
        listOf(
            ToolDef("Combinar PDFs",      Icons.Default.MergeType,    Color(0xFF6C63FF),
                "Une varios PDFs en uno solo. Selecciona los archivos en el orden deseado."),
            ToolDef("Dividir PDF",         Icons.Default.CallSplit,    Color(0xFF00BCD4),
                "Divide un PDF en partes. Elige rango de páginas o divide por página."),
            ToolDef("Extraer páginas",     Icons.Default.ContentCut,   Color(0xFF4CAF50),
                "Extrae páginas específicas de un PDF a un nuevo archivo."),
            ToolDef("Rotar páginas",       Icons.Default.RotateRight,  Color(0xFFF59E0B),
                "Rota todas o algunas páginas del PDF (90°, 180°, 270°)."),
            ToolDef("Comprimir PDF",       Icons.Default.Compress,     Color(0xFFEF5B5B),
                "Reduce el tamaño del archivo PDF optimizando su estructura."),
            ToolDef("PDF a imágenes",      Icons.Default.PhotoLibrary, Color(0xFF9C27B0),
                "Convierte cada página del PDF a una imagen PNG de alta calidad."),
            ToolDef("Ver metadatos",       Icons.Default.Info,         Color(0xFF607D8B),
                "Consulta y edita el título, autor, palabras clave y otros metadatos del PDF."),
        )
    }

    var activeTool by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier            = Modifier.fillMaxSize()
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Herramientas", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("Procesa tus PDFs localmente. Sin servidores, sin internet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
            }
        }

        items(tools) { tool ->
            ToolCard(
                tool      = tool,
                isActive  = activeTool == tool.name,
                onSelect  = { activeTool = if (activeTool == tool.name) null else tool.name }
            ) {
                ToolContent(
                    tool         = tool,
                    vm           = vm,
                    isProcessing = isProcessing
                )
            }
        }

        // Result toast
        item {
            when (val result = toolResult) {
                is ToolResult.Success -> {
                    ResultBanner(
                        message = result.message,
                        isError = false,
                        onDismiss = { vm.clearResult() }
                    )
                }
                is ToolResult.Error -> {
                    ResultBanner(
                        message = result.message,
                        isError = true,
                        onDismiss = { vm.clearResult() }
                    )
                }
                else -> {}
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ── Tool definition ───────────────────────────────────────────────────────────

private data class ToolDef(
    val name       : String,
    val icon       : ImageVector,
    val color      : Color,
    val description: String
)

// ── Expandable tool card ──────────────────────────────────────────────────────

@Composable
private fun ToolCard(
    tool     : ToolDef,
    isActive : Boolean,
    onSelect : () -> Unit,
    content  : @Composable () -> Unit
) {
    Card(
        onClick   = onSelect,
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color  = tool.color.copy(0.15f),
                    shape  = RoundedCornerShape(10.dp)
                ) {
                    Icon(tool.icon, null,
                        tint = tool.color,
                        modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(tool.name, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(tool.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                        maxLines = if (isActive) 3 else 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Icon(
                    if (isActive) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isActive) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                content()
            }
        }
    }
}

// ── Per-tool content ──────────────────────────────────────────────────────────

@Composable
private fun ToolContent(
    tool        : ToolDef,
    vm          : ToolsViewModel,
    isProcessing: Boolean
) {
    val context    = LocalContext.current
    val selectedUris by vm.selectedUris.collectAsState()

    when (tool.name) {
        "Combinar PDFs" -> MergeContent(vm, isProcessing, context)
        "Dividir PDF"   -> SplitContent(vm, isProcessing, context)
        "Extraer páginas" -> ExtractContent(vm, isProcessing, context)
        "Rotar páginas"   -> RotateContent(vm, isProcessing, context)
        "Comprimir PDF"   -> SingleFileToolContent(
            vm, isProcessing, context,
            buttonLabel = "Comprimir",
            onRun       = { uri -> vm.compress(context, uri) }
        )
        "PDF a imágenes"  -> SingleFileToolContent(
            vm, isProcessing, context,
            buttonLabel = "Exportar imágenes",
            onRun       = { uri -> vm.exportImages(context, uri) }
        )
        "Ver metadatos"   -> MetadataContent(vm, isProcessing, context)
    }
}

@Composable
private fun MergeContent(vm: ToolsViewModel, isProcessing: Boolean, context: android.content.Context) {
    val selectedUris by vm.selectedUris.collectAsState()
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.setUris(uris) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${selectedUris.size} archivo(s) seleccionado(s)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick  = { pickerLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.weight(1f)
            ) { Text("Seleccionar PDFs") }
            Button(
                onClick  = { vm.merge(context) },
                enabled  = selectedUris.size >= 2 && !isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Combinar")
            }
        }
    }
}

@Composable
private fun SplitContent(vm: ToolsViewModel, isProcessing: Boolean, context: android.content.Context) {
    var splitAll by remember { mutableStateOf(true) }
    var fromPage by remember { mutableStateOf("1") }
    var toPage   by remember { mutableStateOf("") }
    val picker   = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setUris(listOf(it)) }
    }
    val selectedUris by vm.selectedUris.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }) {
            Text(if (selectedUris.isEmpty()) "Seleccionar PDF" else "PDF seleccionado ✓")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = splitAll, onCheckedChange = { splitAll = it })
            Text("Dividir en páginas individuales", style = MaterialTheme.typography.bodySmall)
        }
        if (!splitAll) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = fromPage, onValueChange = { fromPage = it },
                    label = { Text("Desde pág.") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = toPage, onValueChange = { toPage = it },
                    label = { Text("Hasta pág.") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }
        Button(
            onClick  = {
                val ranges = if (splitAll) emptyList()
                             else {
                                 val f = fromPage.toIntOrNull()?.minus(1) ?: 0
                                 val t = toPage.toIntOrNull()?.minus(1) ?: f
                                 listOf(Pair(f, t))
                             }
                vm.split(context, ranges)
            },
            enabled  = selectedUris.isNotEmpty() && !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isProcessing) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Dividir")
        }
    }
}

@Composable
private fun ExtractContent(vm: ToolsViewModel, isProcessing: Boolean, context: android.content.Context) {
    var pagesInput by remember { mutableStateOf("") }
    val picker     = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setUris(listOf(it)) }
    }
    val selectedUris by vm.selectedUris.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }) {
            Text(if (selectedUris.isEmpty()) "Seleccionar PDF" else "PDF seleccionado ✓")
        }
        OutlinedTextField(
            value = pagesInput, onValueChange = { pagesInput = it },
            label = { Text("Páginas a extraer (ej: 1,3,5-8)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Text("Usa comas para páginas individuales y guiones para rangos",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        Button(
            onClick  = { vm.extract(context, pagesInput) },
            enabled  = selectedUris.isNotEmpty() && pagesInput.isNotEmpty() && !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isProcessing) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Extraer páginas")
        }
    }
}

@Composable
private fun RotateContent(vm: ToolsViewModel, isProcessing: Boolean, context: android.content.Context) {
    var degrees by remember { mutableStateOf(90) }
    var allPages by remember { mutableStateOf(true) }
    val picker   = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setUris(listOf(it)) }
    }
    val selectedUris by vm.selectedUris.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }) {
            Text(if (selectedUris.isEmpty()) "Seleccionar PDF" else "PDF seleccionado ✓")
        }
        Text("Grados de rotación:", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(90, 180, 270).forEach { deg ->
                FilterChip(
                    selected = degrees == deg,
                    onClick  = { degrees = deg },
                    label    = { Text("${deg}°") }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = allPages, onCheckedChange = { allPages = it })
            Text("Todas las páginas", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick  = { vm.rotate(context, if (allPages) emptyList() else emptyList(), degrees) },
            enabled  = selectedUris.isNotEmpty() && !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isProcessing) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Rotar")
        }
    }
}

@Composable
private fun SingleFileToolContent(
    vm          : ToolsViewModel,
    isProcessing: Boolean,
    context     : android.content.Context,
    buttonLabel : String,
    onRun       : (Uri) -> Unit
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setUris(listOf(it)) }
    }
    val selectedUris by vm.selectedUris.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedUris.isEmpty()) "Seleccionar PDF" else "PDF seleccionado ✓")
        }
        Button(
            onClick  = { selectedUris.firstOrNull()?.let(onRun) },
            enabled  = selectedUris.isNotEmpty() && !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isProcessing) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(buttonLabel)
        }
    }
}

@Composable
private fun MetadataContent(vm: ToolsViewModel, isProcessing: Boolean, context: android.content.Context) {
    val metadata by vm.metadata.collectAsState()
    val picker   = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setUris(listOf(it)); vm.loadMetadata(context, it) }
    }
    val selectedUris by vm.selectedUris.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedUris.isEmpty()) "Seleccionar PDF" else "PDF seleccionado ✓")
        }
        if (metadata.isNotEmpty()) {
            metadata.forEach { (key, value) ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(key, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                        modifier = Modifier.weight(0.4f))
                    Text(value, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.6f))
                }
            }
        }
    }
}

// ── Result banner ─────────────────────────────────────────────────────────────

@Composable
private fun ResultBanner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val color = if (isError) MaterialTheme.colorScheme.error else Color(0xFF3DDC84)
    Surface(
        color  = color.copy(0.12f),
        shape  = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(if (isError) Icons.Default.Error else Icons.Default.CheckCircle, null,
                tint = color, modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.size(14.dp))
            }
        }
    }
}
