package com.enmanuelgil.pdfsuite.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.pdfsuite.model.PdfEntry
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPdf : (Uri) -> Unit,
    vm        : HomeViewModel = viewModel()
) {
    val context   = LocalContext.current
    val recents   by vm.recents.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    LaunchedEffect(Unit) { vm.load(context) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            vm.addRecent(context, it)
            onOpenPdf(it)
        }
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                            .background(PdfRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("PS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PDFSuite", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("optisuite.app", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = { picker.launch(arrayOf("application/pdf")) },
                icon           = { Icon(Icons.Default.Add, null) },
                text           = { Text("Abrir PDF") },
                containerColor = PdfRed,
                contentColor   = Color.White
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            contentPadding      = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier            = Modifier.fillMaxSize().padding(padding)
        ) {
            // ── Quick open banner ──
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(PdfRed, Color(0xFFC62828))))
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Column {
                        Text("Tu lector PDF\nprivado y potente",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                            lineHeight = 26.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Sin anuncios · Sin trackers · 100% local",
                            color = Color.White.copy(0.85f), fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("application/pdf")) },
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border  = BorderStroke(1.dp, Color.White.copy(0.7f))
                        ) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Seleccionar PDF", fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── Favorites ──
            if (favorites.isNotEmpty()) {
                item {
                    SectionHeader("Favoritos", Icons.Default.Star)
                }
                item {
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(favorites) { entry ->
                            FavoriteCard(entry, onClick = { onOpenPdf(entry.uri) },
                                onFav = { vm.toggleFavorite(context, entry.uri) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── Recents header ──
            item { SectionHeader("Recientes", Icons.Default.History) }

            when {
                isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                        CircularProgressIndicator(color = PdfRed)
                    }
                }
                recents.isEmpty() -> item {
                    EmptyState()
                }
                else -> {
                    items(recents, key = { it.uri.toString() }) { entry ->
                        RecentItem(
                            entry    = entry,
                            onClick  = { onOpenPdf(entry.uri) },
                            onFav    = { vm.toggleFavorite(context, entry.uri) },
                            onRemove = { vm.removeRecent(context, entry.uri) }
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 76.dp),
                            color = MaterialTheme.colorScheme.outline.copy(0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = PdfRed, modifier = Modifier.size(18.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun FavoriteCard(entry: PdfEntry, onClick: () -> Unit, onFav: () -> Unit) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.width(130.dp),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(160.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                if (entry.thumbnail != null) {
                    Image(bitmap = entry.thumbnail.asImageBitmap(), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFFFFEBEE)), Alignment.Center) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = PdfRed.copy(0.5f),
                            modifier = Modifier.size(40.dp))
                    }
                }
                IconButton(onClick = onFav,
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                    Icon(Icons.Default.Star, null,
                        tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(entry.name, fontSize = 11.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("${entry.pageCount} págs", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecentItem(
    entry   : PdfEntry,
    onClick : () -> Unit,
    onFav   : () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail / icon
            Box(
                Modifier.size(44.dp, 58.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFFEBEE)),
                contentAlignment = Alignment.Center
            ) {
                if (entry.thumbnail != null) {
                    Image(bitmap = entry.thumbnail.asImageBitmap(), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.PictureAsPdf, null, tint = PdfRed.copy(0.6f),
                        modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(entry.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text("${entry.pageCount} páginas · ${entry.sizeLabel}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                        modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (entry.isFavorite) "Quitar favorito" else "Favorito") },
                        onClick = { onFav(); showMenu = false },
                        leadingIcon = { Icon(if (entry.isFavorite) Icons.Default.StarBorder else Icons.Default.Star, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Quitar del historial") },
                        onClick = { onRemove(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(Color(0xFFFFEBEE)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PictureAsPdf, null, tint = PdfRed.copy(0.5f),
                modifier = Modifier.size(40.dp))
        }
        Text("Sin archivos recientes", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface)
        Text("Pulsa Abrir PDF para comenzar",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
