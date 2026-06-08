package com.enmanuelgil.pdfsuite.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.pdfsuite.ui.viewmodel.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    uri    : Uri,
    onBack : () -> Unit,
    vm     : ReaderViewModel = viewModel()
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

    LaunchedEffect(uri) { vm.open(context, uri) }

    val bgColor = if (nightMode) Color(0xFF121212) else Color(0xFFF5F5F5)

    Box(Modifier.fillMaxSize().background(bgColor)) {
        when {
            isLoading -> CircularProgressIndicator(
                color = Color(0xFFD32F2F),
                modifier = Modifier.align(Alignment.Center)
            )
            pageCount == 0 -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp))
                Text("No se pudo abrir el PDF", textAlign = TextAlign.Center,
                    color = if (nightMode) Color.White else Color(0xFF212121))
                TextButton(onClick = onBack) { Text("Volver") }
            }
            else -> PageList(uri, pageCount, nightMode,
                onPageClick  = { vm.toggleBars() },
                onPageChange = { vm.setPage(it) },
                vm           = vm)
        }

        // Top bar
        AnimatedVisibility(visible = showBars,
            enter = slideInVertically { -it }, exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)) {
            Surface(
                color    = if (nightMode) Color(0xF0121212) else Color.White,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null,
                                tint = if (nightMode) Color.White else Color(0xFF212121))
                        }
                        Text(fileName, Modifier.weight(1f).padding(horizontal = 4.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (nightMode) Color.White else Color(0xFF212121),
                            style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { vm.toggleSearch() }) {
                            Icon(Icons.Default.Search, null,
                                tint = if (showSearch) Color(0xFFD32F2F)
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
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = if (searchQuery.isNotEmpty()) {{
                                IconButton(onClick = { vm.setSearch(context, "") }) {
                                    Icon(Icons.Default.Clear, null)
                                }
                            }} else null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(24.dp)
                        )
                    }
                }
            }
        }

        // Bottom bar
        AnimatedVisibility(visible = showBars,
            enter = slideInVertically { it }, exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)) {
            Surface(
                color = if (nightMode) Color(0xF0121212) else Color.White,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { vm.goToPage(0) }, enabled = currentPage > 0) {
                        Icon(Icons.Default.FirstPage, null, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { vm.goToPage(currentPage - 1) }, enabled = currentPage > 0) {
                        Icon(Icons.Default.NavigateBefore, null, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        "${currentPage + 1} / $pageCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (nightMode) Color.White else Color(0xFF212121),
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { vm.goToPage(currentPage + 1) },
                        enabled = currentPage < pageCount - 1) {
                        Icon(Icons.Default.NavigateNext, null, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { vm.goToPage(pageCount - 1) },
                        enabled = currentPage < pageCount - 1) {
                        Icon(Icons.Default.LastPage, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

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

    LaunchedEffect(listState.firstVisibleItemIndex) { onPageChange(listState.firstVisibleItemIndex) }

    val targetPage by vm.targetPage.collectAsState()
    LaunchedEffect(targetPage) {
        if (targetPage >= 0) { listState.animateScrollToItem(targetPage); vm.clearTargetPage() }
    }

    LazyColumn(state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()) {
        items(pageCount, key = { it }) { idx ->
            PageItem(uri, idx, nightMode, onPageClick, vm)
        }
    }
}

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
    var scale   by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        Modifier.fillMaxWidth().wrapContentHeight()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    if (scale > 1f) { offsetX += pan.x; offsetY += pan.y }
                    else            { offsetX = 0f; offsetY = 0f }
                }
            }
            .clickable { onClick(); scale = 1f; offsetX = 0f; offsetY = 0f }
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Página ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY })
        } else {
            Box(
                Modifier.fillMaxWidth().height(300.dp)
                    .background(if (nightMode) Color(0xFF1A1A1A) else Color(0xFFEEEEEE)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFD32F2F), modifier = Modifier.size(32.dp))
            }
        }
    }
}
