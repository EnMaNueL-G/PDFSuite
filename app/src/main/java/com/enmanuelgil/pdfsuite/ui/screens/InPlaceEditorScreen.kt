package com.enmanuelgil.pdfsuite.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.enmanuelgil.pdfsuite.data.PdfRepository
import com.enmanuelgil.pdfsuite.data.PdfTools
import com.enmanuelgil.pdfsuite.model.ToolResult
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.ToolsViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Editor modes ──────────────────────────────────────────────────────────────

enum class InPlaceMode(val label: String, val icon: ImageVector) {
    TEXT        ("Texto",    Icons.Default.TextFields),
    ANNOTATE    ("Anotar",   Icons.Default.StickyNote2),
    HIGHLIGHT   ("Resaltar", Icons.Default.Highlight),
    INSERT_IMAGE("Imagen",   Icons.Default.AddPhotoAlternate),
    SIGN        ("Firma",    Icons.Default.Draw),
    REDACT      ("Redactar", Icons.Default.VisibilityOff),
}

// ── Overlay data ──────────────────────────────────────────────────────────────

enum class OverlayKind { IMAGE, SIGN, ANNOTATE, HIGHLIGHT, REDACT }

data class PlacedOverlay(
    val id      : String = UUID.randomUUID().toString(),
    val kind    : OverlayKind,
    var xPt     : Float,   // PDF-point coordinates
    var yPt     : Float,
    var wPt     : Float,
    var hPt     : Float,
    val bitmap  : Bitmap? = null,  // IMAGE / SIGN
    val text    : String  = "",    // ANNOTATE
    val colorInt: Int     = 0x80FFEB3B.toInt()  // HIGHLIGHT
)

