package com.enmanuelgil.pdfsuite.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.pdfsuite.model.ToolResult
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.ToolsViewModel

// ── Tool descriptor ───────────────────────────────────────────────────────────

data class ToolDef(
    val id          : String,
    val label       : String,
    val icon        : ImageVector,
    val color       : Color,
    val category    : String,
    val description : String
)

private val TOOLS = listOf(
    // Editar
    ToolDef("rotate",    "Rotar",            Icons.Default.RotateRight,          Color(0xFF1E88E5), "Editar",   "Rota todas las páginas 90°, 180° o 270°"),
    ToolDef("compress",  "Comprimir",         Icons.Default.Compress,             Color(0xFF43A047), "Editar",   "Reduce el tamaño del PDF"),
    ToolDef("password",  "Contraseña",        Icons.Default.Lock,                 Color(0xFF8E24AA), "Editar",   "Protege o desbloquea con contraseña AES-128"),
    ToolDef("addtext",   "Agregar texto",     Icons.Default.TextFields,           Color(0xFFF57F17), "Editar",   "Añade texto en cualquier página y posición"),
    ToolDef("annotate",  "Anotaciones",       Icons.Default.Comment,              Color(0xFFF06292), "Editar",    "Notas, texto libre y resaltado de regiones"),
    ToolDef("insertimg", "Insertar imagen",   Icons.Default.AddPhotoAlternate,    Color(0xFF7E57C2), "Editar",    "Inserta una imagen de la galería en el PDF"),
    ToolDef("redact",    "Redactar",          Icons.Default.VisibilityOff,        Color(0xFF37474F), "Editar",    "Censura datos sensibles con rectángulos negros"),

    // Combinar
    ToolDef("merge",     "Combinar",          Icons.Default.CallMerge,            Color(0xFFE53935), "Combinar",  "Une varios PDFs en uno"),
    ToolDef("split",     "Dividir",           Icons.Default.CallSplit,            Color(0xFFFF7043), "Combinar",  "Extrae páginas a un nuevo PDF"),

    // Páginas
    ToolDef("organize",  "Organizar páginas", Icons.Default.ViewList,             Color(0xFF00ACC1), "Páginas",   "Elimina o reordena páginas del PDF"),

    // Convertir
    ToolDef("convert",   "Convertir",         Icons.Default.Transform,            Color(0xFF5C6BC0), "Convertir", "Imágenes → PDF · Documentos Office"),
    ToolDef("translate", "Traducir",          Icons.Default.Translate,            Color(0xFF00838F), "Convertir", "Extrae texto del PDF y lo envía al traductor"),

    // Firmar
    ToolDef("sign",      "Firma digital",     Icons.Default.Draw,                 Color(0xFF00897B), "Firmar",    "Dibuja tu firma y estámpala en el PDF"),
    ToolDef("fillform",  "Formularios",       Icons.Default.Assignment,           Color(0xFF039BE5), "Firmar",    "Rellena campos AcroForm del PDF"),

    // Escanear
    ToolDef("scan",      "Escanear doc.",     Icons.Default.DocumentScanner,      Color(0xFF6D4C41), "Escanear",  "Escanea documentos con la cámara"),
    ToolDef("text",      "Buscar/Extraer",    Icons.Default.ManageSearch,         Color(0xFF546E7A), "Escanear",  "Busca o extrae texto del PDF"),

    // Info
    ToolDef("metadata",  "Metadatos",         Icons.Default.Info,                 Color(0xFF00695C), "Info",      "Título, autor, páginas y detalles del archivo"),
)

// ── Screen states ─────────────────────────────────────────────────────────────

