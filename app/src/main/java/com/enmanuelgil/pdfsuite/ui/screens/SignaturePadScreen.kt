package com.enmanuelgil.pdfsuite.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmanuelgil.pdfsuite.ui.theme.PdfRed

data class SignatureLine(val points: List<Offset>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturePadScreen(
    onSignatureReady : (Bitmap) -> Unit,
    onDismiss        : () -> Unit
) {
    val context = LocalContext.current
    val lines   = remember { mutableStateListOf<SignatureLine>() }
    val current = remember { mutableStateListOf<Offset>() }
    var inkColor by remember { mutableStateOf(Color.Black) }

    val colorOptions = listOf(
        Color.Black         to "Negro",
        Color(0xFF1565C0)   to "Azul",
        Color(0xFF388E3C)   to "Verde",
        PdfRed              to "Rojo"
    )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────────
            Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Cerrar")
                    }
                    Text(
                        "Firma digital",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        modifier   = Modifier.weight(1f).padding(start = 4.dp)
                    )
                    TextButton(onClick = {
                        lines.clear()
                        current.clear()
                    }) {
                        Text("Borrar", color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            if (lines.isNotEmpty() || current.isNotEmpty()) {
                                onSignatureReady(renderSignature(lines, current, inkColor))
                            }
                        },
                        colors  = ButtonDefaults.buttonColors(containerColor = PdfRed),
                        modifier= Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Usar")
                    }
                }
            }

            // ── Instructions ──────────────────────────────────────────────────
            Text(
                "Dibuja tu firma en el área blanca de abajo",
                fontSize = 13.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ── Ink color selector ────────────────────────────────────────────
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Color:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                colorOptions.forEach { (color, _) ->
                    val selected = color == inkColor
                    Box(
                        Modifier
                            .size(if (selected) 32.dp else 26.dp)
                            .background(color, RoundedCornerShape(50))
                            .let {
                                if (selected) it.background(
                                    Color.Transparent,
                                    RoundedCornerShape(50)
                                ) else it
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = { inkColor = color },
                            shape   = RoundedCornerShape(50),
                            color   = color,
                            modifier= Modifier.size(if (selected) 32.dp else 26.dp),
                            border  = if (selected) androidx.compose.foundation.BorderStroke(
                                2.dp, MaterialTheme.colorScheme.outline
                            ) else null
                        ) {}
                    }
                }
            }

            // ── Drawing canvas ────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // White background card
                Surface(
                    Modifier.fillMaxSize(),
                    shape          = RoundedCornerShape(16.dp),
                    color          = Color.White,
                    shadowElevation= 4.dp,
                    border         = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant
                    )
                ) {}

                // Dashed baseline hint
                Canvas(Modifier.fillMaxSize().padding(16.dp)) {
                    val y = size.height * 0.7f
                    val dashLen = 10.dp.toPx()
                    val gap     = 6.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color     = Color(0xFFBDBDBD),
                            start     = Offset(x, y),
                            end       = Offset((x + dashLen).coerceAtMost(size.width), y),
                            strokeWidth = 1.dp.toPx()
                        )
                        x += dashLen + gap
                    }
                    drawLine(Color(0xFFEEEEEE), Offset(0f, size.height * 0.85f),
                        Offset(size.width, size.height * 0.85f), 0.5.dp.toPx())
                }

                // Touch-driven drawing
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> current.add(offset) },
                                onDrag      = { change, _ ->
                                    change.consume()
                                    current.add(change.position)
                                },
                                onDragEnd = {
                                    lines.add(SignatureLine(current.toList()))
                                    current.clear()
                                }
                            )
                        }
                ) {
                    val stroke = Stroke(
                        width = 3.dp.toPx(),
                        cap   = StrokeCap.Round,
                        join  = StrokeJoin.Round
                    )
                    // Draw committed lines
                    for (line in lines) {
                        if (line.points.size < 2) continue
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(line.points[0].x, line.points[0].y)
                        for (pt in line.points.drop(1))
                            path.lineTo(pt.x, pt.y)
                        drawPath(path, inkColor, style = stroke)
                    }
                    // Draw current stroke
                    if (current.size >= 2) {
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(current[0].x, current[0].y)
                        for (pt in current.drop(1)) path.lineTo(pt.x, pt.y)
                        drawPath(path, inkColor, style = stroke)
                    }
                }

                // Empty hint
                if (lines.isEmpty() && current.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "✍ Desliza el dedo para firmar",
                            color    = Color(0xFFBDBDBD),
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // ── Bottom info ───────────────────────────────────────────────────
            Text(
                "La firma se añadirá como imagen en la página que selecciones",
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
        }
    }
}

/** Renders all drawn strokes to a transparent-background Bitmap */
private fun renderSignature(
    lines  : List<SignatureLine>,
    current: List<Offset>,
    color  : Color
): Bitmap {
    val W = 800; val H = 300
    val bmp    = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        style       = Paint.Style.STROKE
        strokeCap   = android.graphics.Paint.Cap.ROUND
        strokeJoin  = android.graphics.Paint.Join.ROUND
        this.color  = color.toArgb()
    }

    // Scale the drawn strokes to the bitmap size
    // (assumes drawing area was roughly W_draw x H_draw in screen pixels)
    // We can't know exact size here, so we normalize across bounding box
    val all = (lines.flatMap { it.points } + current)
    if (all.isEmpty()) return bmp

    val minX = all.minOf { it.x }
    val minY = all.minOf { it.y }
    val maxX = all.maxOf { it.x }
    val maxY = all.maxOf { it.y }
    val rangeX = (maxX - minX).coerceAtLeast(1f)
    val rangeY = (maxY - minY).coerceAtLeast(1f)
    val margin = 30f
    val scaleX = (W - margin * 2) / rangeX
    val scaleY = (H - margin * 2) / rangeY
    val scale  = minOf(scaleX, scaleY)

    fun mapX(x: Float) = margin + (x - minX) * scale
    fun mapY(y: Float) = margin + (y - minY) * scale

    fun drawLine(pts: List<Offset>) {
        if (pts.size < 2) return
        val path = Path()
        path.moveTo(mapX(pts[0].x), mapY(pts[0].y))
        for (pt in pts.drop(1)) path.lineTo(mapX(pt.x), mapY(pt.y))
        canvas.drawPath(path, paint)
    }

    for (line in lines) drawLine(line.points)
    drawLine(current)

    return bmp
}
