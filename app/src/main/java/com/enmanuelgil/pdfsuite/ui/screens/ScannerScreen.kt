package com.enmanuelgil.pdfsuite.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enmanuelgil.pdfsuite.model.ToolResult
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed
import com.enmanuelgil.pdfsuite.ui.viewmodel.ToolsViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

@Composable
fun ScannerScreen(
    vm        : ToolsViewModel = viewModel(),
    onDismiss : () -> Unit,
    onOpenPdf : (Uri) -> Unit = {}
) {
    val context   = LocalContext.current
    val result    by vm.result.collectAsState()
    val isWorking by vm.isWorking.collectAsState()

    var scanError  by remember { mutableStateOf<String?>(null) }
    var scanDone   by remember { mutableStateOf(false) }

    // ML Kit Document Scanner launcher
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
        scanResult?.let { r ->
            // If ML Kit gives us a PDF directly, use it
            val pdf = r.pdf
            if (pdf != null) {
                // Copy the PDF into our app storage
                try {
                    val pdfUri = pdf.uri
                    val inStream = context.contentResolver.openInputStream(pdfUri)
                    val outFile  = java.io.File(context.filesDir, "escaneado_${System.currentTimeMillis()}.pdf")
                    inStream?.use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    scanDone = true
                    // Return the file URI via FileProvider — trigger open in reader
                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                        context, "com.enmanuelgil.pdfsuite.provider", outFile
                    )
                    onOpenPdf(fileUri)
                    onDismiss()
                } catch (e: Exception) {
                    scanError = "Error al guardar el PDF escaneado: ${e.message}"
                }
            } else {
                // Fallback: get pages as images and convert to PDF
                val pages = r.pages ?: emptyList()
                if (pages.isNotEmpty()) {
                    val bitmaps = pages.mapNotNull { page ->
                        try {
                            val imgUri = page.imageUri
                            val stream = context.contentResolver.openInputStream(imgUri)
                            android.graphics.BitmapFactory.decodeStream(stream)
                        } catch (_: Exception) { null }
                    }
                    if (bitmaps.isNotEmpty()) {
                        vm.imagesToPdf(context, bitmaps)
                    } else {
                        scanError = "No se pudieron procesar las páginas escaneadas"
                    }
                } else {
                    scanError = "El escáner no devolvió páginas"
                }
            }
        } ?: run {
            // User cancelled — don't show error
        }
    }

    // Launch scanner immediately when this screen opens
    var launched by remember { mutableStateOf(false) }
    if (!launched) {
        launched = true
        LaunchedEffect(Unit) {
            try {
                val options = GmsDocumentScannerOptions.Builder()
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .setGalleryImportAllowed(true)
                    .setPageLimit(20)
                    .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
                    )
                    .build()

                val scanner = GmsDocumentScanning.getClient(options)
                scanner.getStartScanIntent(context as androidx.activity.ComponentActivity)
                    .addOnSuccessListener { intentSender ->
                        scanLauncher.launch(
                            IntentSenderRequest.Builder(intentSender).build()
                        )
                    }
                    .addOnFailureListener { e ->
                        Log.e("Scanner", "Error iniciando escáner: ${e.message}", e)
                        scanError = "Error al iniciar el escáner: ${e.message}\n\n" +
                            "Asegúrate de tener Google Play Services actualizado."
                    }
            } catch (e: Exception) {
                scanError = "Error al configurar el escáner: ${e.message}"
            }
        }
    }

    // Handle result from imagesToPdf
    if (result is ToolResult.Success) {
        LaunchedEffect(result) {
            val r = result as ToolResult.Success
            onOpenPdf(r.outputUri)
            vm.clearResult()
            onDismiss()
        }
    }

    // Waiting / error UI (shown while scanner is launching or on error)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (scanError != null) {
                Icon(Icons.Default.ErrorOutline, null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(16.dp))
                Text("No se pudo iniciar el escáner",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(scanError ?: "", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = PdfRed)) {
                    Text("Volver")
                }
            } else {
                // Loading — waiting for scanner Activity to launch
                CircularProgressIndicator(color = PdfRed, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Abriendo escáner…", fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    }
}

/**
 * Alternative: camera scan without ML Kit (manual approach using CameraX)
 * Used as fallback if ML Kit Document Scanner is not available.
 */
@Composable
fun ManualScanPrompt(
    onPickImage : () -> Unit,
    onDismiss   : () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(60.dp),
                    color = PdfRed
                ) {
                    Icon(Icons.Default.DocumentScanner, null,
                        tint     = Color.White,
                        modifier = Modifier.size(120.dp).padding(28.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Escanear documento", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Toma una foto del documento o selecciona imágenes de la galería. " +
                "PDFSuite las convertirá automáticamente a PDF.",
                fontSize  = 14.sp,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick  = onPickImage,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = PdfRed)
            ) {
                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Seleccionar imágenes")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar")
            }
        }
    }
}
