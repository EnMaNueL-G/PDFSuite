package com.enmanuelgil.pdfsuite.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed

@Composable
fun AboutScreen() {
    val context = LocalContext.current

    fun openUrl(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {}
    }

    fun copyToClipboard(text: String, label: String = "Copiado") {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header gradient ──
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(PdfRed, Color(0xFFB71C1C))))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // App icon
                Box(
                    Modifier.size(80.dp).clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("PDF", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
                Text("PDFSuite", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                Text(
                    "v${
                        try { context.packageManager
                            .getPackageInfo(context.packageName, 0).versionName }
                        catch (e: PackageManager.NameNotFoundException) { "?" }
                    }",
                    color = Color.White.copy(0.75f), fontSize = 13.sp
                )
                Text("Parte del ecosistema OptiSuite", color = Color.White.copy(0.8f), fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Feature pills ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            for (tag in listOf("Sin anuncios", "Sin trackers", "Código abierto")) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PdfRed.copy(0.1f)
                ) {
                    Text(tag, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 11.sp, color = PdfRed, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── About card ──
        AboutCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AboutRow(Icons.Default.Person, "Desarrollador", "Enmanuel Gil")
                Divider()
                AboutRow(Icons.Default.Language, "Sitio web", "optisuite.app",
                    onClick = { openUrl("https://optisuite.app") })
                Divider()
                AboutRow(Icons.Default.Code, "Código fuente", "github.com/EnMaNueL-G/PDFSuite",
                    onClick = { openUrl("https://github.com/EnMaNueL-G/PDFSuite") })
                Divider()
                AboutRow(Icons.Default.Description, "Licencia", "MIT — libre y auditable")
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Funciones card ──
        AboutCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Herramientas incluidas", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                for (f in listOf("📖 Lector con zoom/pan y modo nocturno",
                    "🔀 Combinar múltiples PDFs",
                    "✂️ Dividir y extraer páginas",
                    "🗜️ Comprimir — reduce el peso",
                    "🔒 Proteger con contraseña",
                    "🔓 Desbloquear PDF cifrado",
                    "🔄 Rotar páginas",
                    "⭐ Favoritos y historial local")) {
                    Text(f, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Donations card ──
        AboutCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Favorite, null, tint = PdfRed, modifier = Modifier.size(20.dp))
                    Text("Apoya el proyecto", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Text("PDFSuite es y siempre será gratuito. Si te resulta útil, considera una donación para mantener el desarrollo activo.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp)

                // Binance Pay
                var copied1 by remember { mutableStateOf(false) }
                DonationRow(
                    label = "Binance Pay ID",
                    value = "1165745950",
                    copied = copied1,
                    onCopy = { copyToClipboard("1165745950", "Binance ID"); copied1 = true }
                )

                // BSC wallet
                var copied2 by remember { mutableStateOf(false) }
                DonationRow(
                    label = "BSC / BEP20",
                    value = "0xb6f6731a4ea87f8e1fd6f44f48b5bc4204571f08",
                    short = "0x0a9a...b3c1",
                    copied = copied2,
                    onCopy = { copyToClipboard("0xb6f6731a4ea87f8e1fd6f44f48b5bc4204571f08", "BSC Wallet"); copied2 = true }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Other apps ──
        AboutCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Otras apps OptiSuite", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                OtherAppRow("🔒 SecuritySuite", "Seguridad y privacidad para Windows",
                    onClick = { openUrl("https://github.com/EnMaNueL-G/SecuritySuite") })
                OtherAppRow("📱 AndroidSecurity", "Auditor de permisos para Android",
                    onClick = { openUrl("https://github.com/EnMaNueL-G/AndroidSecurity") })
                OtherAppRow("🖥️ WinOptimizer", "Optimizador completo para Windows",
                    onClick = { openUrl("https://github.com/EnMaNueL-G/WinOptimizer") })
                OutlinedButton(
                    onClick = { openUrl("https://optisuite.app") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PdfRed),
                    border = BorderStroke(1.dp, PdfRed.copy(0.5f))
                ) {
                    Icon(Icons.Default.Language, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ver todas en optisuite.app")
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("© 2026 Enmanuel Gil · optisuite.app", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
            textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 24.dp))
    }
}

// ── Reusable components ───────────────────────────────────────────────────────

@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun AboutRow(
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    label  : String,
    value  : String,
    onClick: (() -> Unit)? = null
) {
    val modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = PdfRed.copy(0.8f), modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 13.sp, color = if (onClick != null) PdfRed
                else MaterialTheme.colorScheme.onSurface)
        }
        if (onClick != null) {
            Icon(Icons.Default.OpenInNew, null,
                tint = PdfRed.copy(0.5f), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun DonationRow(
    label  : String,
    value  : String,
    short  : String = value,
    copied : Boolean,
    onCopy : () -> Unit
) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(short, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null,
                    tint = if (copied) Color(0xFF43A047) else PdfRed,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun OtherAppRow(name: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
            modifier = Modifier.size(18.dp))
    }
}
