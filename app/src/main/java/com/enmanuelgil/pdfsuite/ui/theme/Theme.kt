package com.enmanuelgil.pdfsuite.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand colors ──────────────────────────────────────────────────────────────
val PdfRed       = Color(0xFFD32F2F)    // PDF red — primary
val PdfRedDark   = Color(0xFFB71C1C)
val PdfRedLight  = Color(0xFFEF9A9A)
val AccentBlue   = Color(0xFF1565C0)    // secondary accent
val SurfaceLight = Color(0xFFFFFFFF)
val BgLight      = Color(0xFFF5F5F5)
val SurfaceVar   = Color(0xFFFAFAFA)
val OnSurface    = Color(0xFF212121)
val OnSurface2   = Color(0xFF757575)
val DividerColor = Color(0xFFE0E0E0)

// Dark reader colors
val DarkBg      = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkOnSurf  = Color(0xFFEEEEEE)

// ── Light color scheme (default) ──────────────────────────────────────────────
private val LightColors = lightColorScheme(
    primary            = PdfRed,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFFFEBEE),
    onPrimaryContainer = PdfRedDark,
    secondary          = AccentBlue,
    onSecondary        = Color.White,
    background         = BgLight,
    onBackground       = OnSurface,
    surface            = SurfaceLight,
    onSurface          = OnSurface,
    surfaceVariant     = SurfaceVar,
    onSurfaceVariant   = OnSurface2,
    outline            = DividerColor,
    error              = Color(0xFFB00020),
)

// ── Dark color scheme (for reader) ────────────────────────────────────────────
private val DarkColors = darkColorScheme(
    primary            = PdfRedLight,
    onPrimary          = Color(0xFF690005),
    background         = DarkBg,
    onBackground       = DarkOnSurf,
    surface            = DarkSurface,
    onSurface          = DarkOnSurf,
    surfaceVariant     = Color(0xFF2C2C2C),
    onSurfaceVariant   = Color(0xFFBBBBBB),
)

@Composable
fun PDFSuiteTheme(
    darkTheme: Boolean = false,
    content  : @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography  = Typography(),
        content     = content
    )
}
