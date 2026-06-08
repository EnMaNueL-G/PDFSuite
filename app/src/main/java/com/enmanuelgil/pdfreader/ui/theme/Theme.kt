package com.enmanuelgil.pdfreader.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Color palette ─────────────────────────────────────────────────────────────
val Primary         = Color(0xFF6C63FF)   // purple-blue
val PrimaryVariant  = Color(0xFF3F3A8A)
val Secondary       = Color(0xFF03DAC5)
val Background      = Color(0xFF0F0F17)
val Surface         = Color(0xFF1A1A2E)
val SurfaceVariant  = Color(0xFF22223A)
val OnPrimary       = Color.White
val OnBackground    = Color(0xFFE8E8FF)
val OnSurface       = Color(0xFFCCCCE8)
val Accent          = Color(0xFFEF5B5B)   // red for danger / highlight

private val DarkColorScheme = darkColorScheme(
    primary          = Primary,
    onPrimary        = OnPrimary,
    primaryContainer = PrimaryVariant,
    secondary        = Secondary,
    background       = Background,
    surface          = Surface,
    surfaceVariant   = SurfaceVariant,
    onBackground     = OnBackground,
    onSurface        = OnSurface,
    error            = Accent,
)

private val LightColorScheme = lightColorScheme(
    primary          = Primary,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFEAE8FF),
    secondary        = Color(0xFF00897B),
    background       = Color(0xFFF6F6FF),
    surface          = Color.White,
    surfaceVariant   = Color(0xFFF0F0F8),
    onBackground     = Color(0xFF1A1A2E),
    onSurface        = Color(0xFF333355),
    error            = Color(0xFFEF5B5B),
)

@Composable
fun PDFReaderTheme(
    darkTheme: Boolean = true, // default dark for reading comfort
    content  : @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography  = Typography(),
        content     = content
    )
}
