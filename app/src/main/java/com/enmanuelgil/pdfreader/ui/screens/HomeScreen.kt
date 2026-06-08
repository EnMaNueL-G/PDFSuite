package com.enmanuelgil.pdfreader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.enmanuelgil.pdfreader.model.PdfEntry
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.enmanuelgil.pdfreader.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPdf     : (Uri) -> Unit,
    vm            : HomeViewModel = viewModel()
) {
    val context       = LocalContext.current
    val recents       by vm.recents.collectAsState()
    val favorites     by vm.favorites.collectAsState()
    val isLoading     by vm.isLoading.collectAsState()

    LaunchedEffect(Unit) { vm.load(context) }

    // File picker launcher
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // Persist permission to re-open this URI later
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            vm.addRecent(context, it)
            onOpenPdf(it)
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("application/pdf")) },
                icon    = { Icon(Icons.Default.Add, null) },
                text    = { Text("Abrir PDF") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier            = Modifier.fillMaxSize().padding(padding)
        ) {
            // ── Header ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("PDF Reader",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text("Sin anuncios · Sin trackers · Privado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
                }
            }

            // ── Favorites ──
            if (favorites.isNotEmpty()) {
                item {
                    SectionTitle("Favoritos", Icons.Default.Star)
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding        = PaddingValues(end = 16.dp)
                    ) {
                        items(favorites) { entry ->
                            PdfCard(
                                entry        = entry,
                                onClick      = { onOpenPdf(entry.uri) },
                                onFavorite   = { vm.toggleFavorite(context, entry.uri) }
                            )
                        }
                    }
                }
            }

            // ── Recents ──
            item {
                SectionTitle("Recientes", Icons.Default.History)
            }

            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (recents.isEmpty()) {
                item {
                    EmptyState(
                        icon    = Icons.Default.PictureAsPdf,
                        message = "Aún no hay archivos recientes.\nPulsa + para abrir un PDF."
                    )
                }
            } else {
                items(recents, key = { it.uri.toString() }) { entry ->
                    PdfListItem(
                        entry      = entry,
                        onClick    = { onOpenPdf(entry.uri) },
                        onFavorite = { vm.toggleFavorite(context, entry.uri) },
                        onRemove   = { vm.removeRecent(context, entry.uri) }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) } // FAB clearance
        }
    }
}

// ── Section title ─────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

// ── PDF card (horizontal scroll, favorites) ───────────────────────────────────

@Composable
private fun PdfCard(
    entry     : PdfEntry,
    onClick   : () -> Unit,
    onFavorite: () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.width(140.dp),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Thumbnail
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                if (entry.thumbnail != null) {
                    Image(
                        bitmap = entry.thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PictureAsPdf, null,
                            tint = MaterialTheme.colorScheme.primary.copy(0.4f),
                            modifier = Modifier.size(48.dp))
                    }
                }
                // Favorite button overlay
                IconButton(
                    onClick  = onFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                ) {
                    Icon(
                        if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        null,
                        tint     = Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            // Info
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("${entry.pageCount} págs · ${entry.sizeLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        }
    }
}

// ── PDF list item (recents, vertical list) ─────────────────────────────────────

@Composable
private fun PdfListItem(
    entry     : PdfEntry,
    onClick   : () -> Unit,
    onFavorite: () -> Unit,
    onRemove  : () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick  = onClick,
        color    = MaterialTheme.colorScheme.surface,
        shape    = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                Modifier
                    .size(48.dp, 64.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                if (entry.thumbnail != null) {
                    Image(
                        bitmap       = entry.thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier     = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Icon(Icons.Default.PictureAsPdf, null,
                            tint = MaterialTheme.colorScheme.primary.copy(0.5f),
                            modifier = Modifier.size(28.dp))
                    }
                }
            }

            // Info
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.displayName, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("${entry.pageCount} páginas · ${entry.sizeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }

            // Menu
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                        modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded         = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text    = { Text(if (entry.isFavorite) "Quitar favorito" else "Añadir favorito") },
                        onClick = { onFavorite(); showMenu = false },
                        leadingIcon = {
                            Icon(if (entry.isFavorite) Icons.Default.StarBorder else Icons.Default.Star, null)
                        }
                    )
                    DropdownMenuItem(
                        text    = { Text("Eliminar del historial") },
                        onClick = { onRemove(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null,
            tint = MaterialTheme.colorScheme.onBackground.copy(0.2f),
            modifier = Modifier.size(56.dp))
        Text(message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(0.4f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
