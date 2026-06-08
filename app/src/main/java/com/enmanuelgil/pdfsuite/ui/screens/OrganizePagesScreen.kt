package com.enmanuelgil.pdfsuite.ui.screens

import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.ToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizePagesScreen(
    uri       : Uri,
    pageCount : Int,
    vm        : ToolsViewModel,
    onDismiss : () -> Unit
) {
    val context = LocalContext.current

    // Page list: 1-based page numbers in current display order
    val pages    = remember { mutableStateListOf<Int>().also { list ->
        for (i in 1..pageCount.coerceAtLeast(1)) list.add(i)
    }}
    // Use plain var + Set copy to avoid mutableStateSetOf API issues
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var tab      by remember { mutableStateOf(0) }  // 0=Eliminar, 1=Reordenar

    val canApply = when (tab) {
        0 -> selected.isNotEmpty() && selected.size < pages.size
        1 -> pages.toList() != (1..pageCount).toList()
        else -> false
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Cerrar")
                        }
                        Text(
                            "Organizar páginas",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 18.sp,
                            modifier   = Modifier.weight(1f).padding(start = 4.dp)
                        )
                        Button(
                            onClick  = {
                                when (tab) {
                                    0 -> {
                                        vm.deletePages(context, uri, selected)
                                        onDismiss()
                                    }
                                    1 -> {
                                        vm.reorderPages(context, uri, pages.toList())
                                        onDismiss()
                                    }
                                }
                            },
                            enabled  = canApply,
                            colors   = ButtonDefaults.buttonColors(containerColor = PdfRed),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                if (tab == 0) Icons.Default.DeleteForever else Icons.Default.Save,
                                null, Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (tab == 0) "Eliminar" else "Guardar")
                        }
                    }
                    TabRow(
                        selectedTabIndex = tab,
                        containerColor   = MaterialTheme.colorScheme.surface
                    ) {
                        Tab(selected = tab == 0, onClick = { tab = 0 },
                            text = { Text("Eliminar") },
                            icon = { Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp)) })
                        Tab(selected = tab == 1, onClick = { tab = 1 },
                            text = { Text("Reordenar") },
                            icon = { Icon(Icons.Default.SwapVert, null, Modifier.size(18.dp)) })
                    }
                }
            }

            // ── Page count info ───────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("$pageCount páginas en total",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tab == 0 && selected.isNotEmpty()) {
                    Text("${selected.size} seleccionada(s)",
                        fontSize = 13.sp, color = PdfRed, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── List ──────────────────────────────────────────────────────────
            if (tab == 0) {
                // Delete mode: checkboxes
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pages.size) { idx ->
                        val pageNum = pages[idx]
                        val isSelected = selected.contains(pageNum)
                        PageDeleteItem(
                            pageNum    = pageNum,
                            isSelected = isSelected,
                            onToggle   = {
                                selected = if (isSelected) selected - pageNum
                                           else selected + pageNum
                            }
                        )
                    }
                }
            } else {
                // Reorder mode: up/down arrows
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(pages) { idx, pageNum ->
                        PageReorderItem(
                            pageNum    = pageNum,
                            index      = idx,
                            total      = pages.size,
                            onMoveUp   = {
                                if (idx > 0) {
                                    val tmp = pages[idx - 1]
                                    pages[idx - 1] = pages[idx]
                                    pages[idx] = tmp
                                }
                            },
                            onMoveDown = {
                                if (idx < pages.size - 1) {
                                    val tmp = pages[idx + 1]
                                    pages[idx + 1] = pages[idx]
                                    pages[idx] = tmp
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDeleteItem(
    pageNum   : Int,
    isSelected: Boolean,
    onToggle  : () -> Unit
) {
    Surface(
        onClick      = onToggle,
        shape        = RoundedCornerShape(12.dp),
        color        = if (isSelected) PdfRed.copy(alpha = 0.1f)
                       else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 0.dp else 1.dp,
        shadowElevation = 1.dp,
        modifier     = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Checkbox(
                checked         = isSelected,
                onCheckedChange = { onToggle() },
                colors          = CheckboxDefaults.colors(checkedColor = PdfRed)
            )
            Icon(
                Icons.Default.PictureAsPdf, null,
                Modifier.size(24.dp),
                tint = if (isSelected) PdfRed else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Página $pageNum",
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (isSelected) PdfRed else MaterialTheme.colorScheme.onSurface,
                fontSize   = 15.sp
            )
            if (isSelected) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp), tint = PdfRed)
            }
        }
    }
}

@Composable
private fun PageReorderItem(
    pageNum   : Int,
    index     : Int,
    total     : Int,
    onMoveUp  : () -> Unit,
    onMoveDown: () -> Unit
) {
    Surface(
        shape           = RoundedCornerShape(12.dp),
        color           = MaterialTheme.colorScheme.surface,
        tonalElevation  = 1.dp,
        shadowElevation = 1.dp,
        modifier        = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle, null,
                Modifier.size(20.dp).padding(end = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(Icons.Default.PictureAsPdf, null,
                Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Página $pageNum", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                if (index != pageNum - 1) {
                    Text("(era pág. $pageNum → posición ${index + 1})",
                        fontSize = 11.sp, color = PdfRed)
                }
            }
            // Up/down controls
            Column {
                IconButton(
                    onClick  = onMoveUp,
                    enabled  = index > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, "Subir",
                        Modifier.size(20.dp),
                        tint = if (index > 0) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
                IconButton(
                    onClick  = onMoveDown,
                    enabled  = index < total - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Bajar",
                        Modifier.size(20.dp),
                        tint = if (index < total - 1) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}
