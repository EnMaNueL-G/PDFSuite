package com.enmanuelgil.pdfreader.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.pdfreader.ui.viewmodel.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    uri      : Uri,
    onBack   : () -> Unit,
    vm       : ReaderViewModel = viewModel()
) {
    val context    = LocalContext.current
    val pageCount  by vm.pageCount.collectAsState()
    val currentPage by vm.currentPage.collectAsState()
    val isLoading  by vm.isLoading.collectAsState()
    val nightMode  by vm.nightMode.collectAsState()
    val showBars   by vm.showBars.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val showSearch by vm.showSearch.collectAsState()

    LaunchedEffect(uri) { vm.open(context, uri) }

    val bgColor = if (nightMode) Color(0xFF121212) else Color(0xFFF5F5F5)

    Box(
        Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            pageCount == 0 -> {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Error, null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Text("No se pudo abrir el PDF",
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center)
                    TextButton(onClick = onBack) { Text("Volver") }
                }
            }
            else -> {
                // ── Pages ──
                PageList(
                    uri         = uri,
                    pageCount   = pageCount,
                    nightMode   = nightMode,
                    onPageClick = { vm.toggleBars() },
                    onPageChange = { vm.setPage(it) },
                    vm          = vm
                )
            }
        }

        // ── Top bar ──
        AnimatedVisibility(
            visible = showBars,
            enter   = slideInVertically { -it },
            exit    = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(0.95f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Volver",
                                tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            vm.fileName.value,
                            style    = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            color    = MaterialTheme.colorScheme.onSurface,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        // Search toggle
                        IconButton(onClick = { vm.toggleSearch() }) {
                            Icon(Icons.Default.Search, "Buscar",
                                tint = if (showSearch) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface)
                        }
                        // Night mode toggle
                        IconButton(onClick = { vm.toggleNightMode() }) {
                            Icon(
                                if (nightMode) Icons.Default.WbSunny else Icons.Default.DarkMode,
                                "Modo lectura",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Share
                        IconButton(onClick = { vm.share(context, uri) }) {
                            Icon(Icons.Default.Share, "Compartir",
                                tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Search bar
                    AnimatedVisibility(visible = showSearch) {
                        OutlinedTextField(
                            value         = searchQuery,
                            onValueChange = { vm.setSearch(context, it) },
                            placeholder   = { Text("Buscar en el documento…", fontSize = 14.sp) },
                            leadingIcon   = { Icon(Icons.Default.Search, null) },
                            trailingIcon  = if (searchQuery.isNotEmpty()) {{
                                IconButton(onClick = { vm.setSearch(context, "") }) {
                                    Icon(Icons.Default.Clear, null)
                                }
                            }} else null,
                            singleLine    = true,
                            modifier      = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            shape         = RoundedCornerShape(24.dp),
                            colors        = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }

        // ── Bottom bar (page indicator + navigation) ──
        AnimatedVisibility(
            visible  = showBars,
            enter    = slideInVertically { it },
            exit     = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color    = MaterialTheme.colorScheme.surface.copy(0.95f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick  = { vm.goToPage(0) },
                        enabled  = currentPage > 0
                    ) {
                        Icon(Icons.Default.FirstPage, "Primera", modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick  = { vm.goToPage(currentPage - 1) },
                        enabled  = currentPage > 0
                    ) {
                        Icon(Icons.Default.NavigateBefore, "Anterior", modifier = Modifier.size(20.dp))
                    }

                    Text(
                        "${currentPage + 1} / $pageCount",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    IconButton(
                        onClick  = { vm.goToPage(currentPage + 1) },
                        enabled  = currentPage < pageCount - 1
                    ) {
                        Icon(Icons.Default.NavigateNext, "Siguiente", modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick  = { vm.goToPage(pageCount - 1) },
                        enabled  = currentPage < pageCount - 1
                    ) {
                        Icon(Icons.Default.LastPage, "Última", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ── Page list view ────────────────────────────────────────────────────────────

@Composable
private fun PageList(
    uri         : Uri,
    pageCount   : Int,
    nightMode   : Boolean,
    onPageClick : () -> Unit,
    onPageChange: (Int) -> Unit,
    vm          : ReaderViewModel
) {
    val context    = LocalContext.current
    val listState  = rememberLazyListState()
    val density    = LocalDensity.current

    // Notify current page on scroll
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onPageChange(listState.firstVisibleItemIndex)
    }

    // Jump to page from vm
    val targetPage by vm.targetPage.collectAsState()
    LaunchedEffect(targetPage) {
        if (targetPage >= 0 && targetPage < pageCount) {
            listState.animateScrollToItem(targetPage)
            vm.clearTargetPage()
        }
    }

    LazyColumn(
        state           = listState,
        contentPadding  = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier        = Modifier.fillMaxSize()
    ) {
        items(pageCount, key = { it }) { pageIndex ->
            PageItem(
                uri       = uri,
                pageIndex = pageIndex,
                nightMode = nightMode,
                onClick   = onPageClick,
                vm        = vm
            )
        }
    }
}

// ── Single page item ──────────────────────────────────────────────────────────

@Composable
private fun PageItem(
    uri       : Uri,
    pageIndex : Int,
    nightMode : Boolean,
    onClick   : () -> Unit,
    vm        : ReaderViewModel
) {
    val context = LocalContext.current
    val bitmap  by produceState<android.graphics.Bitmap?>(null, uri, pageIndex, nightMode) {
        value = vm.getPage(context, uri, pageIndex, nightMode)
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f; offsetY = 0f
                    }
                }
            }
            .clickable { onClick(); scale = 1f; offsetX = 0f; offsetY = 0f }
    ) {
        if (bitmap != null) {
            Image(
                bitmap      = bitmap!!.asImageBitmap(),
                contentDescription = "Página ${pageIndex + 1}",
                contentScale     = ContentScale.FillWidth,
                modifier    = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .graphicsLayer {
                        scaleX        = scale
                        scaleY        = scale
                        translationX  = offsetX
                        translationY  = offsetY
                    }
            )
        } else {
            // Placeholder while loading
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(if (nightMode) Color(0xFF1A1A1A) else Color(0xFFEEEEEE)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
