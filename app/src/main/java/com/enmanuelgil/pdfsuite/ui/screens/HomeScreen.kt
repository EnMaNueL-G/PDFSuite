package com.enmanuelgil.pdfsuite.ui.screens

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.pdfsuite.model.PdfEntry
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.HomeViewModel

// ── Quick-access tool descriptors ─────────────────────────────────────────────

private data class QuickTool(
    val id    : String,
    val label : String,
    val icon  : ImageVector,
    val color : Color
)

private val QUICK_TOOLS = listOf(
    QuickTool("edit",      "Editar PDF",      Icons.Default.Edit,             Color(0xFFD32F2F)),
    QuickTool("scan",      "Escanear",        Icons.Default.DocumentScanner,  Color(0xFF1976D2)),
    QuickTool("sign",      "Firmar",          Icons.Default.Draw,             Color(0xFF388E3C)),
    QuickTool("compress",  "Comprimir",       Icons.Default.Compress,         Color(0xFF7B1FA2)),
    QuickTool("convert",   "Imagen a PDF",    Icons.Default.Image,            Color(0xFFE64A19)),
    QuickTool("merge",     "Combinar PDFs",   Icons.Default.CallMerge,        Color(0xFF0097A7)),
    QuickTool("split",     "Dividir PDF",     Icons.Default.CallSplit,        Color(0xFFF57C00)),
    QuickTool("more",      "Más herramientas",Icons.Default.GridView,         Color(0xFF546E7A)),
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPdf    : (Uri) -> Unit,
    onOpenPicker : () -> Unit,
    homeVm       : HomeViewModel
) {
    val context   = LocalContext.current
    val recents   by homeVm.recents.collectAsState()
    val favorites by homeVm.favorites.collectAsState()
    val isLoading by homeVm.isLoading.collectAsState()

    LaunchedEffect(Unit) { homeVm.load(context) }

    // Callback: quick tool tapped — open picker then route to tool
    // For tools needing a PDF first we open the picker; "more" goes to tools tab
    // (navigation to tools tab is done by caller via a flag)
    var pendingToolId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        item {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                            .background(PdfRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("PS", color = Color.White, fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PDFSuite", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("optisuite.app", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Search button
                    IconButton(onClick = onOpenPicker) {
                        Icon(Icons.Default.Search, "Buscar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ── Quick tools grid ──────────────────────────────────────────────────
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
                Text("Herramientas", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))

                val rows = QUICK_TOOLS.chunked(4)
                rows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { tool ->
                            QuickToolCell(
                                tool     = tool,
                                modifier = Modifier.weight(1f),
                                onClick  = { onOpenPicker() }  // opens PDF, then tools tab handles it
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    if (row != rows.last()) Spacer(Modifier.height(8.dp))
                }
            }
        }

        // ── Recents section ───────────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, null, tint = PdfRed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Recientes", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.weight(1f))
                if (recents.isNotEmpty()) {
                    TextButton(onClick = {}) {
                        Text("Ver todo", fontSize = 12.sp, color = PdfRed)
                    }
                }
            }
        }

        when {
            isLoading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    CircularProgressIndicator(color = PdfRed, modifier = Modifier.size(28.dp))
                }
            }
            recents.isEmpty() -> item { HomeEmptyState(onOpenPicker) }
            else -> {
                items(recents, key = { it.uri.toString() }) { entry ->
                    RecentItem(
                        entry    = entry,
                        onClick  = { onOpenPdf(entry.uri) },
                        onFav    = { homeVm.toggleFavorite(context, entry.uri) },
                        onRemove = { homeVm.removeRecent(context, entry.uri) }
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                }
            }
        }
    }
}

// ── Quick tool cell ───────────────────────────────────────────────────────────

@Composable
private fun QuickToolCell(
    tool     : QuickTool,
    modifier : Modifier,
    onClick  : () -> Unit
) {
    Surface(
        onClick         = onClick,
        modifier        = modifier,
        shape           = RoundedCornerShape(14.dp),
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        tonalElevation  = 0.dp
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(tool.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(22.dp))
            }
            Text(tool.label, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center, maxLines = 2,
                lineHeight = 13.sp)
        }
    }
}

