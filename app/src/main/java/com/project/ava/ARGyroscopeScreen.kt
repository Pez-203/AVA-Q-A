package com.project.ava

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.project.ava.data.Question
import kotlinx.coroutines.delay
import kotlin.math.*

// Offsets de tarjetas relativos al ancla del grupo (dp)
private val Q_OFFSETS = listOf(
    Offset(-87f, -200f), Offset(87f, -200f),
    Offset(-87f, -120f), Offset(87f, -120f),
    Offset(-87f,  -40f), Offset(87f,  -40f),
    Offset(-87f,   40f), Offset(87f,   40f),
    Offset(-87f,  120f), Offset(87f,  120f),
    Offset(-87f,  200f), Offset(87f,  200f),
)

// Ancla de pantalla para cada grupo QR (dp desde el centro de pantalla)
private val GROUP_ANCHORS = listOf(
    Offset(  0f,    0f),
    Offset(-200f, -260f),
    Offset( 200f, -260f),
    Offset(-200f,  260f),
    Offset( 200f,  260f),
)

private fun qOffset(idx: Int): Offset =
    if (idx < Q_OFFSETS.size) Q_OFFSETS[idx]
    else Offset(0f, 280f + (idx - Q_OFFSETS.size) * 85f)

private fun groupAnchor(gIdx: Int): Offset =
    if (gIdx < GROUP_ANCHORS.size) GROUP_ANCHORS[gIdx]
    else Offset(0f, 360f * (gIdx - GROUP_ANCHORS.size + 1))

// ── Giroscopio: parallax del mundo ───────────────────────────────────────────
@Composable
private fun rememberParallaxOffset(): State<Offset> {
    val context = LocalContext.current
    val raw     = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(Unit) {
        val sm     = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        var refPitch   = Float.NaN
        var refAzimuth = Float.NaN
        val sens = 30f
        val max  = 300f

        val listener = object : SensorEventListener {
            private val rm    = FloatArray(9)
            private val outRm = FloatArray(9)
            private val ori   = FloatArray(3)
            private var smoothAz = 0f
            private var smoothPi = 0f
            private val alpha    = 0.3f
            private var first    = true
            private var count    = 0

            override fun onSensorChanged(e: SensorEvent) {
                if (e.values.size < 3) return
                SensorManager.getRotationMatrixFromVector(rm, e.values)
                SensorManager.remapCoordinateSystem(rm, SensorManager.AXIS_X, SensorManager.AXIS_Z, outRm)
                SensorManager.getOrientation(outRm, ori)

                val az = Math.toDegrees(ori[0].toDouble()).toFloat()
                val pi = Math.toDegrees(ori[1].toDouble()).toFloat()

                if (first) { smoothAz = az; smoothPi = pi; first = false }
                else {
                    var diff = az - smoothAz
                    if (diff > 180) diff -= 360
                    if (diff < -180) diff += 360
                    smoothAz += alpha * diff
                    smoothPi += alpha * (pi - smoothPi)
                }

                if (count < 5) {
                    count++
                    if (count == 5) { refAzimuth = smoothAz; refPitch = smoothPi }
                    return
                }

                var dAz = smoothAz - refAzimuth
                if (dAz > 180) dAz -= 360
                if (dAz < -180) dAz += 360
                val dPi = smoothPi - refPitch

                val dx = (dAz  * sens).coerceIn(-max, max)
                val dy = (-dPi * sens).coerceIn(-max, max)
                raw.value = Offset(dx, dy)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST) }
        onDispose { sm.unregisterListener(listener) }
    }
    return raw
}

