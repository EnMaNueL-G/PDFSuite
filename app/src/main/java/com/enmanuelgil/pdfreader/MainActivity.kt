package com.enmanuelgil.pdfreader

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.enmanuelgil.pdfreader.ui.screens.HomeScreen
import com.enmanuelgil.pdfreader.ui.screens.ReaderScreen
import com.enmanuelgil.pdfreader.ui.screens.ToolsScreen
import com.enmanuelgil.pdfreader.ui.theme.PDFReaderTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // URI passed from external "Open with..." intent
        val intentUri: Uri? = intent?.data

        setContent {
            PDFReaderTheme {
                PDFReaderApp(initialUri = intentUri)
            }
        }
    }
}

// ── Navigation destinations ───────────────────────────────────────────────────

sealed class Dest(val route: String) {
    object Home   : Dest("home")
    object Tools  : Dest("tools")
    object Reader : Dest("reader/{uri}") {
        fun route(uri: String) = "reader/$uri"
    }
}

data class BottomTab(val dest: Dest, val label: String, val icon: ImageVector)

val BOTTOM_TABS = listOf(
    BottomTab(Dest.Home,  "Inicio",       Icons.Default.Home),
    BottomTab(Dest.Tools, "Herramientas", Icons.Default.Build),
)

// ── App root ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PDFReaderApp(initialUri: Uri? = null) {
    val navController = rememberNavController()
    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentRoute  = navBackStack?.destination?.route

    val showBottomBar = currentRoute in listOf(Dest.Home.route, Dest.Tools.route)

    // If app was opened with a PDF URI, navigate to reader immediately
    LaunchedEffect(initialUri) {
        initialUri?.let { uri ->
            val encoded = Uri.encode(uri.toString())
            navController.navigate(Dest.Reader.route(encoded))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    BOTTOM_TABS.forEach { tab ->
                        NavigationBarItem(
                            selected  = currentRoute == tab.dest.route,
                            onClick   = {
                                if (currentRoute != tab.dest.route) {
                                    navController.navigate(tab.dest.route) {
                                        popUpTo(Dest.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                }
                            },
                            icon      = { Icon(tab.icon, tab.label) },
                            label     = { Text(tab.label) },
                            colors    = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(0.2f),
                                selectedIconColor   = MaterialTheme.colorScheme.primary,
                                selectedTextColor   = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController    = navController,
            startDestination = Dest.Home.route,
            modifier         = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(
                route     = Dest.Home.route,
                enterTransition = { fadeIn() },
                exitTransition  = { fadeOut() }
            ) {
                HomeScreen(
                    onOpenPdf = { uri ->
                        val encoded = Uri.encode(uri.toString())
                        navController.navigate(Dest.Reader.route(encoded))
                    }
                )
            }

            composable(
                route     = Dest.Tools.route,
                enterTransition = { fadeIn() },
                exitTransition  = { fadeOut() }
            ) {
                ToolsScreen()
            }

            composable(
                route     = Dest.Reader.route,
                enterTransition = { slideInVertically { it } + fadeIn() },
                exitTransition  = { slideOutVertically { it } + fadeOut() }
            ) { backStack ->
                val encodedUri = backStack.arguments?.getString("uri") ?: ""
                val uri = Uri.parse(Uri.decode(encodedUri))
                ReaderScreen(
                    uri    = uri,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