// ── Files screen (Archivos tab) ───────────────────────────────────────────────

@Composable
fun FilesScreen(
    onOpenPdf    : (Uri) -> Unit,
    onOpenPicker : () -> Unit,
    homeVm       : HomeViewModel
) {
    val context   = LocalContext.current
    val recents   by homeVm.recents.collectAsState()
    val favorites by homeVm.favorites.collectAsState()
    val isLoading by homeVm.isLoading.collectAsState()

    LaunchedEffect(Unit) { homeVm.load(context) }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Archivos", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenPicker) {
                    Icon(Icons.Default.Add, "Abrir PDF", tint = PdfRed)
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Favorites
            if (favorites.isNotEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp))
                        Text("Favoritos", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(favorites) { entry ->
                            FavoriteCard(entry,
                                onClick = { onOpenPdf(entry.uri) },
                                onFav   = { homeVm.toggleFavorite(context, entry.uri) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // All recents
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.History, null, tint = PdfRed,
                        modifier = Modifier.size(16.dp))
                    Text("Historial", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            when {
                isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        CircularProgressIndicator(color = PdfRed, modifier = Modifier.size(28.dp))
                    }
                }
                recents.isEmpty() -> item { HomeEmptyState(onOpenPicker) }
                else -> items(recents, key = { it.uri.toString() }) { entry ->
                    RecentItem(
                        entry    = entry,
                        onClick  = { onOpenPdf(entry.uri) },
                        onFav    = { homeVm.toggleFavorite(context, entry.uri) },
                        onRemove = { homeVm.removeRecent(context, entry.uri) }
                    )
                    HorizontalDivider(Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outline.copy(0.3f))
                }
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
internal fun RecentItem(
    entry   : PdfEntry,
    onClick : () -> Unit,
    onFav   : () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                Modifier.size(44.dp, 56.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFFEBEE)),
                contentAlignment = Alignment.Center
            ) {
                if (entry.thumbnail != null) {
                    Image(entry.thumbnail.asImageBitmap(), null,
                        contentScale = ContentScale.Crop,
                        modifier     = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.PictureAsPdf, null, tint = PdfRed.copy(0.5f),
                        modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(entry.displayName, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("${entry.pageCount} pág. · ${entry.sizeLabel}", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                        modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text        = { Text(if (entry.isFavorite) "Quitar favorito" else "Agregar favorito") },
                        onClick     = { onFav(); showMenu = false },
                        leadingIcon = { Icon(if (entry.isFavorite) Icons.Default.StarBorder else Icons.Default.Star, null) }
                    )
                    DropdownMenuItem(
                        text        = { Text("Quitar del historial") },
                        onClick     = { onRemove(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun FavoriteCard(entry: PdfEntry, onClick: () -> Unit, onFav: () -> Unit) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.width(110.dp),
        shape     = RoundedCornerShape(10.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(130.dp)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))) {
                if (entry.thumbnail != null) {
                    Image(entry.thumbnail.asImageBitmap(), null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFFFFEBEE)), Alignment.Center) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = PdfRed.copy(0.4f),
                            modifier = Modifier.size(32.dp))
                    }
                }
                IconButton(onClick = onFav,
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp)) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(12.dp))
                }
            }
            Text(entry.name, fontSize = 10.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                color    = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun HomeEmptyState(onOpenPicker: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFFFEBEE)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PictureAsPdf, null, tint = PdfRed.copy(0.5f),
                modifier = Modifier.size(36.dp))
        }
        Text("No hay archivos recientes", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text("Pulsa + para abrir un PDF",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onOpenPicker,
            colors   = ButtonDefaults.buttonColors(containerColor = PdfRed),
            shape    = RoundedCornerShape(24.dp)) {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Abrir PDF")
        }
    }
}
