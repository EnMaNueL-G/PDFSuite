package com.enmanuelgil.pdfsuite.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.pdfsuite.model.ToolResult
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.ReaderViewModel
import com.enmanuelgil.pdfsuite.ui.viewmodel.ToolsViewModel

// ── Bottom tab definitions ────────────────────────────────────────────────────

private enum class ReaderTab { COMMENT, EDIT, FILL_SIGN, PAGES }

private data class TabDef(val tab: ReaderTab, val label: String, val icon: ImageVector)

private val READER_TABS = listOf(
    TabDef(ReaderTab.COMMENT,   "Comentario",      Icons.Default.Comment),
    TabDef(ReaderTab.EDIT,      "Editar",          Icons.Default.Edit),
    TabDef(ReaderTab.FILL_SIGN, "Rellenar/Firmar", Icons.Default.Draw),
    TabDef(ReaderTab.PAGES,     "Páginas",         Icons.Default.ViewList),
)

// ── Sub-tools per tab ─────────────────────────────────────────────────────────

private data class SubTool(val id: String, val label: String, val icon: ImageVector, val color: Color)

private val EDIT_TOOLS = listOf(
    SubTool("addtext",   "Texto",    Icons.Default.TextFields,         Color(0xFFF57F17)),
    SubTool("insertimg", "Imagen",   Icons.Default.AddPhotoAlternate,  Color(0xFF7E57C2)),
    SubTool("redact",    "Redactar", Icons.Default.VisibilityOff,      Color(0xFF37474F)),
    SubTool("compress",  "Comprimir",Icons.Default.Compress,           Color(0xFF43A047)),
    SubTool("translate", "Traducir", Icons.Default.Translate,          Color(0xFF00838F)),
)

private val COMMENT_TOOLS = listOf(
    SubTool("annotate",  "Anotar",    Icons.Default.Comment,           Color(0xFFF06292)),
    SubTool("highlight", "Resaltar",  Icons.Default.Highlight,         Color(0xFFF59E0B)),
    SubTool("stamp",     "Sello",     Icons.Default.Approval,          Color(0xFF1976D2)),
)

private val FILL_SIGN_TOOLS = listOf(
    SubTool("fillform",  "Formulario", Icons.Default.Assignment,        Color(0xFF039BE5)),
    SubTool("sign",      "Firma",      Icons.Default.Draw,              Color(0xFF00897B)),
)

private val PAGES_TOOLS = listOf(
    SubTool("organize",  "Organizar",  Icons.Default.ViewList,          Color(0xFF00ACC1)),
    SubTool("split",     "Dividir",    Icons.Default.CallSplit,         Color(0xFFFF7043)),
    SubTool("rotate",    "Rotar",      Icons.Default.RotateRight,       Color(0xFF1E88E5)),
)

// ── Overlay states ────────────────────────────────────────────────────────────

private sealed class ReaderOverlay {
    object None                                                          : ReaderOverlay()
    data class InPlace(val pages: Int, val startPage: Int,
                       val mode: InPlaceMode = InPlaceMode.TEXT)        : ReaderOverlay()
    data class Organize(val pages: Int)                                  : ReaderOverlay()
    object Translate                                                     : ReaderOverlay()
    object FillForm                                                      : ReaderOverlay()
}

