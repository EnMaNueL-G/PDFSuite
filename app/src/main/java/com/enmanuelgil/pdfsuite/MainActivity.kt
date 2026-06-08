package com.enmanuelgil.pdfsuite

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.enmanuelgil.pdfsuite.ui.screens.*
import com.enmanuelgil.pdfsuite.ui.theme.PDFSuiteTheme
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        val initialUri: Uri? = intent?.data
        setContent { PDFSuiteTheme { PDFSuiteApp(initialUri) } }
    }
}

@Composable
fun PDFSuiteApp(initialUri: Uri? = null) {
    val context       = LocalContext.current
    val navController = rememberNavController()
    val homeVm        : HomeViewModel = viewModel()
    var readerUri     by remember { mutableStateOf<Uri?>(initialUri) }

    // Global file picker (FAB center button) — PDF + common office formats
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            homeVm.addRecent(context, it)
            readerUri = it
        }
    }
    val supportedMimeTypes = arrayOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )

    // ── Full-screen reader ────────────────────────────────────────────────────
    if (readerUri != null) {
        AnimatedVisibility(
            visible = true,
            enter   = slideInVertically { it },
            exit    = slideOutVertically { it }
        ) {
            ReaderScreen(uri = readerUri!!, onBack = { readerUri = null })
        }
        return
    }

    // ── Main scaffold with bottom nav ─────────────────────────────────────────
    Scaffold(
        bottomBar = {
            BottomNavBar(
                navController = navController,
                onOpenPicker  = { picker.launch(supportedMimeTypes) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        NavHost(
            navController    = navController,
            startDestination = "home",
            modifier         = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    onOpenPdf    = { uri -> readerUri = uri },
                    onOpenPicker = { picker.launch(supportedMimeTypes) },
                    homeVm       = homeVm
                )
            }
            composable("files") {
                FilesScreen(
                    onOpenPdf    = { uri -> readerUri = uri },
                    onOpenPicker = { picker.launch(supportedMimeTypes) },
                    homeVm       = homeVm
                )
            }
            composable("tools") {
                ToolsScreen(onOpenPdf = { uri -> readerUri = uri })
            }
            composable("about") {
                AboutScreen()
            }
        }
    }
}

// ── Bottom navigation bar with center FAB ────────────────────────────────────

@Composable
private fun BottomNavBar(
    navController : androidx.navigation.NavHostController,
    onOpenPicker  : () -> Unit
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current   = backStack?.destination?.route

    Surface(
        color           = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation  = 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Inicio
            NavBarBtn(
                icon     = Icons.Default.Home,
                label    = "Inicio",
                selected = current == "home",
                onClick  = { navController.navigate("home") {
                    popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true
                }}
            )
            // Archivos
            NavBarBtn(
                icon     = Icons.Default.Folder,
                label    = "Archivos",
                selected = current == "files",
                onClick  = { navController.navigate("files") {
                    popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true
                }}
            )
            // Center FAB
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(PdfRed),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onOpenPicker) {
                    Icon(Icons.Default.Add, "Abrir PDF", tint = Color.White,
                        modifier = Modifier.size(28.dp))
                }
            }
            // Herramientas
            NavBarBtn(
                icon     = Icons.Default.GridView,
                label    = "Herramientas",
                selected = current == "tools",
                onClick  = { navController.navigate("tools") {
                    popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true
                }}
            )
            // Info
            NavBarBtn(
                icon     = Icons.Default.Info,
                label    = "Info",
                selected = current == "about",
                onClick  = { navController.navigate("about") {
                    popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true
                }}
            )
        }
    }
}

@Composable
private fun NavBarBtn(
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    label    : String,
    selected : Boolean,
    onClick  : () -> Unit
) {
    val tint = if (selected) PdfRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
    Column(
        Modifier.width(64.dp),
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint, maxLines = 1)
    }
}