// ── Pantalla AR: retícula fija, tarjetas ancladas al QR ──────────────────────
@Composable
fun ARGyroscopeScreen(
    questions: List<Question>,
    onQuestionSelected: (Question) -> Unit,
    onBack: () -> Unit,
    additionalGroups: List<List<Question>> = emptyList()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val parallaxRaw    by rememberParallaxOffset()

    // wx/wy: desplazamiento del mundo en dp.
    // Al girar a la derecha (wx > 0), el mundo se desplaza a la izquierda en pantalla.
    val wx by animateFloatAsState(parallaxRaw.x, spring(stiffness = Spring.StiffnessVeryLow), label = "wx")
    val wy by animateFloatAsState(parallaxRaw.y, spring(stiffness = Spring.StiffnessVeryLow), label = "wy")

    val allGroups: List<List<Question>> = remember(questions, additionalGroups) {
        buildList {
            if (questions.isNotEmpty()) add(questions)
            addAll(additionalGroups)
        }
    }

    var hoveredGroup  by remember { mutableStateOf(-1) }
    var hoveredCard   by remember { mutableStateOf(-1) }
    var expandedGroup by remember { mutableStateOf(-1) }
    var expandedCard  by remember { mutableStateOf(-1) }
    var hoverProgress by remember { mutableStateOf(0f) }

    // Detección de hover: la retícula está fija en (0,0); las tarjetas se mueven con el mundo.
    // Una tarjeta está bajo la retícula cuando su posición en el mundo ≈ (wx, wy).
    LaunchedEffect(wx, wy, expandedGroup) {
        if (expandedGroup >= 0) { hoveredGroup = -1; hoveredCard = -1; return@LaunchedEffect }

        var bestGroup = -1; var bestCard = -1; var minDist = Float.MAX_VALUE

        allGroups.forEachIndexed { gIdx, group ->
            val anchor = groupAnchor(gIdx)
            group.indices.forEach { cIdx ->
                val off = qOffset(cIdx)
                val dx  = (anchor.x + off.x) - wx
                val dy  = (anchor.y + off.y) - wy
                val d   = sqrt(dx * dx + dy * dy)
                if (d < minDist) { minDist = d; bestGroup = gIdx; bestCard = cIdx }
            }
        }
        if (minDist < 68f) { hoveredGroup = bestGroup; hoveredCard = bestCard }
        else { hoveredGroup = -1; hoveredCard = -1 }
    }

    // Temporizador de selección 1.5s
    LaunchedEffect(hoveredGroup, hoveredCard) {
        if (hoveredGroup >= 0 && hoveredCard >= 0 && expandedGroup < 0) {
            hoverProgress = 0f
            val steps = 60; val ms = 1500L / steps
            repeat(steps) { i ->
                delay(ms)
                if (hoveredGroup < 0) return@LaunchedEffect
                hoverProgress = (i + 1f) / steps
            }
            if (hoveredGroup >= 0 && hoveredCard >= 0) {
                expandedGroup = hoveredGroup
                expandedCard  = hoveredCard
                hoveredGroup  = -1
                hoveredCard   = -1
                hoverProgress = 0f
            }
        } else {
            hoverProgress = 0f
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density    = LocalDensity.current
        val swPx       = constraints.maxWidth.toFloat()
        val shPx       = constraints.maxHeight.toFloat()
        val swDp: Dp   = with(density) { swPx.toDp() }
        val shDp: Dp   = with(density) { shPx.toDp() }
        val densityVal = density.density

        // ── CAPA 1: Cámara ────────────────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                val pv = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                ProcessCameraProvider.getInstance(ctx).addListener({
                    try {
                        val prov = ProcessCameraProvider.getInstance(ctx).get()
                        val prev = Preview.Builder().build().also { it.surfaceProvider = pv.surfaceProvider }
                        prov.unbindAll()
                        prov.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, prev)
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))
                pv
            }
        )

        // ── CAPA 2: Velo ──────────────────────────────────────────────────────
        Box(Modifier.fillMaxSize().background(Color(0xFF060F1C).copy(alpha = 0.45f)))

        // ── CAPA 3: Tarjetas ancladas al mundo (se mueven opuesto a la cámara) ─
        val cardW     = 168.dp
        val baseCardH = 74.dp

        allGroups.forEachIndexed { gIdx, group ->
            val anchor = groupAnchor(gIdx)
            group.forEachIndexed { cIdx, question ->
                val off = qOffset(cIdx)

                // Posición en pantalla: se aleja del centro cuando la cámara gira hacia ellas
                val cardCX: Dp = swDp / 2 + (anchor.x + off.x - wx).dp
                val cardCY: Dp = shDp / 2 + (anchor.y + off.y - wy).dp

                val isHovered  = hoveredGroup  == gIdx && hoveredCard  == cIdx
                val isExpanded = expandedGroup == gIdx && expandedCard == cIdx
                val dimmed     = expandedGroup >= 0 && !isExpanded

                Box(
                    modifier = Modifier
                        .absoluteOffset(x = cardCX - cardW / 2, y = cardCY - baseCardH / 2)
                        .width(cardW)
                        .alpha(if (dimmed) 0.28f else 1f)
                ) {
                    ARQuestionCard(
                        question      = question,
                        isHovered     = isHovered,
                        isExpanded    = isExpanded,
                        hoverProgress = if (isHovered) hoverProgress else 0f,
                        onCollapse    = { expandedGroup = -1; expandedCard = -1 },
                        onSendToChat  = { onQuestionSelected(question) }
                    )
                }
            }
        }

        // ── CAPA 4: Canvas (anclas QR + líneas + retícula FIJA) ──────────────
        Canvas(Modifier.fillMaxSize()) {
            val centerX = swPx / 2f
            val centerY = shPx / 2f

            allGroups.forEachIndexed { gIdx, group ->
                val anchor = groupAnchor(gIdx)
                // Ancla del QR en pantalla: se desplaza opuesto al movimiento de cámara
                val ancX = centerX + (anchor.x - wx) * densityVal
                val ancY = centerY + (anchor.y - wy) * densityVal

                // Líneas ancla → tarjetas
                if (expandedGroup < 0) {
                    group.indices.forEach { cIdx ->
                        val off   = qOffset(cIdx)
                        val cardX = centerX + (anchor.x + off.x - wx) * densityVal
                        val cardY = centerY + (anchor.y + off.y - wy) * densityVal
                        drawLine(
                            color       = Color(0xFF2ECC71).copy(alpha = 0.14f),
                            start       = Offset(ancX, ancY),
                            end         = Offset(cardX, cardY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // Marcador QR (esquinas visor AR) — se mueve con el mundo
                val qrS    = 22.dp.toPx()
                val qrC    = 7.dp.toPx()
                val swQ    = 2.dp.toPx()
                val qAlpha = if (expandedGroup < 0) 0.80f else 0.22f
                val qCol   = Color(0xFF2ECC71).copy(alpha = qAlpha)
                drawRect(
                    color   = Color(0xFF2ECC71).copy(alpha = qAlpha * 0.4f),
                    topLeft = Offset(ancX - qrS / 2, ancY - qrS / 2),
                    size    = Size(qrS, qrS),
                    style   = Stroke(1.dp.toPx())
                )
                val l = ancX - qrS / 2; val r = ancX + qrS / 2
                val t = ancY - qrS / 2; val b = ancY + qrS / 2
                drawLine(qCol, Offset(l, t + qrC), Offset(l, t), swQ)
                drawLine(qCol, Offset(l, t), Offset(l + qrC, t), swQ)
                drawLine(qCol, Offset(r - qrC, t), Offset(r, t), swQ)
                drawLine(qCol, Offset(r, t), Offset(r, t + qrC), swQ)
                drawLine(qCol, Offset(l, b - qrC), Offset(l, b), swQ)
                drawLine(qCol, Offset(l, b), Offset(l + qrC, b), swQ)
                drawLine(qCol, Offset(r - qrC, b), Offset(r, b), swQ)
                drawLine(qCol, Offset(r, b - qrC), Offset(r, b), swQ)
            }

            // Retícula FIJA en el centro de pantalla — nunca se mueve
            if (expandedGroup < 0) {
                val cx  = centerX; val cy = centerY
                val rad = 22.dp.toPx()
                val arm = 14.dp.toPx(); val gap = 7.dp.toPx()
                val swC = 2.dp.toPx()
                val isAiming = hoveredGroup >= 0
                val crossCol = Color(0xFF2ECC71).copy(alpha = if (isAiming) 1f else 0.65f)

                drawCircle(Color(0xFF2ECC71).copy(alpha = 0.08f), rad + 6.dp.toPx(), Offset(cx, cy))
                drawCircle(crossCol, rad, Offset(cx, cy), style = Stroke(swC))
                drawCircle(crossCol, 4.dp.toPx(), Offset(cx, cy))
                drawLine(crossCol, Offset(cx - rad - arm - gap, cy), Offset(cx - rad - gap, cy), swC)
                drawLine(crossCol, Offset(cx + rad + gap, cy), Offset(cx + rad + gap + arm, cy), swC)
                drawLine(crossCol, Offset(cx, cy - rad - arm - gap), Offset(cx, cy - rad - gap), swC)
                drawLine(crossCol, Offset(cx, cy + rad + gap), Offset(cx, cy + rad + gap + arm), swC)

                if (isAiming && hoverProgress > 0f) {
                    drawArc(
                        color      = Color(0xFF4CAF50),
                        startAngle = -90f,
                        sweepAngle = 360f * hoverProgress,
                        useCenter  = false,
                        topLeft    = Offset(cx - rad, cy - rad),
                        size       = Size(rad * 2f, rad * 2f),
                        style      = Stroke(3.5f.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        // ── CAPA 5: Botón atrás ───────────────────────────────────────────────
        Surface(
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart),
            shape    = RoundedCornerShape(10.dp),
            color    = Color(0xFF0D1F35).copy(alpha = 0.85f),
            border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2ECC71).copy(alpha = 0.35f)),
            onClick  = onBack
        ) {
            androidx.compose.material3.Icon(
                painter            = androidx.compose.ui.res.painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Back",
                tint               = Color.White,
                modifier           = Modifier.padding(10.dp).size(22.dp)
            )
        }

        // ── CAPA 6: Instrucción ───────────────────────────────────────────────
        if (expandedGroup < 0) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
                shape    = RoundedCornerShape(20.dp),
                color    = Color(0xFF0D1F35).copy(alpha = 0.85f),
                border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2ECC71).copy(alpha = 0.30f))
            ) {
                Text(
                    "Mueve el celular para apuntar · 1.5s para seleccionar",
                    color    = Color(0xFF80CBC4),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

// ── Tarjeta de pregunta expandible ───────────────────────────────────────────
@Composable
private fun ARQuestionCard(
    question:      Question,
    isHovered:     Boolean,
    isExpanded:    Boolean,
    hoverProgress: Float,
    onCollapse:    () -> Unit,
    onSendToChat:  () -> Unit
) {
    val borderW = if (isHovered || isExpanded) 2.dp else 1.dp
    val bgColor = when {
        isExpanded -> Color(0xFF0D2A12)
        isHovered  -> Color(0xFF1B5E20)
        else       -> Color(0xFF0D1F35).copy(alpha = 0.92f)
    }
    val borderColor = when {
        isExpanded -> Color(0xFF4CAF50)
        isHovered  -> Color(0xFF4CAF50)
        else       -> Color(0xFF2ECC71).copy(alpha = 0.55f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape    = RoundedCornerShape(14.dp),
        color    = bgColor,
        border   = androidx.compose.foundation.BorderStroke(borderW, borderColor)
    ) {
        Column(Modifier.padding(12.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(9.dp).clip(CircleShape).background(borderColor)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text       = question.questionText,
                    color      = if (isExpanded || isHovered) Color.White else Color(0xFFCFD8DC),
                    fontSize   = 13.sp,
                    fontWeight = if (isHovered || isExpanded) FontWeight.SemiBold else FontWeight.Normal,
                    lineHeight = 18.sp,
                    maxLines   = if (isExpanded) Int.MAX_VALUE else 3
                )
            }

            if (!isExpanded && isHovered && hoverProgress > 0f) {
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0xFF2ECC71).copy(alpha = 0.22f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(hoverProgress)
                            .fillMaxHeight()
                            .background(Color(0xFF4CAF50))
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF2ECC71).copy(alpha = 0.28f))
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text       = question.answerText,
                        color      = Color(0xFFECEFF1),
                        fontSize   = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(9.dp),
                            color    = Color(0xFF1A2E1A),
                            border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF388E3C)),
                            onClick  = onCollapse
                        ) {
                            Text(
                                text       = "Cerrar",
                                color      = Color(0xFF81C784),
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign  = TextAlign.Center,
                                modifier   = Modifier.padding(vertical = 11.dp)
                            )
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(9.dp),
                            color    = Color(0xFF2E7D32),
                            onClick  = onSendToChat
                        ) {
                            Text(
                                text       = "Ver en chat",
                                color      = Color.White,
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign  = TextAlign.Center,
                                modifier   = Modifier.padding(vertical = 11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
