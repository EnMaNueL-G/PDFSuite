package com.enmanuelgil.pdfsuite.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── OptiSuite brand colors — deep teal, not the usual PDF red/blue ────────────
val BrandTeal       = Color(0xFF006D77)   // main brand
val BrandTealDark   = Color(0xFF00474F)   // dark variant
val BrandTealLight  = Color(0xFFB2DFDB)   // light / container
val BrandTealOnCont = Color(0xFF00363D)

// Keep PdfRed as alias so every file that references it uses the new brand color
val PdfRed       = BrandTeal
val PdfRedDark   = BrandTealDark
val PdfRedLight  = BrandTealLight

val AccentAmber  = Color(0xFFFF8F00)      // highlight / secondary accent
val SurfaceLight = Color(0xFFFFFFFF)
val BgLight      = Color(0xFFF4F6F5)      // slightly warmer than pure grey
val SurfaceVar   = Color(0xFFF8FAFA)
val OnSurface    = Color(0xFF1A2B2C)      // very dark teal-black
val OnSurface2   = Color(0xFF5E7273)
val DividerColor = Color(0xFFDDE5E5)

// Dark reader colors
val DarkBg      = Color(0xFF0D1A1B)
val DarkSurface = Color(0xFF162122)
val DarkOnSurf  = Color(0xFFDFECED)

// ── Light color scheme ────────────────────────────────────────────────────────
private val LightColors = lightColorScheme(
    primary            = BrandTeal,
    onPrimary          = Color.White,
    primaryContainer   = BrandTealLight,
    onPrimaryContainer = BrandTealOnCont,
    secondary          = AccentAmber,
    onSecondary        = Color.White,
    background         = BgLight,
    onBackground       = OnSurface,
    surface            = SurfaceLight,
    onSurface          = OnSurface,
    surfaceVariant     = SurfaceVar,
    onSurfaceVariant   = OnSurface2,
    outline            = DividerColor,
    error              = Color(0xFFC62828),
)

// ── Dark color scheme (for reader night mode) ─────────────────────────────────
private val DarkColors = darkColorScheme(
    primary            = BrandTealLight,
    onPrimary          = BrandTealDark,
    background         = DarkBg,
    onBackground       = DarkOnSurf,
    surface            = DarkSurface,
    onSurface          = DarkOnSurf,
    surfaceVariant     = Color(0xFF1E2E2F),
    onSurfaceVariant   = Color(0xFFB0C8CA),
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

// Alias so call sites don't need updating
@Composable
fun OptiSuiteTheme(
    darkTheme: Boolean = false,
    content  : @Composable () -> Unit
) = PDFSuiteTheme(darkTheme, content)