// ── Main screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InPlaceEditorScreen(
    uri         : Uri,
    pageCount   : Int,
    startPage   : Int         = 1,
    startMode   : InPlaceMode = InPlaceMode.TEXT,
    toolsVm     : ToolsViewModel,
    onDismiss   : () -> Unit
) {
    val context      = LocalContext.current
    val density      = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope        = rememberCoroutineScope()

    // ── Core state ────────────────────────────────────────────────────────────
    var currentPage  by remember { mutableStateOf(startPage.coerceIn(1, pageCount)) }
    var mode         by remember { mutableStateOf(startMode) }
    var pageBitmap   by remember { mutableStateOf<Bitmap?>(null) }
    var pageInfo     by remember { mutableStateOf<PdfTools.PdfPageInfo?>(null) }
    var textBlocks   by remember { mutableStateOf<List<PdfTools.PdfTextBlock>>(emptyList()) }
    var textEdits    by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var activeTextIdx by remember { mutableStateOf<Int?>(null) }
    var overlays     = remember { mutableStateListOf<PlacedOverlay>() }
    var selectedId   by remember { mutableStateOf<String?>(null) }
    var containerWidthPx by remember { mutableStateOf(0f) }
    var isLoading    by remember { mutableStateOf(true) }
    var isSaving     by remember { mutableStateOf(false) }
    var showSignPad  by remember { mutableStateOf(false) }
    // Redact drag
    var redactStart  by remember { mutableStateOf<Offset?>(null) }
    var redactCurrent by remember { mutableStateOf<Offset?>(null) }
    // Annotation pending
    var pendingAnnotPos by remember { mutableStateOf<Offset?>(null) }
    var pendingAnnotText by remember { mutableStateOf("") }
    // Scale (zoom)
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // ── VM result watcher ─────────────────────────────────────────────────────
    val vmResult by toolsVm.result.collectAsState()
    var saveResult by remember { mutableStateOf<ToolResult?>(null) }
    LaunchedEffect(vmResult) {
        if (isSaving && vmResult != null && vmResult !is ToolResult.Loading) {
            saveResult = vmResult
            isSaving   = false
        }
    }

    // ── Image picker ──────────────────────────────────────────────────────────
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { imageUri ->
        if (imageUri != null && pageInfo != null) {
            scope.launch {
                val bmp = loadBitmapFromUri(context, imageUri) ?: return@launch
            val info = pageInfo!!
            val aspect = bmp.height.toFloat() / bmp.width.toFloat()
            val wPt = info.widthPt * 0.4f
            overlays.add(PlacedOverlay(
                kind   = OverlayKind.IMAGE,
                xPt    = info.widthPt  * 0.1f,
                yPt    = info.heightPt * 0.4f,
                wPt    = wPt,
                hPt    = wPt * aspect,
                bitmap = bmp
            ))
        }}
    }

    // ── Load page ─────────────────────────────────────────────────────────────
    LaunchedEffect(uri, currentPage) {
        isLoading    = true
        activeTextIdx = null
        pageBitmap   = null
        textBlocks   = emptyList()
        textEdits    = emptyMap()
        overlays.clear()
        selectedId   = null
        val metrics  = context.resources.displayMetrics
        pageBitmap   = PdfRepository.renderPage(context, uri, currentPage - 1, metrics.widthPixels)
        pageInfo     = toolsVm.getPdfPageInfo(context, uri, currentPage)
        if (mode == InPlaceMode.TEXT)
            textBlocks = toolsVm.extractTextBlocks(context, uri, currentPage)
        isLoading    = false
    }

    // ── Save result dialog ────────────────────────────────────────────────────
    val sr = saveResult
    if (sr != null) {
        AlertDialog(
            onDismissRequest = { saveResult = null; toolsVm.clearResult()
                if (sr is ToolResult.Success) onDismiss() },
            title   = { Text(if (sr is ToolResult.Success) "✓ Guardado" else "Error",
                fontWeight = FontWeight.Bold) },
            text    = { Text(when (sr) {
                is ToolResult.Success -> sr.message
                is ToolResult.Error   -> sr.message
                else -> ""
            }) },
            confirmButton = {
                Button(onClick = { saveResult = null; toolsVm.clearResult()
                    if (sr is ToolResult.Success) onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = PdfRed)
                ) { Text("OK") }
            }
        )
    }

    // ── Signature pad (bottom sheet) ──────────────────────────────────────────
    if (showSignPad) {
        SignaturePadBottomSheet(
            onDone    = { bmp ->
                showSignPad = false
                val info = pageInfo ?: return@SignaturePadBottomSheet
                val aspect = bmp.height.toFloat() / bmp.width.toFloat()
                val wPt = info.widthPt * 0.35f
                overlays.add(PlacedOverlay(
                    kind   = OverlayKind.SIGN,
                    xPt    = info.widthPt  * 0.1f,
                    yPt    = info.heightPt * 0.1f,
                    wPt    = wPt,
                    hPt    = wPt * aspect,
                    bitmap = bmp
                ))
                selectedId = overlays.last().id
            },
            onDismiss = { showSignPad = false }
        )
    }

    // ── Annotation input dialog ───────────────────────────────────────────────
    val annotPos = pendingAnnotPos
    if (annotPos != null) {
        AlertDialog(
            onDismissRequest = { pendingAnnotPos = null; pendingAnnotText = "" },
            title   = { Text("Añadir anotación") },
            text    = {
                OutlinedTextField(
                    value         = pendingAnnotText,
                    onValueChange = { pendingAnnotText = it },
                    label         = { Text("Escribe tu nota…") },
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = PdfRed)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pendingAnnotText.isNotBlank() && pageInfo != null) {
                            val info = pageInfo!!
                            val scale2 = containerWidthPx / info.widthPt
                            val xPt = annotPos.x / scale2
                            val yPt = info.heightPt - (annotPos.y / scale2) - 20f
                            overlays.add(PlacedOverlay(
                                kind = OverlayKind.ANNOTATE,
                                xPt  = xPt.coerceIn(0f, info.widthPt - 80f),
                                yPt  = yPt.coerceIn(0f, info.heightPt - 20f),
                                wPt  = 120f, hPt = 30f,
                                text = pendingAnnotText
                            ))
                        }
                        pendingAnnotPos  = null
                        pendingAnnotText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PdfRed)
                ) { Text("Añadir") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAnnotPos = null; pendingAnnotText = "" }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ── Derived scale ─────────────────────────────────────────────────────────
    val pageWidthPx  = containerWidthPx.takeIf { it > 0f }
        ?: context.resources.displayMetrics.widthPixels.toFloat()
    val pageWidthPt  = pageInfo?.widthPt  ?: 595f
    val pageHeightPt = pageInfo?.heightPt ?: 842f
    val docScale     = pageWidthPx / pageWidthPt
    val pageHeightPx = pageHeightPt * docScale

    val hasChanges = textEdits.any { (i, t) -> i < textBlocks.size && t != textBlocks[i].text }
        || overlays.isNotEmpty()

    // ── Layout ────────────────────────────────────────────────────────────────
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Editor in-place", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Modo: ${mode.label}", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Undo last overlay
                    if (overlays.isNotEmpty()) {
                        IconButton(onClick = { overlays.removeLastOrNull() }) {
                            Icon(Icons.Default.Undo, "Deshacer", tint = PdfRed)
                        }
                    }
                    // Save
                    Button(
                        onClick = {
                            focusManager.clearFocus(); activeTextIdx = null; isSaving = true
                            scope.launch { saveAll(context, uri, currentPage, textBlocks, textEdits, overlays, toolsVm) }
                        },
                        enabled  = hasChanges && !isLoading && !isSaving,
                        colors   = ButtonDefaults.buttonColors(containerColor = PdfRed),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isSaving) CircularProgressIndicator(color = Color.White,
                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("Guardar") }
                    }
                }
            }

            // ── PDF canvas ────────────────────────────────────────────────────
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .onGloballyPositioned { containerWidthPx = it.size.width.toFloat() }
            ) {
                val bmp = pageBitmap

                if (isLoading || bmp == null) {
                    Box(Modifier.fillMaxWidth().height(500.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PdfRed)
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth()
                            .height(with(density) { pageHeightPx.toDp() })
                            .graphicsLayer {
                                scaleX = scale; scaleY = scale
                                translationX = offsetX; translationY = offsetY
                            }
                    ) {
                        // ── PDF background ────────────────────────────────────
                        Image(
                            bitmap             = bmp.asImageBitmap(),
                            contentDescription = "Página $currentPage",
                            contentScale       = ContentScale.FillWidth,
                            modifier           = Modifier.fillMaxSize()
                                .pointerInput(mode, textBlocks, activeTextIdx) {
                                    if (mode == InPlaceMode.REDACT) {
                                        detectDragGestures(
                                            onDragStart = { redactStart = it; redactCurrent = it },
                                            onDrag      = { _, delta ->
                                                redactCurrent = redactCurrent?.plus(delta)
                                            },
                                            onDragEnd   = {
                                                val s = redactStart; val e = redactCurrent
                                                if (s != null && e != null && pageInfo != null) {
                                                    val sc = docScale
                                                    val x1 = minOf(s.x, e.x) / sc
                                                    val x2 = maxOf(s.x, e.x) / sc
                                                    val y1Bot = pageHeightPt - maxOf(s.y, e.y) / sc
                                                    val y1Top = pageHeightPt - minOf(s.y, e.y) / sc
                                                    overlays.add(PlacedOverlay(
                                                        kind = OverlayKind.REDACT,
                                                        xPt  = x1, yPt = y1Bot,
                                                        wPt  = (x2 - x1).coerceAtLeast(10f),
                                                        hPt  = (y1Top - y1Bot).coerceAtLeast(10f)
                                                    ))
                                                }
                                                redactStart = null; redactCurrent = null
                                            }
                                        )
                                    } else if (mode == InPlaceMode.HIGHLIGHT) {
                                        detectDragGestures(
                                            onDragStart = { redactStart = it; redactCurrent = it },
                                            onDrag      = { _, delta ->
                                                redactCurrent = redactCurrent?.plus(delta)
                                            },
                                            onDragEnd   = {
                                                val s = redactStart; val e = redactCurrent
                                                if (s != null && e != null && pageInfo != null) {
                                                    val sc = docScale
                                                    val x1 = minOf(s.x, e.x) / sc
                                                    val x2 = maxOf(s.x, e.x) / sc
                                                    val y1Bot = pageHeightPt - maxOf(s.y, e.y) / sc
                                                    val y1Top = pageHeightPt - minOf(s.y, e.y) / sc
                                                    overlays.add(PlacedOverlay(
                                                        kind     = OverlayKind.HIGHLIGHT,
                                                        xPt      = x1, yPt = y1Bot,
                                                        wPt      = (x2 - x1).coerceAtLeast(10f),
                                                        hPt      = (y1Top - y1Bot).coerceAtLeast(8f),
                                                        colorInt = 0x80FFEB3B.toInt()
                                                    ))
                                                }
                                                redactStart = null; redactCurrent = null
                                            }
                                        )
                                    } else {
                                        // Tap: TEXT = deselect, ANNOTATE = place note
                                        detectTapGestures { tap ->
                                            selectedId    = null
                                            if (mode == InPlaceMode.ANNOTATE) {
                                                pendingAnnotPos = tap
                                            } else if (mode == InPlaceMode.TEXT) {
                                                val hit = hitTestBlock(tap, textBlocks, docScale, pageHeightPt, density.density)
                                                activeTextIdx = if (hit != null && hit != activeTextIdx) hit else null
                                                if (hit == null) focusManager.clearFocus()
                                            }
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                                        if (scale > 1f) { offsetX += pan.x; offsetY += pan.y }
                                        else { offsetX = 0f; offsetY = 0f }
                                    }
                                }
                        )

                        // ── Text overlays (TEXT mode) ─────────────────────────
                        if (mode == InPlaceMode.TEXT) {
                            textBlocks.forEachIndexed { idx, block ->
                                val lPx  = block.x * docScale
                                val tPx  = (pageHeightPt - block.y - block.height) * docScale
                                val wPx  = block.width  * docScale
                                val hPx  = block.height * docScale
                                val isAct = activeTextIdx == idx
                                val editT = textEdits[idx] ?: block.text
                                val fReq  = remember { FocusRequester() }

                                with(density) {
                                    Box(Modifier
                                        .absoluteOffset { IntOffset(lPx.roundToInt(), tPx.roundToInt()) }
                                        .width(wPx.coerceAtLeast(40f).toDp())
                                        .height(hPx.coerceAtLeast(12f).toDp())
                                    ) {
                                        if (isAct) {
                                            LaunchedEffect(Unit) { fReq.requestFocus() }
                                            BasicTextField(
                                                value         = editT,
                                                onValueChange = { textEdits = textEdits + (idx to it) },
                                                textStyle = TextStyle(
                                                    fontSize   = (block.fontSize * docScale / density.density)
                                                        .coerceIn(7f, 26f).sp,
                                                    fontFamily = FontFamily.SansSerif,
                                                    color      = Color.Black
                                                ),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(onDone = {
                                                    activeTextIdx = null; focusManager.clearFocus()
                                                }),
                                                modifier = Modifier.fillMaxSize()
                                                    .focusRequester(fReq)
                                                    .background(Color.White.copy(0.93f), RoundedCornerShape(2.dp))
                                                    .border(1.5.dp, PdfRed, RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 2.dp, vertical = 1.dp),
                                                singleLine = false
                                            )
                                        } else {
                                            val edited = textEdits.containsKey(idx) && textEdits[idx] != block.text
                                            Box(Modifier.fillMaxSize()
                                                .background(
                                                    if (edited) Color(0x30FF5722) else Color(0x15006D77),
                                                    RoundedCornerShape(1.dp))
                                                .border(0.5.dp,
                                                    if (edited) PdfRed.copy(0.7f) else PdfRed.copy(0.3f),
                                                    RoundedCornerShape(1.dp))
                                                .pointerInput(Unit) { detectTapGestures { activeTextIdx = idx } }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Draggable/resizable overlays ──────────────────────
                        overlays.toList().forEach { ov ->
                            val lPx = ov.xPt * docScale
                            val tPx = (pageHeightPt - ov.yPt - ov.hPt) * docScale
                            val wPx = ov.wPt * docScale
                            val hPx = ov.hPt * docScale
                            val sel = selectedId == ov.id

                            with(density) {
                                Box(Modifier
                                    .absoluteOffset { IntOffset(lPx.roundToInt(), tPx.roundToInt()) }
                                    .width(wPx.coerceAtLeast(20f).toDp())
                                    .height(hPx.coerceAtLeast(20f).toDp())
                                ) {
                                    when (ov.kind) {
                                        OverlayKind.IMAGE, OverlayKind.SIGN -> {
                                            val bmpOv = ov.bitmap
                                            if (bmpOv != null) {
                                                Image(
                                                    bitmap             = bmpOv.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale       = ContentScale.Fit,
                                                    modifier           = Modifier.fillMaxSize()
                                                        .then(if (ov.kind == OverlayKind.SIGN)
                                                            Modifier.background(Color.Transparent)
                                                        else Modifier)
                                                        .border(
                                                            if (sel) 1.5.dp else 0.5.dp,
                                                            if (sel) PdfRed else PdfRed.copy(0.3f),
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .pointerInput(ov.id) {
                                                            detectDragGestures { _, drag ->
                                                                val dx = drag.x / docScale
                                                                val dy = drag.y / docScale
                                                                val idx2 = overlays.indexOfFirst { it.id == ov.id }
                                                                if (idx2 >= 0) overlays[idx2] = overlays[idx2].copy(
                                                                    xPt = (overlays[idx2].xPt + dx).coerceIn(0f, pageWidthPt - ov.wPt),
                                                                    yPt = (overlays[idx2].yPt - dy).coerceIn(0f, pageHeightPt - ov.hPt)
                                                                )
                                                            }
                                                        }
                                                        .pointerInput(ov.id) { detectTapGestures { selectedId = ov.id } }
                                                )
                                            }
                                            // Resize handle (bottom-right corner)
                                            if (sel) {
                                                Box(Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .size(18.dp)
                                                    .background(PdfRed, RoundedCornerShape(4.dp))
                                                    .pointerInput(ov.id) {
                                                        detectDragGestures { _, drag ->
                                                            val dw = drag.x / docScale
                                                            val dh = drag.y / docScale
                                                            val idx2 = overlays.indexOfFirst { it.id == ov.id }
                                                            if (idx2 >= 0) overlays[idx2] = overlays[idx2].copy(
                                                                wPt = (overlays[idx2].wPt + dw).coerceAtLeast(20f),
                                                                hPt = (overlays[idx2].hPt + dh).coerceAtLeast(20f)
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(Icons.Default.OpenWith, null,
                                                        Modifier.size(12.dp).align(Alignment.Center),
                                                        tint = Color.White)
                                                }
                                                // Delete handle
                                                Box(Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(18.dp)
                                                    .background(Color(0xFFB71C1C), RoundedCornerShape(9.dp))
                                                    .clickable { overlays.removeAll { it.id == ov.id }; selectedId = null }
                                                ) {
                                                    Icon(Icons.Default.Close, null,
                                                        Modifier.size(12.dp).align(Alignment.Center),
                                                        tint = Color.White)
                                                }
                                            }
                                        }

                                        OverlayKind.HIGHLIGHT -> {
                                            Box(Modifier.fillMaxSize()
                                                .background(Color(ov.colorInt), RoundedCornerShape(2.dp))
                                                .pointerInput(ov.id) {
                                                    detectDragGestures { _, drag ->
                                                        val idx2 = overlays.indexOfFirst { it.id == ov.id }
                                                        if (idx2 >= 0) overlays[idx2] = overlays[idx2].copy(
                                                            xPt = overlays[idx2].xPt + drag.x / docScale,
                                                            yPt = overlays[idx2].yPt - drag.y / docScale
                                                        )
                                                    }
                                                }
                                                .pointerInput(ov.id) {
                                                    detectTapGestures { selectedId = ov.id }
                                                }
                                            )
                                            if (sel) Box(Modifier.align(Alignment.TopEnd).size(16.dp)
                                                .background(Color(0xFF757575), RoundedCornerShape(8.dp))
                                                .clickable { overlays.removeAll { it.id == ov.id }; selectedId = null }
                                            ) { Icon(Icons.Default.Close, null,
                                                Modifier.size(10.dp).align(Alignment.Center), tint = Color.White) }
                                        }

                                        OverlayKind.REDACT -> {
                                            Box(Modifier.fillMaxSize()
                                                .background(Color.Black)
                                                .pointerInput(ov.id) {
                                                    detectDragGestures { _, drag ->
                                                        val idx2 = overlays.indexOfFirst { it.id == ov.id }
                                                        if (idx2 >= 0) overlays[idx2] = overlays[idx2].copy(
                                                            xPt = overlays[idx2].xPt + drag.x / docScale,
                                                            yPt = overlays[idx2].yPt - drag.y / docScale
                                                        )
                                                    }
                                                }
                                                .pointerInput(ov.id) { detectTapGestures { selectedId = ov.id } }
                                            )
                                            if (sel) Box(Modifier.align(Alignment.TopEnd).size(16.dp)
                                                .background(Color(0xFFB71C1C), RoundedCornerShape(8.dp))
                                                .clickable { overlays.removeAll { it.id == ov.id }; selectedId = null }
                                            ) { Icon(Icons.Default.Close, null,
                                                Modifier.size(10.dp).align(Alignment.Center), tint = Color.White) }
                                        }

                                        OverlayKind.ANNOTATE -> {
                                            Box(Modifier.fillMaxSize()
                                                .background(Color(0xFFFFF9C4), RoundedCornerShape(4.dp))
                                                .border(1.dp, Color(0xFFF9A825), RoundedCornerShape(4.dp))
                                                .padding(4.dp)
                                                .pointerInput(ov.id) { detectTapGestures { selectedId = ov.id } }
                                                .pointerInput(ov.id) {
                                                    detectDragGestures { _, drag ->
                                                        val idx2 = overlays.indexOfFirst { it.id == ov.id }
                                                        if (idx2 >= 0) overlays[idx2] = overlays[idx2].copy(
                                                            xPt = overlays[idx2].xPt + drag.x / docScale,
                                                            yPt = overlays[idx2].yPt - drag.y / docScale
                                                        )
                                                    }
                                                }
                                            ) {
                                                Text(ov.text, fontSize = (8 * docScale / density.density)
                                                    .coerceIn(7f, 12f).sp,
                                                    color = Color(0xFF5D4037), maxLines = 3)
                                            }
                                            if (sel) Box(Modifier.align(Alignment.TopEnd).size(16.dp)
                                                .background(Color(0xFF757575), RoundedCornerShape(8.dp))
                                                .clickable { overlays.removeAll { it.id == ov.id }; selectedId = null }
                                            ) { Icon(Icons.Default.Close, null,
                                                Modifier.size(10.dp).align(Alignment.Center), tint = Color.White) }
                                        }
                                    }
                                }
                            }
                        }

                        // ── Redact / Highlight drawing preview ────────────────
                        val rStart = redactStart; val rCur = redactCurrent
                        if (rStart != null && rCur != null &&
                            (mode == InPlaceMode.REDACT || mode == InPlaceMode.HIGHLIGHT)) {
                            val x  = minOf(rStart.x, rCur.x)
                            val y  = minOf(rStart.y, rCur.y)
                            val w  = abs(rCur.x - rStart.x)
                            val h  = abs(rCur.y - rStart.y)
                            with(density) {
                                Box(Modifier
                                    .absoluteOffset { IntOffset(x.roundToInt(), y.roundToInt()) }
                                    .width(w.toDp()).height(h.toDp())
                                    .background(
                                        if (mode == InPlaceMode.REDACT) Color.Black.copy(0.6f)
                                        else Color(0x80FFEB3B),
                                        RoundedCornerShape(2.dp)
                                    )
                                    .border(1.dp,
                                        if (mode == InPlaceMode.REDACT) Color.White.copy(0.4f)
                                        else Color(0xFFF9A825),
                                        RoundedCornerShape(2.dp))
                                )
                            }
                        }

                        // ── Empty text hint ───────────────────────────────────
                        if (!isLoading && mode == InPlaceMode.TEXT && textBlocks.isEmpty()) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Card(shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.92f))) {
                                    Column(Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.ImageNotSupported, null,
                                            Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Sin texto editable", fontWeight = FontWeight.SemiBold)
                                        Text("PDF basado en imágenes escaneadas.",
                                            fontSize = 12.sp, textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Mode toolbar ──────────────────────────────────────────────────
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Column {
                    // Mode-specific action bar
                    when (mode) {
                        InPlaceMode.INSERT_IMAGE -> {
                            Row(Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.Center) {
                                Button(
                                    onClick = { imagePicker.launch("image/*") },
                                    colors  = ButtonDefaults.buttonColors(containerColor = PdfRed)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Seleccionar imagen de la galería")
                                }
                            }
                        }
                        InPlaceMode.SIGN -> {
                            Row(Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.Center) {
                                Button(
                                    onClick = { showSignPad = true },
                                    colors  = ButtonDefaults.buttonColors(containerColor = PdfRed)
                                ) {
                                    Icon(Icons.Default.Draw, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Dibujar firma →")
                                }
                            }
                        }
                        InPlaceMode.REDACT -> {
                            Surface(color = Color(0xFFFBE9E7)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.TouchApp, null, Modifier.size(14.dp), tint = Color(0xFFBF360C))
                                    Text("Arrastra sobre el texto para redactarlo",
                                        fontSize = 11.sp, color = Color(0xFFBF360C))
                                }
                            }
                        }
                        InPlaceMode.HIGHLIGHT -> {
                            Surface(color = Color(0xFFFFFDE7)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.TouchApp, null, Modifier.size(14.dp), tint = Color(0xFFF57F17))
                                    Text("Arrastra sobre el texto para resaltarlo",
                                        fontSize = 11.sp, color = Color(0xFFF57F17))
                                }
                            }
                        }
                        InPlaceMode.ANNOTATE -> {
                            Surface(color = Color(0xFFF3E5F5)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.TouchApp, null, Modifier.size(14.dp), tint = Color(0xFF6A1B9A))
                                    Text("Toca cualquier punto del documento para añadir una nota",
                                        fontSize = 11.sp, color = Color(0xFF6A1B9A))
                                }
                            }
                        }
                        else -> {}
                    }

                    // Mode selector tabs
                    Row(
                        Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        InPlaceMode.entries.forEach { m ->
                            val sel = mode == m
                            Surface(
                                onClick      = {
                                    mode        = m
                                    selectedId  = null
                                    activeTextIdx = null
                                    focusManager.clearFocus()
                                    if (m == InPlaceMode.TEXT && textBlocks.isEmpty() && !isLoading) {
                                        scope.launch {
                                            textBlocks = toolsVm.extractTextBlocks(context, uri, currentPage)
                                        }
                                    }
                                },
                                shape        = RoundedCornerShape(20.dp),
                                color        = if (sel) PdfRed else MaterialTheme.colorScheme.surfaceVariant,
                                modifier     = Modifier.padding(vertical = 2.dp)
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(m.icon, null, Modifier.size(14.dp),
                                        tint = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(m.label, fontSize = 11.sp,
                                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Page navigation overlay (top-left when pdf loaded)
        if (!isLoading && pageCount > 1) {
            Row(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 130.dp)
                    .background(Color.Black.copy(0.45f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (currentPage > 1) { currentPage--; textEdits = emptyMap(); overlays.clear() } },
                    enabled = currentPage > 1, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, null,
                        Modifier.size(18.dp), tint = Color.White)
                }
                Text("$currentPage/$pageCount", fontSize = 11.sp, color = Color.White,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 2.dp))
                IconButton(
                    onClick = { if (currentPage < pageCount) { currentPage++; textEdits = emptyMap(); overlays.clear() } },
                    enabled = currentPage < pageCount, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, null,
                        Modifier.size(18.dp), tint = Color.White)
                }
            }
        }
        } // end Box
    } // end Surface
}

// ── Signature pad as ModalBottomSheet ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignaturePadBottomSheet(
    onDone    : (Bitmap) -> Unit,
    onDismiss : () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, tonalElevation = 8.dp) {
        val lines   = remember { mutableStateListOf<SignatureLine>() }
        val current = remember { mutableStateListOf<Offset>() }
        var inkColor by remember { mutableStateOf(Color.Black) }

        Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Dibuja tu firma", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { lines.clear(); current.clear() }) {
                        Text("Borrar", color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = {
                        if (lines.isNotEmpty() || current.isNotEmpty())
                            onDone(renderSignature(lines, current, inkColor))
                        else onDismiss()
                    }, colors = ButtonDefaults.buttonColors(containerColor = PdfRed)) {
                        Text("Usar firma")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Ink color row
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Color:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(Color.Black to "Negro", Color(0xFF1565C0) to "Azul", PdfRed to "Verde").forEach { (c, _) ->
                    Box(Modifier.size(if (c == inkColor) 30.dp else 22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(c)
                        .clickable { inkColor = c }
                        .then(if (c == inkColor) Modifier.border(2.dp, Color.Gray, RoundedCornerShape(50)) else Modifier)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // Drawing area
            Box(Modifier.fillMaxWidth().height(220.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))) {
                androidx.compose.foundation.Canvas(
                    Modifier.fillMaxSize().padding(8.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current.add(it) },
                                onDrag      = { c, _ -> c.consume(); current.add(c.position) },
                                onDragEnd   = { lines.add(SignatureLine(current.toList())); current.clear() }
                            )
                        }
                ) {
                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3.dp.toPx(),
                        cap   = androidx.compose.ui.graphics.StrokeCap.Round,
                        join  = androidx.compose.ui.graphics.StrokeJoin.Round)
                    for (line in lines) {
                        if (line.points.size < 2) continue
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(line.points[0].x, line.points[0].y)
                        for (pt in line.points.drop(1)) path.lineTo(pt.x, pt.y)
                        drawPath(path, inkColor, style = stroke)
                    }
                    if (current.size >= 2) {
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(current[0].x, current[0].y)
                        for (pt in current.drop(1)) path.lineTo(pt.x, pt.y)
                        drawPath(path, inkColor, style = stroke)
                    }
                }
                if (lines.isEmpty() && current.isEmpty()) {
                    Text("✍  Desliza para firmar", Modifier.align(Alignment.Center),
                        color = Color(0xFFBDBDBD), fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Save all pending changes ───────────────────────────────────────────────────

private suspend fun saveAll(
    context    : android.content.Context,
    uri        : Uri,
    pageNum    : Int,
    textBlocks : List<PdfTools.PdfTextBlock>,
    textEdits  : Map<Int, String>,
    overlays   : List<PlacedOverlay>,
    toolsVm    : ToolsViewModel
) {
    // 1. Text edits
    val editList = textEdits
        .filter { (idx, t) -> idx < textBlocks.size && t != textBlocks[idx].text }
        .map { (idx, t) -> textBlocks[idx] to t }
    if (editList.isNotEmpty()) {
        toolsVm.applyWysiwygEdits(context, uri, editList, pageNum)
        return   // result flows back via vmResult; UI handles chaining
    }

    // 2. Overlays: stamp images/signatures
    val bitmapOverlays = overlays.filter { it.kind == OverlayKind.IMAGE || it.kind == OverlayKind.SIGN }
    if (bitmapOverlays.isNotEmpty()) {
        val first = bitmapOverlays.first()
        val bmp   = first.bitmap ?: return
        toolsVm.stampSignature(context, uri, bmp, pageNum,
            first.xPt, first.yPt, first.wPt, first.hPt)
        return
    }

    // 3. Redact areas
    val redacts = overlays.filter { it.kind == OverlayKind.REDACT }
    if (redacts.isNotEmpty()) {
        toolsVm.redactAreas(context, uri, redacts.map {
            PdfTools.RedactArea(pageNum, it.xPt, it.yPt, it.xPt + it.wPt, it.yPt + it.hPt)
        })
        return
    }

    // 4. Annotations
    val annots = overlays.filter { it.kind == OverlayKind.ANNOTATE }
    if (annots.isNotEmpty()) {
        val a = annots.first()
        toolsVm.addAnnotation(context, uri, "comment", a.text, pageNum,
            a.xPt, a.yPt, a.wPt, a.hPt, 0xFF006D77.toInt())
        return
    }

    // 5. Highlights (stored as annotations of type "highlight")
    val highlights = overlays.filter { it.kind == OverlayKind.HIGHLIGHT }
    if (highlights.isNotEmpty()) {
        val h = highlights.first()
        toolsVm.addAnnotation(context, uri, "highlight", "", pageNum,
            h.xPt, h.yPt, h.wPt, h.hPt, 0x80FFEB3B.toInt())
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun hitTestBlock(
    tap    : Offset,
    blocks : List<PdfTools.PdfTextBlock>,
    scale  : Float,
    pageH  : Float,
    density: Float
): Int? {
    val pad = 6f * density
    for ((idx, b) in blocks.withIndex()) {
        val l = b.x * scale;       val r = l + b.width  * scale
        val t = (pageH - b.y - b.height) * scale; val btm = t + b.height * scale
        if (tap.x in (l-pad)..(r+pad) && tap.y in (t-pad)..(btm+pad)) return idx
    }
    return null
}

private suspend fun loadBitmapFromUri(
    context : android.content.Context,
    uri     : Uri
): Bitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 1 }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        }
    } catch (_: Exception) { null }
}
