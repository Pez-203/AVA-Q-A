package com.project.ava

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

@Composable
fun AROverlayScreen(
    categoryTitle: String,
    onAnimationFinished: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── Estado de las fases de la animación ──────────────────────────────────
    // Fase 0: marco escaneando (pulse)
    // Fase 1: AVA emerge (scale + fade)
    // Fase 2: texto flotante aparece
    // Fase 3: transición al chat
    var phase by remember { mutableStateOf(0) }

    // Dispara las fases en secuencia
    LaunchedEffect(Unit) {
        delay(800)   // marco pulsando
        phase = 1    // AVA emerge
        delay(900)
        phase = 2    // texto aparece
        delay(1400)
        phase = 3    // salida
        delay(400)
        onAnimationFinished()
    }

    // ── Animaciones ──────────────────────────────────────────────────────────

    // Pulso del marco QR (fase 0)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val pulseStroke by infiniteTransition.animateFloat(
        initialValue = 2f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseStroke"
    )

    // AVA: escala de 0 → 1 cuando phase >= 1
    val avaScale by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "avaScale"
    )
    val avaAlpha by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(500),
        label = "avaAlpha"
    )

    // Texto: fade in cuando phase >= 2
    val textAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(600),
        label = "textAlpha"
    )
    val textOffsetY by animateFloatAsState(
        targetValue = if (phase >= 2) 0f else 30f,
        animationSpec = tween(600, easing = EaseOut),
        label = "textOffsetY"
    )

    // Fade out general en fase 3
    val screenAlpha by animateFloatAsState(
        targetValue = if (phase >= 3) 0f else 1f,
        animationSpec = tween(400),
        label = "screenAlpha"
    )

    // ── UI ───────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha)
    ) {
        // CAPA 1 — Cámara en vivo de fondo
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    try {
                        val provider = future.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview
                        )
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // CAPA 2 — Velo oscuro semitransparente
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1B2A).copy(alpha = 0.55f))
        )

        // CAPA 3 — Marco QR animado en el centro
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Marco QR pulsante
            val qrFrameSize = 220.dp
            Canvas(
                modifier = Modifier.size(qrFrameSize)
            ) {
                val w = size.width
                val h = size.height
                val corner = 40.dp.toPx()
                val stroke = pulseStroke.dp.toPx()
                val green = Color(0xFF2ECC71).copy(alpha = pulseAlpha)

                // Esquinas del marco QR
                // Top-left
                drawLine(green, Offset(0f, 0f), Offset(corner, 0f), stroke)
                drawLine(green, Offset(0f, 0f), Offset(0f, corner), stroke)
                // Top-right
                drawLine(green, Offset(w, 0f), Offset(w - corner, 0f), stroke)
                drawLine(green, Offset(w, 0f), Offset(w, corner), stroke)
                // Bottom-left
                drawLine(green, Offset(0f, h), Offset(corner, h), stroke)
                drawLine(green, Offset(0f, h), Offset(0f, h - corner), stroke)
                // Bottom-right
                drawLine(green, Offset(w, h), Offset(w - corner, h), stroke)
                drawLine(green, Offset(w, h), Offset(w, h - corner), stroke)

                // Borde interior punteado
                drawRoundRect(
                    color = Color(0xFF2ECC71).copy(alpha = pulseAlpha * 0.3f),
                    topLeft = Offset(12.dp.toPx(), 12.dp.toPx()),
                    size = Size(w - 24.dp.toPx(), h - 24.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(
                        width = 1.5f.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 6.dp.toPx())
                        )
                    )
                )

                // Línea de escaneo horizontal (solo en fase 0)
                if (phase == 0) {
                    drawLine(
                        color = Color(0xFF2ECC71).copy(alpha = pulseAlpha * 0.8f),
                        start = Offset(16.dp.toPx(), h / 2),
                        end = Offset(w - 16.dp.toPx(), h / 2),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            // CAPA 4 — AVA emerge desde el centro del marco
            Image(
                painter = painterResource(id = R.drawable.ava_think),
                contentDescription = "AVA AR",
                modifier = Modifier
                    .size(260.dp)
                    .offset(y = (-30).dp)   // ligeramente hacia arriba para que "salga" del marco
                    .scale(avaScale)
                    .alpha(avaAlpha),
                contentScale = ContentScale.Fit
            )
        }

        // CAPA 5 — Texto informativo flotante (aparece en fase 2)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .alpha(textAlpha)
                .offset(y = textOffsetY.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Chip con el nombre de la categoría
            Surface(
                color = Color(0xFF2E7D32),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = "✓  QR Reconocido",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Categoría detectada
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = categoryTitle,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }

            // Mensaje de AVA
            Surface(
                color = Color(0xFF1E2A38).copy(alpha = 0.80f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Cargando tus preguntas...",
                    color = Color(0xFFB2DFDB),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }

        // CAPA 6 — Partículas decorativas (puntos verdes flotantes)
        ARParticles(
            modifier = Modifier.fillMaxSize(),
            visible = phase >= 1
        )
    }
}

// ── Partículas decorativas ────────────────────────────────────────────────────
@Composable
private fun ARParticles(modifier: Modifier, visible: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800),
        label = "particlesAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    // 3 partículas con offsets y fases distintas
    val p1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "p1"
    )
    val p2 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "p2"
    )
    val p3 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "p3"
    )

    Canvas(modifier = modifier.alpha(alpha)) {
        val w = size.width
        val h = size.height
        val green = Color(0xFF2ECC71)

        // Partícula 1 — lado izquierdo flotando hacia arriba
        val y1 = h * 0.75f - (p1 % 1f) * h * 0.4f
        drawCircle(green.copy(alpha = 0.6f), radius = 4.dp.toPx(), center = Offset(w * 0.15f, y1))
        drawCircle(green.copy(alpha = 0.2f), radius = 8.dp.toPx(), center = Offset(w * 0.15f, y1))

        // Partícula 2 — lado derecho
        val y2 = h * 0.80f - (p2 % 1f) * h * 0.45f
        drawCircle(green.copy(alpha = 0.5f), radius = 3.dp.toPx(), center = Offset(w * 0.82f, y2))
        drawCircle(green.copy(alpha = 0.15f), radius = 7.dp.toPx(), center = Offset(w * 0.82f, y2))

        // Partícula 3 — centro-derecha
        val y3 = h * 0.70f - (p3 % 1f) * h * 0.35f
        drawCircle(green.copy(alpha = 0.4f), radius = 5.dp.toPx(), center = Offset(w * 0.65f, y3))
        drawCircle(green.copy(alpha = 0.12f), radius = 10.dp.toPx(), center = Offset(w * 0.65f, y3))
    }
}

@ComposePreview(showBackground = true)
@Composable
fun AROverlayScreenPreview() {
    AROverlayScreen(categoryTitle = "Reinscripción", onAnimationFinished = {})
}