private sealed class ToolOverlay {
    object None                                          : ToolOverlay()
    data class Signing(val uri: Uri)                     : ToolOverlay()
    data class Form(val uri: Uri)                        : ToolOverlay()
    data class Scanner(val uri: Uri? = null)             : ToolOverlay()
    data class Text(val uri: Uri)                        : ToolOverlay()
    data class Metadata(val uri: Uri)                    : ToolOverlay()
    data class AddText(val uri: Uri, val pages: Int)     : ToolOverlay()
    data class Annotate(val uri: Uri, val pages: Int)    : ToolOverlay()
    data class InsertImage(val uri: Uri, val pages: Int) : ToolOverlay()
    data class Organize(val uri: Uri, val pages: Int)    : ToolOverlay()
    data class Redact(val uri: Uri, val pages: Int)      : ToolOverlay()
    data class Translate(val uri: Uri)                   : ToolOverlay()
    object Convert                                       : ToolOverlay()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onOpenPdf : ((android.net.Uri) -> Unit)? = null,
    vm        : ToolsViewModel = viewModel()
) {
    val context   = LocalContext.current
    val result    by vm.result.collectAsState()
    val isWorking by vm.isWorking.collectAsState()
    val pageCount by vm.pageCount.collectAsState()

    var overlay by remember { mutableStateOf<ToolOverlay>(ToolOverlay.None) }

    // Dialog states
    var showRotateDialog   by remember { mutableStateOf(false) }
    var showSplitDialog    by remember { mutableStateOf(false) }
    var showPassDialog     by remember { mutableStateOf(false) }
    var pendingUri         by remember { mutableStateOf<Uri?>(null) }
    var pendingImageUri    by remember { mutableStateOf<Uri?>(null) }
    var pendingPageCount   by remember { mutableStateOf(1) }
    var passMode           by remember { mutableStateOf("protect") }  // "protect" or "remove"
    var waitingForSplit     by remember { mutableStateOf(false) }
    var waitingForAddText   by remember { mutableStateOf(false) }
    var waitingForAnnotate  by remember { mutableStateOf(false) }
    var waitingForInsertImg by remember { mutableStateOf(false) }
    var waitingForOrganize  by remember { mutableStateOf(false) }
    var waitingForRedact    by remember { mutableStateOf(false) }
    var waitingForTranslate by remember { mutableStateOf(false) }

    // Sync page count from VM — show pending dialogs/overlays once count is ready
    LaunchedEffect(pageCount) {
        if (pageCount > 0) {
            pendingPageCount = pageCount
            if (waitingForSplit) {
                waitingForSplit  = false
                showSplitDialog  = true
            }
            if (waitingForAddText) {
                waitingForAddText = false
                pendingUri?.let { overlay = ToolOverlay.AddText(it, pageCount) }
            }
            if (waitingForAnnotate) {
                waitingForAnnotate = false
                pendingUri?.let { overlay = ToolOverlay.Annotate(it, pageCount) }
            }
            if (waitingForInsertImg) {
                waitingForInsertImg = false
                pendingUri?.let { overlay = ToolOverlay.InsertImage(it, pageCount) }
            }
            if (waitingForOrganize) {
                waitingForOrganize = false
                pendingUri?.let { overlay = ToolOverlay.Organize(it, pageCount) }
            }
            if (waitingForRedact) {
                waitingForRedact = false
                pendingUri?.let { overlay = ToolOverlay.Redact(it, pageCount) }
            }
            if (waitingForTranslate) {
                waitingForTranslate = false
                pendingUri?.let { overlay = ToolOverlay.Translate(it) }
            }
        }
    }

    // Result dialog
    if (result != null && result !is ToolResult.Loading) {
        ResultDialog(
            result    = result!!,
            onDismiss = { vm.clearResult() },
            onShare   = { vm.shareResult(context) }
        )
    }

    // Fullscreen overlays
    val ov = overlay
    if (ov !is ToolOverlay.None) {
        AnimatedVisibility(
            visible = true,
            enter   = slideInVertically { it },
            exit    = slideOutVertically { it }
        ) {
            when (ov) {
                is ToolOverlay.Signing  -> SignaturePadScreen(
                    onSignatureReady = { bitmap ->
                        val uri = ov.uri
                        overlay = ToolOverlay.None
                        vm.stampSignature(context, uri, bitmap,
                            pageNum = 1, x = 72f, y = 72f, w = 220f, h = 90f)
                    },
                    onDismiss = { overlay = ToolOverlay.None }
                )
                is ToolOverlay.Form     -> FormFillScreen(
                    uri = ov.uri, vm = vm,
                    onDismiss = { overlay = ToolOverlay.None }
                )
                is ToolOverlay.Scanner  -> ScannerScreen(
                    vm        = vm,
                    onDismiss = { overlay = ToolOverlay.None },
                    onOpenPdf = { /* reader is handled in MainActivity */ }
                )
                is ToolOverlay.Text     -> TextToolScreen(
                    uri = ov.uri, vm = vm,
                    onDismiss = { overlay = ToolOverlay.None }
                )
                is ToolOverlay.Metadata -> MetadataScreen(
                    uri = ov.uri, vm = vm,
                    onDismiss = { overlay = ToolOverlay.None }
                )
                is ToolOverlay.AddText  -> AddTextScreen(
                    uri       = ov.uri,
                    pageCount = ov.pages,
                    vm        = vm,
                    onDismiss = { overlay = ToolOverlay.None }
                )
                is ToolOverlay.Annotate -> AnnotateScreen(
                    uri       = ov.uri,
                    pageCount = ov.pages,
                    vm        = vm,
                    onDismiss = { overlay = ToolOverlay.None }
                )
                is ToolOverlay.InsertImage -> InsertImageScreen(
                    pdfUri              = ov.uri,
                    pageCount           = ov.pages,
                    vm                  = vm,
                    onDismiss           = { overlay = ToolOverlay.None; pendingImageUri = null },
                    preSelectedImageUri = pendingImageUri
                )
                is ToolOverlay.Organize -> OrganizePagesScreen(
                    uri       = ov.uri,
                    pageCount = ov.pages,
                    vm        = vm,
                    onDismiss = { overlay = ToolOverlay.None }
                )
                is ToolOverlay.Redact -> RedactScreen(
                    uri       = ov.uri,
                    pageCount = ov.pages,
                    vm        = vm,
                    onDismiss = { overlay = ToolOverlay.None }
                )
                is ToolOverlay.Translate -> TranslateScreen(
                    uri       = ov.uri,
                    onDismiss = { overlay = ToolOverlay.None }
                )
                is ToolOverlay.Convert -> ConvertScreen(
                    vm        = vm,
                    onDismiss = { overlay = ToolOverlay.None }
                )
                else -> {}
            }
        }
        return
    }

    // Single-file picker
    var onFilePicked by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onFilePicked?.invoke(it) } }

    // Multi-file picker (for merge)
    var onMultiFilePicked by remember { mutableStateOf<((List<Uri>) -> Unit)?>(null) }
    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onMultiFilePicked?.invoke(uris) }

    // Single image picker (for insertimg direct launch)
    var waitingForInsertImgPdf by remember { mutableStateOf(false) }
    val singleImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
            // Now ask user to pick the PDF to insert into
            waitingForInsertImgPdf = true
            onFilePicked = { pdfUri ->
                pendingUri = pdfUri
                waitingForInsertImg   = true
                waitingForInsertImgPdf = false
                vm.loadPageCount(context, pdfUri)
            }
            singlePicker.launch(arrayOf("application/pdf"))
        }
    }

    // Multi-image picker (for convert/images-to-PDF)
    var onImagesPicked by remember { mutableStateOf<((List<Uri>) -> Unit)?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onImagesPicked?.invoke(uris) }

    // ── Rotate dialog ─────────────────────────────────────────────────────────
    if (showRotateDialog) {
        RotateDialog(
            onConfirm = { degrees ->
                showRotateDialog = false
                pendingUri?.let { vm.rotateAll(context, it, degrees) }
            },
            onDismiss = { showRotateDialog = false }
        )
    }

    // ── Split dialog ──────────────────────────────────────────────────────────
    if (showSplitDialog) {
        SplitDialog(
            maxPage   = pendingPageCount,
            onConfirm = { start, end ->
                showSplitDialog = false
                pendingUri?.let { vm.split(context, it, start, end) }
            },
            onDismiss = { showSplitDialog = false }
        )
    }

    // ── Password dialog ───────────────────────────────────────────────────────
    if (showPassDialog) {
        PasswordDialog(
            mode      = passMode,
            onProtect = { userPass ->
                showPassDialog = false
                pendingUri?.let { vm.setPassword(context, it, userPass) }
            },
            onUnlock  = { pass ->
                showPassDialog = false
                pendingUri?.let { vm.removePassword(context, it, pass) }
            },
            onDismiss = { showPassDialog = false }
        )
    }

    // ── Main scaffold ─────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Herramientas", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Text("${TOOLS.size} herramientas", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier       = Modifier.fillMaxSize().padding(padding)
        ) {
            val categories = TOOLS.map { it.category }.distinct()
            for (cat in categories) {
                item {
                    Text(cat,
                        Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp, end = 16.dp),
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    ToolGrid(TOOLS.filter { it.category == cat }) { tool ->
                        when (tool.id) {
                            "scan" -> {
                                overlay = ToolOverlay.Scanner()
                            }
                            "convert" -> {
                                overlay = ToolOverlay.Convert
                            }
                            "merge" -> {
                                onMultiFilePicked = { uris -> vm.merge(context, uris) }
                                multiPicker.launch(arrayOf("application/pdf"))
                            }
                            "insertimg" -> {
                                // Open gallery directly — PDF is picked in second step
                                singleImagePicker.launch(arrayOf("image/*"))
                            }
                            else -> {
                                onFilePicked = { uri ->
                                    pendingUri = uri
                                    when (tool.id) {
                                        "compress" -> vm.compress(context, uri)
                                        "rotate"   -> {
                                            showRotateDialog = true
                                        }
                                        "split"    -> {
                                            // Load page count; dialog shown by LaunchedEffect
                                            pendingPageCount = 1
                                            waitingForSplit  = true
                                            vm.loadPageCount(context, uri)
                                        }
                                        "password" -> {
                                            passMode = "both"
                                            showPassDialog = true
                                        }
                                        "sign"     -> overlay = ToolOverlay.Signing(uri)
                                        "fillform" -> overlay = ToolOverlay.Form(uri)
                                        "text"     -> overlay = ToolOverlay.Text(uri)
                                        "metadata" -> overlay = ToolOverlay.Metadata(uri)
                                        "addtext"  -> {
                                            // Load page count; overlay shown by LaunchedEffect
                                            waitingForAddText = true
                                            vm.loadPageCount(context, uri)
                                        }
                                        "annotate"  -> {
                                            waitingForAnnotate = true
                                            vm.loadPageCount(context, uri)
                                        }
                                        "insertimg" -> { /* handled by singleImagePicker above */ }
                                        "organize"  -> {
                                            waitingForOrganize = true
                                            vm.loadPageCount(context, uri)
                                        }
                                        "redact" -> {
                                            waitingForRedact = true
                                            vm.loadPageCount(context, uri)
                                        }
                                        "translate" -> {
                                            waitingForTranslate = true
                                            vm.loadPageCount(context, uri)
                                        }
                                        else -> {}
                                    }
                                }
                                singlePicker.launch(arrayOf("application/pdf"))
                            }
                        }
                    }
                }
            }

            // Loading indicator
            if (isWorking) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = PdfRed, modifier = Modifier.size(24.dp))
                            Text("Procesando…", fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ── Tool Grid & Cell ──────────────────────────────────────────────────────────

// 4-column compact grid
@Composable
private fun ToolGrid(tools: List<ToolDef>, onClick: (ToolDef) -> Unit) {
    val rows = tools.chunked(4)
    Column(Modifier.padding(horizontal = 10.dp)) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (tool in row) {
                    ToolCell(tool, { onClick(tool) }, Modifier.weight(1f))
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ToolCell(tool: ToolDef, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick         = onClick,
        modifier        = modifier,
        shape           = RoundedCornerShape(12.dp),
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(tool.color.copy(0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text(tool.label, fontSize = 9.5.sp, fontWeight = FontWeight.Medium,
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, textAlign = TextAlign.Center,
                lineHeight = 12.sp)
        }
    }
}

// ── Result Dialog ─────────────────────────────────────────────────────────────

@Composable
private fun ResultDialog(result: ToolResult, onDismiss: () -> Unit, onShare: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (result) {
                    is ToolResult.Success -> {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, null,
                                tint     = Color(0xFF43A047),
                                modifier = Modifier.size(24.dp))
                            Text("¡Completado!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(result.message, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Share + Cloud row
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick  = { onShare(); onDismiss() },
                                modifier = Modifier.weight(1f),
                                colors   = ButtonDefaults.buttonColors(containerColor = PdfRed)
                            ) {
                                Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Compartir")
                            }
                            OutlinedButton(
                                onClick  = {
                                    // Save to cloud via Android SAF (Google Drive, Dropbox, etc.)
                                    saveToCloud(context, result.outputUri)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Cloud, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Nube")
                            }
                        }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Cerrar")
                        }
                    }
                    is ToolResult.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Error, null,
                                tint     = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp))
                            Text("Error", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(result.message, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Entendido")
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

// ── Rotate Dialog ─────────────────────────────────────────────────────────────

@Composable
private fun RotateDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(90) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon   = { Icon(Icons.Default.RotateRight, null, tint = Color(0xFF1E88E5)) },
        title  = { Text("Rotar páginas") },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(90, 180, 270).forEach { deg ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = selected == deg,
                            onClick  = { selected = deg },
                            colors   = RadioButtonDefaults.colors(selectedColor = PdfRed)
                        )
                        Text("$deg° ${when(deg) { 90 -> "(horario)" 180 -> "(voltear)" else -> "(antihorario)" }}")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) },
                colors = ButtonDefaults.buttonColors(containerColor = PdfRed)) {
                Text("Rotar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ── Split Dialog ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitDialog(maxPage: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var startStr by remember { mutableStateOf("1") }
    var endStr   by remember { mutableStateOf(if (maxPage > 1) "1" else "1") }
    val start    = startStr.toIntOrNull() ?: 1
    val end      = endStr.toIntOrNull() ?: start
    val valid    = start in 1..(maxPage.coerceAtLeast(1)) &&
                   end in start..(maxPage.coerceAtLeast(1))

    AlertDialog(
        onDismissRequest = onDismiss,
        icon   = { Icon(Icons.Default.CallSplit, null, tint = Color(0xFFFF7043)) },
        title  = { Text("Dividir PDF") },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (maxPage > 1)
                    Text("Páginas del PDF: $maxPage",
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value         = startStr,
                        onValueChange = { startStr = it.filter { c -> c.isDigit() } },
                        label         = { Text("Desde") },
                        modifier      = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError       = start < 1 || start > maxPage.coerceAtLeast(1),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PdfRed)
                    )
                    OutlinedTextField(
                        value         = endStr,
                        onValueChange = { endStr = it.filter { c -> c.isDigit() } },
                        label         = { Text("Hasta") },
                        modifier      = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError       = end < start,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PdfRed)
                    )
                }
                if (valid) {
                    Text("Se extraerán ${end - start + 1} página(s)",
                        fontSize = 12.sp, color = Color(0xFF43A047))
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (valid) onConfirm(start, end) },
                enabled  = valid,
                colors   = ButtonDefaults.buttonColors(containerColor = PdfRed)
            ) { Text("Extraer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ── Password Dialog ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordDialog(
    mode      : String,
    onProtect : (String) -> Unit,
    onUnlock  : (String) -> Unit,
    onDismiss : () -> Unit
) {
    var tab        by remember { mutableStateOf(0) }  // 0=protect, 1=unlock
    var password   by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon   = { Icon(Icons.Default.Lock, null, tint = Color(0xFF8E24AA)) },
        title  = { Text("Contraseña PDF") },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(selectedTabIndex = tab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor   = Color(0xFF8E24AA)) {
                    Tab(selected = tab == 0, onClick = { tab = 0; password = "" },
                        text = { Text("Proteger", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Lock, null, Modifier.size(16.dp)) })
                    Tab(selected = tab == 1, onClick = { tab = 1; password = "" },
                        text = { Text("Desbloquear", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.LockOpen, null, Modifier.size(16.dp)) })
                }
                OutlinedTextField(
                    value            = password,
                    onValueChange    = { password = it },
                    label            = { Text(if (tab == 0) "Nueva contraseña" else "Contraseña actual") },
                    visualTransformation = if (passVisible) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    trailingIcon     = {
                        IconButton(onClick = { passVisible = !passVisible }) {
                            Icon(if (passVisible) Icons.Default.VisibilityOff
                                 else Icons.Default.Visibility, null)
                        }
                    },
                    modifier         = Modifier.fillMaxWidth(),
                    singleLine       = true,
                    colors           = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF8E24AA))
                )
                if (tab == 0) {
                    Text("Cifrado AES-128. Permite impresión y copia.",
                        fontSize = 11.sp,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    if (password.isNotBlank()) {
                        if (tab == 0) onProtect(password) else onUnlock(password)
                    }
                },
                enabled  = password.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8E24AA))
            ) {
                Text(if (tab == 0) "Proteger" else "Desbloquear")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ── Cloud save helper ─────────────────────────────────────────────────────────

private fun saveToCloud(context: Context, fileUri: Uri) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_TITLE, "Guardar en la nube")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Guardar en…"))
    } catch (_: Exception) {}
}