// ── Main screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    uri     : Uri,
    onBack  : () -> Unit,
    vm      : ReaderViewModel = viewModel(),
    toolsVm : ToolsViewModel  = viewModel()
) {
    val context     = LocalContext.current
    val pageCount   by vm.pageCount.collectAsState()
    val currentPage by vm.currentPage.collectAsState()
    val isLoading   by vm.isLoading.collectAsState()
    val nightMode   by vm.nightMode.collectAsState()
    val showBars    by vm.showBars.collectAsState()
    val fileName    by vm.fileName.collectAsState()
    val showSearch  by vm.showSearch.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()

    var activeTab     by remember { mutableStateOf<ReaderTab?>(null) }  // null = no tab active
    var readerOverlay by remember { mutableStateOf<ReaderOverlay>(ReaderOverlay.None) }

    val toolResult by toolsVm.result.collectAsState()
    val isWorking  by toolsVm.isWorking.collectAsState()

    LaunchedEffect(uri) { vm.open(context, uri) }

    val bgColor = if (nightMode) Color(0xFF121212) else Color(0xFFF5F5F5)

    // ── Full-screen tool overlays ─────────────────────────────────────────────
    val ov = readerOverlay
    if (ov !is ReaderOverlay.None) {
        AnimatedVisibility(visible = true,
            enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            when (ov) {
                is ReaderOverlay.InPlace -> InPlaceEditorScreen(
                    uri       = uri,
                    pageCount = ov.pages,
                    startPage = ov.startPage,
                    startMode = ov.mode,
                    toolsVm   = toolsVm,
                    onDismiss = { readerOverlay = ReaderOverlay.None; vm.open(context, uri) }
                )
                is ReaderOverlay.Organize -> OrganizePagesScreen(uri, ov.pages, toolsVm) { readerOverlay = ReaderOverlay.None }
                is ReaderOverlay.Translate -> TranslateScreen(uri) { readerOverlay = ReaderOverlay.None }
                is ReaderOverlay.FillForm  -> FormFillScreen(uri, toolsVm) { readerOverlay = ReaderOverlay.None }
                else -> {}
            }
        }

        // Result dialog for non-inplace tools
        if (ov !is ReaderOverlay.InPlace && toolResult != null && toolResult !is ToolResult.Loading) {
            AlertDialog(
                onDismissRequest = { toolsVm.clearResult(); vm.open(context, uri) },
                title = { Text(if (toolResult is ToolResult.Success) "¡Guardado!" else "Error",
                    fontWeight = FontWeight.Bold) },
                text  = { Text(when (val r = toolResult) {
                    is ToolResult.Success -> r.message
                    is ToolResult.Error   -> r.message
                    else -> ""
                }) },
                confirmButton = {
                    Button(onClick = { toolsVm.clearResult(); vm.open(context, uri) },
                        colors = ButtonDefaults.buttonColors(containerColor = PdfRed)) {
                        Text("OK")
                    }
                }
            )
        }
        return
    }

    // ── Reader layout ─────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize().background(bgColor)) {

        when {
            isLoading   -> CircularProgressIndicator(color = PdfRed,
                modifier = Modifier.align(Alignment.Center))
            pageCount == 0 -> Column(Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp))
                Text("No se pudo abrir el PDF", textAlign = TextAlign.Center,
                    color = if (nightMode) Color.White else Color(0xFF212121))
                TextButton(onClick = onBack) { Text("Volver") }
            }
            else -> {
                // Calculate bottom padding: topbar + bottom tab bar
                val bottomPad = if (activeTab != null) 116.dp else 72.dp
                PageList(uri, pageCount, nightMode,
                    onPageClick  = { vm.toggleBars() },
                    onPageChange = { vm.setPage(it) },
                    vm           = vm,
                    extraBottomPad = bottomPad)
            }
        }

        // Working overlay
        if (isWorking) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.3f)),
                contentAlignment = Alignment.Center) {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = PdfRed)
                        Text("Procesando…", fontSize = 14.sp)
                    }
                }
            }
        }

        // ── Top bar ───────────────────────────────────────────────────────────
        AnimatedVisibility(visible = showBars,
            enter = slideInVertically { -it }, exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)) {
            Surface(color = if (nightMode) Color(0xF0121212) else Color.White,
                modifier = Modifier.fillMaxWidth(), shadowElevation = 4.dp) {
                Column {
                    Row(Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                                tint = if (nightMode) Color.White else Color(0xFF212121))
                        }
                        Text(fileName, Modifier.weight(1f).padding(horizontal = 4.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (nightMode) Color.White else Color(0xFF212121),
                            style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { vm.toggleSearch() }) {
                            Icon(Icons.Default.Search, null,
                                tint = if (showSearch) PdfRed
                                else if (nightMode) Color.White else Color(0xFF424242))
                        }
                        IconButton(onClick = { vm.toggleNightMode() }) {
                            Icon(if (nightMode) Icons.Default.WbSunny else Icons.Default.DarkMode,
                                null, tint = if (nightMode) Color.White else Color(0xFF424242))
                        }
                        IconButton(onClick = { vm.share(context, uri) }) {
                            Icon(Icons.Default.Share, null,
                                tint = if (nightMode) Color.White else Color(0xFF424242))
                        }
                    }
                    AnimatedVisibility(visible = showSearch) {
                        OutlinedTextField(
                            value = searchQuery, onValueChange = { vm.setSearch(context, it) },
                            placeholder = { Text("Buscar en el documento…", fontSize = 13.sp) },
                            leadingIcon  = { Icon(Icons.Default.Search, null) },
                            trailingIcon = if (searchQuery.isNotEmpty()) {{
                                IconButton(onClick = { vm.setSearch(context, "") }) {
                                    Icon(Icons.Default.Clear, null) }
                            }} else null,
                            singleLine = true,
                            modifier   = Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(24.dp)
                        )
                    }
                }
            }
        }

        // ── Bottom editor bar (MobiPDF style) ─────────────────────────────────
        if (pageCount > 0) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {

                // Sub-tools strip (shown when a tab is active)
                AnimatedVisibility(
                    visible = activeTab != null,
                    enter   = slideInVertically { it } + fadeIn(),
                    exit    = slideOutVertically { it } + fadeOut()
                ) {
                    val subTools = when (activeTab) {
                        ReaderTab.COMMENT   -> COMMENT_TOOLS
                        ReaderTab.EDIT      -> EDIT_TOOLS
                        ReaderTab.FILL_SIGN -> FILL_SIGN_TOOLS
                        ReaderTab.PAGES     -> PAGES_TOOLS
                        else                -> emptyList()
                    }
                    Surface(color = if (nightMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface,
                        shadowElevation = 0.dp) {
                        Row(Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            subTools.forEach { tool ->
                                SubToolChip(tool, isWorking) {
                                    when (tool.id) {
                                        "addtext"   -> readerOverlay = ReaderOverlay.InPlace(pageCount, currentPage + 1, InPlaceMode.TEXT)
                                        "annotate",
                                        "stamp"     -> readerOverlay = ReaderOverlay.InPlace(pageCount, currentPage + 1, InPlaceMode.ANNOTATE)
                                        "highlight" -> readerOverlay = ReaderOverlay.InPlace(pageCount, currentPage + 1, InPlaceMode.HIGHLIGHT)
                                        "sign"      -> readerOverlay = ReaderOverlay.InPlace(pageCount, currentPage + 1, InPlaceMode.SIGN)
                                        "insertimg" -> readerOverlay = ReaderOverlay.InPlace(pageCount, currentPage + 1, InPlaceMode.INSERT_IMAGE)
                                        "redact"    -> readerOverlay = ReaderOverlay.InPlace(pageCount, currentPage + 1, InPlaceMode.REDACT)
                                        "organize"  -> readerOverlay = ReaderOverlay.Organize(pageCount)
                                        "translate" -> readerOverlay = ReaderOverlay.Translate
                                        "compress"  -> { toolsVm.compress(context, uri); activeTab = null }
                                        "fillform"  -> readerOverlay = ReaderOverlay.FillForm
                                        "split"     -> readerOverlay = ReaderOverlay.Organize(pageCount)
                                        "rotate"    -> { toolsVm.rotateAll(context, uri, 90); activeTab = null }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab bar
                Surface(
                    color           = if (nightMode) Color(0xFF1A1A1A) else Color.White,
                    shadowElevation = 12.dp
                ) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp),
                        verticalAlignment = Alignment.CenterVertically) {

                        // Page counter (left side)
                        Text("${currentPage + 1}/$pageCount",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color    = if (nightMode) Color.White.copy(0.6f) else Color(0xFF757575),
                            modifier = Modifier.padding(start = 10.dp).width(40.dp),
                            textAlign = TextAlign.Center)

                        // Tabs
                        READER_TABS.forEach { tabDef ->
                            val selected = activeTab == tabDef.tab
                            Column(
                                Modifier.weight(1f)
                                    .clickable {
                                        activeTab = if (selected) null else tabDef.tab
                                        if (!selected && showBars) vm.toggleBars()
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(tabDef.icon, null,
                                    modifier = Modifier.size(20.dp),
                                    tint     = if (selected) PdfRed
                                              else if (nightMode) Color.White.copy(0.6f)
                                              else Color(0xFF757575))
                                Text(tabDef.label, fontSize = 9.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) PdfRed
                                           else if (nightMode) Color.White.copy(0.6f)
                                           else Color(0xFF757575),
                                    maxLines = 1)
                                // Active indicator
                                Box(Modifier.height(2.dp).width(24.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(if (selected) PdfRed else Color.Transparent))
                            }
                        }

                        // Prev/Next quick nav (right side)
                        Row(Modifier.padding(end = 4.dp)) {
                            IconButton(onClick = { vm.goToPage(currentPage - 1) },
                                enabled  = currentPage > 0,
                                modifier = Modifier.size(36.dp)) {
                                Icon(Icons.AutoMirrored.Filled.NavigateBefore, null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (nightMode) Color.White else Color(0xFF424242))
                            }
                            IconButton(onClick = { vm.goToPage(currentPage + 1) },
                                enabled  = currentPage < pageCount - 1,
                                modifier = Modifier.size(36.dp)) {
                                Icon(Icons.AutoMirrored.Filled.NavigateNext, null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (nightMode) Color.White else Color(0xFF424242))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Sub-tool chip ─────────────────────────────────────────────────────────────

@Composable
private fun SubToolChip(tool: SubTool, working: Boolean, onClick: () -> Unit) {
    Surface(
        onClick         = onClick,
        enabled         = !working,
        shape           = RoundedCornerShape(10.dp),
        color           = tool.color.copy(alpha = 0.1f),
        shadowElevation = 0.dp
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(22.dp))
            Text(tool.label, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                color = tool.color, maxLines = 1)
        }
    }
}

// ── Page list ─────────────────────────────────────────────────────────────────

@Composable
private fun PageList(
    uri            : Uri,
    pageCount      : Int,
    nightMode      : Boolean,
    onPageClick    : () -> Unit,
    onPageChange   : (Int) -> Unit,
    vm             : ReaderViewModel,
    extraBottomPad : androidx.compose.ui.unit.Dp = 72.dp
) {
    val context   = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(listState.firstVisibleItemIndex) { onPageChange(listState.firstVisibleItemIndex) }

    val targetPage by vm.targetPage.collectAsState()
    LaunchedEffect(targetPage) {
        if (targetPage >= 0) { listState.animateScrollToItem(targetPage); vm.clearTargetPage() }
    }

    LazyColumn(state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = extraBottomPad),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()) {
        items(pageCount, key = { it }) { idx ->
            PageItem(uri, idx, nightMode, onPageClick, vm)
        }
    }
}

// ── Single page ───────────────────────────────────────────────────────────────

@Composable
private fun PageItem(
    uri       : Uri,
    pageIndex : Int,
    nightMode : Boolean,
    onClick   : () -> Unit,
    vm        : ReaderViewModel
) {
    val context = LocalContext.current
    val bitmap  by produceState<android.graphics.Bitmap?>(null, uri, pageIndex) {
        value = vm.getPage(context, uri, pageIndex, nightMode)
    }
    var scale   by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(Modifier.fillMaxWidth().wrapContentHeight()
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(0.5f, 5f)
                if (scale > 1f) { offsetX += pan.x; offsetY += pan.y }
                else            { offsetX = 0f; offsetY = 0f }
            }
        }
        .clickable { onClick(); scale = 1f; offsetX = 0f; offsetY = 0f }
    ) {
        val nightFilter = if (nightMode) ColorFilter.colorMatrix(
            ColorMatrix().also { it.setToScale(0.90f, 0.86f, 0.76f, 1f) }
        ) else null

        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Página ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                colorFilter  = nightFilter,
                modifier     = Modifier.fillMaxWidth().wrapContentHeight()
                    .graphicsLayer { scaleX = scale; scaleY = scale
                        translationX = offsetX; translationY = offsetY })
        } else {
            Box(Modifier.fillMaxWidth().height(300.dp)
                .background(if (nightMode) Color(0xFF1A1A1A) else Color(0xFFEEEEEE)),
                contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PdfRed, modifier = Modifier.size(32.dp))
            }
        }
    }
}
