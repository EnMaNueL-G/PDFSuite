package com.enmanuelgil.pdfsuite

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.enmanuelgil.pdfsuite.ui.screens.*
import com.enmanuelgil.pdfsuite.ui.theme.PDFSuiteTheme
import java.net.URLDecoder
import java.net.URLEncoder

data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("home",  "Inicio",        Icons.Default.Home),
    NavItem("tools", "Herramientas",  Icons.Default.Build),
    NavItem("about", "Acerca de",     Icons.Default.Info),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        val initialUri: Uri? = intent?.data

        setContent {
            PDFSuiteTheme {
                PDFSuiteApp(initialUri)
            }
        }
    }
}

@Composable
fun PDFSuiteApp(initialUri: Uri? = null) {
    val navController = rememberNavController()
    var readerUri     by remember { mutableStateOf<Uri?>(initialUri) }

    if (readerUri != null) {
        val uri = readerUri!!
        AnimatedVisibility(
            visible = true,
            enter   = slideInVertically { it },
            exit    = slideOutVertically { it }
        ) {
            ReaderScreen(uri = uri, onBack = { readerUri = null })
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(tonalElevation = 0.dp) {
                    val backStack by navController.currentBackStackEntryAsState()
                    val current   = backStack?.destination?.route
                    NAV_ITEMS.forEach { item ->
                        NavigationBarItem(
                            selected  = current == item.route,
                            onClick   = {
                                navController.navigate(item.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon  = { Icon(item.icon, null) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController    = navController,
                startDestination = "home",
                modifier         = Modifier.padding(padding)
            ) {
                composable("home") {
                    HomeScreen(onOpenPdf = { uri -> readerUri = uri })
                }
                composable("tools") {
                    ToolsScreen()
                }
                composable("about") {
                    AboutScreen()
                }
            }
        }
    }
}
