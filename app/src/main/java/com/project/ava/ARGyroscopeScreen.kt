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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.project.ava.data.Question
import kotlinx.coroutines.delay
import kotlin.math.*

// ── Giroscopio: parallax suave ±18dp ─────────────────────────────────────────
@Composable
private fun rememberParallaxOffset(): State<Offset> {
    val context = LocalContext.current
    val raw     = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(Unit) {
        val sm     = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Usamos posición absoluta pero con referencia re-calibrable
        var refPitch   = Float.NaN
        var refAzimuth = Float.NaN
        val sens = 30f      // ← aumentado
        val max  = 300f     // ← sin límite práctico

        val listener = object : SensorEventListener {
            private val rm    = FloatArray(9)
            private val outRm = FloatArray(9)
            private val ori   = FloatArray(3)

            private var smoothAz = 0f
            private var smoothPi = 0f
            private val alpha    = 0.3f   // ← más reactivo
            private var first    = true
            private var count    = 0

            override fun onSensorChanged(e: SensorEvent) {
                if (e.values.size < 3) return
                SensorManager.getRotationMatrixFromVector(rm, e.values)
                SensorManager.remapCoordinateSystem(
                    rm, SensorManager.AXIS_X, SensorManager.AXIS_Z, outRm
                )
                SensorManager.getOrientation(outRm, ori)

                val az = Math.toDegrees(ori[0].toDouble()).toFloat()
                val pi = Math.toDegrees(ori[1].toDouble()).toFloat()

                if (first) {
                    smoothAz = az; smoothPi = pi; first = false
                } else {
                    // Interpolación circular para azimuth (evita salto en ±180)
                    var diff = az - smoothAz
                    if (diff > 180)  diff -= 360
                    if (diff < -180) diff += 360
                    smoothAz += alpha * diff
                    smoothPi += alpha * (pi - smoothPi)
                }

                // Esperar 5 muestras (antes 10) para calibrar más rápido
                if (count < 5) {
                    count++
                    if (count == 5) {
                        refAzimuth = smoothAz
                        refPitch   = smoothPi
                    }
                    return
                }

                var dAz = smoothAz - refAzimuth
                if (dAz > 180)  dAz -= 360
                if (dAz < -180) dAz += 360
                val dPi = smoothPi - refPitch

                val dx = (dAz  * sens).coerceIn(-max, max)
                val dy = (-dPi * sens).coerceIn(-max, max)
                raw.value = Offset(dx, dy)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensor?.let {
            sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST) // ← FASTEST en vez de GAME
        }
        onDispose { sm.unregisterListener(listener) }
    }
    return raw
}
//
// ── Pantalla AR con cuadrícula fija ───────────────────────────────────────────
@Composable
fun ARGyroscopeScreen(
    questions: List<Question>,
    onQuestionSelected: (Question) -> Unit,
    onBack: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context        = LocalContext.current
    val parallaxRaw    by rememberParallaxOffset()

    // Parallax animado suavemente
    val px by animateFloatAsState(
        targetValue = parallaxRaw.x,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow), // ← más suave/rápido
        label = "px"
    )
    val py by animateFloatAsState(
        targetValue = parallaxRaw.y,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "py"
    )

    val densityVal = context.resources.displayMetrics.density

    var hoveredIndex     by remember { mutableStateOf(-1) }
    var hoverProgress    by remember { mutableStateOf(0f) }
    var selectedQuestion by remember { mutableStateOf<Question?>(null) }
    var showAnswer       by remember { mutableStateOf(false) }

    // Posiciones en pantalla de cada chip (para detectar hover con el retículo)
    val chipCenters = remember { mutableStateMapOf<Int, Offset>() }
    // Centro de pantalla (retículo) — se calcula una vez con BoxWithConstraints
    var screenCenter by remember { mutableStateOf(Offset.Zero) }

    // Detectar qué chip está bajo el retículo
    // Reemplaza el LaunchedEffect de hover detection:
    LaunchedEffect(chipCenters.toMap(), screenCenter, px, py) {
        if (screenCenter == Offset.Zero || showAnswer) return@LaunchedEffect

        val margin = 60.dp.value * densityVal

        // 👇 Misma fórmula que en el Canvas — posición real del retículo en pantalla
        val pointerX = (screenCenter.x + px * densityVal)
            .coerceIn(margin, screenCenter.x * 2 - margin)
        val pointerY = (screenCenter.y + py * densityVal)
            .coerceIn(margin, screenCenter.y * 2 - margin)
        val pointerPos = Offset(pointerX, pointerY)

        var closest = -1
        var minDist = Float.MAX_VALUE

        chipCenters.forEach { (idx, center) ->
            val dist = sqrt(
                (center.x - pointerPos.x).pow(2) +
                        (center.y - pointerPos.y).pow(2)
            )
            if (dist < minDist) { minDist = dist; closest = idx }
        }

        val threshold = 80.dp.value * densityVal
        hoveredIndex = if (minDist < threshold) closest else -1
    }

    // Timer de selección 1.5s
    LaunchedEffect(hoveredIndex) {
        if (hoveredIndex >= 0 && !showAnswer) {
            hoverProgress = 0f
            val steps = 60; val stepMs = 1500L / steps
            repeat(steps) { i ->
                delay(stepMs)
                if (hoveredIndex < 0) return@LaunchedEffect
                hoverProgress = (i + 1f) / steps
            }
            if (hoveredIndex >= 0) {
                selectedQuestion = questions[hoveredIndex]
                showAnswer       = true
                hoveredIndex     = -1
                hoverProgress    = 0f
            }
        } else {
            hoverProgress = 0f
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // Guardar el centro de pantalla (posición del retículo)
        LaunchedEffect(w, h) {
            screenCenter = Offset(w / 2f, h / 2f)
        }

        // ── CAPA 1: Cámara ────────────────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                val pv = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                ProcessCameraProvider.getInstance(ctx).addListener({
                    try {
                        val prov = ProcessCameraProvider.getInstance(ctx).get()
                        val prev = Preview.Builder().build()
                            .also { it.surfaceProvider = pv.surfaceProvider }
                        prov.unbindAll()
                        prov.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, prev)
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))
                pv
            }
        )

        // ── CAPA 2: Velo ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF060F1C).copy(alpha = 0.52f))
        )

        // ── CAPA 3: Cuadrícula de preguntas (estática) ───────────────────────
        if (!showAnswer) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Dividir preguntas: mitad arriba del retículo, mitad abajo
                val half  = (questions.size + 1) / 2
                val top   = questions.subList(0, half)
                val bot   = questions.subList(half, questions.size)

                // ── Zona superior ─────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 88.dp, start = 14.dp, end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    top.chunked(2).forEach { row ->
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { q ->
                                val idx       = questions.indexOf(q)
                                val isHovered = hoveredIndex == idx
                                ChipItem(
                                    question  = q,
                                    isHovered = isHovered,
                                    modifier  = Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { coords ->
                                            val b = coords.boundsInRoot()
                                            chipCenters[idx] = Offset(
                                                b.left + b.width / 2f,
                                                b.top  + b.height / 2f
                                            )
                                        }
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                // ── Zona inferior ─────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp, start = 14.dp, end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bot.chunked(2).forEach { row ->
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { q ->
                                val idx       = questions.indexOf(q)
                                val isHovered = hoveredIndex == idx
                                ChipItem(
                                    question  = q,
                                    isHovered = isHovered,
                                    modifier  = Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { coords ->
                                            val b = coords.boundsInRoot()
                                            chipCenters[idx] = Offset(
                                                b.left + b.width / 2f,
                                                b.top  + b.height / 2f
                                            )
                                        }
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ── CAPA 4: Mano como puntero ────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val offsetX  = px * densityVal
            val offsetY  = py * densityVal
            val margin   = 60.dp.toPx()
            val cx = (size.width  / 2f + offsetX).coerceIn(margin, size.width  - margin)
            val cy = (size.height / 2f + offsetY).coerceIn(margin, size.height - margin)
            val isAiming = hoveredIndex >= 0 && !showAnswer

            drawHandCursor(tip = Offset(cx, cy), color = Color(0xFF2ECC71), isAiming = isAiming)

            // Arco de carga alrededor de la mano (tipo Kinect)
            if (isAiming && hoverProgress > 0f) {
                val arcR   = 40.dp.toPx()
                val palmCx = cx + 6.dp.toPx()
                val palmCy = cy + 22.dp.toPx()
                drawArc(
                    color      = Color(0xFF4CAF50),
                    startAngle = -90f,
                    sweepAngle = 360f * hoverProgress,
                    useCenter  = false,
                    topLeft    = Offset(palmCx - arcR, palmCy - arcR),
                    size       = Size(arcR * 2f, arcR * 2f),
                    style      = Stroke(width = 4.5f.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // ── CAPA 5: Panel de respuesta ────────────────────────────────────────
        if (showAnswer && selectedQuestion != null) {
            val a by animateFloatAsState(1f, tween(350), label = "a")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF030A14).copy(alpha = 0.88f))
                    .alpha(a),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.92f).wrapContentHeight(),
                    shape    = RoundedCornerShape(20.dp),
                    color    = Color(0xFF0D1F35),
                    border   = androidx.compose.foundation.BorderStroke(1.5f.dp, Color(0xFF2ECC71))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            selectedQuestion!!.questionText,
                            color = Color(0xFF4CAF50), fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, lineHeight = 21.sp
                        )
                        Box(Modifier.fillMaxWidth().height(1.dp)
                            .background(Color(0xFF2ECC71).copy(alpha = 0.25f)))
                        Text(
                            selectedQuestion!!.answerText,
                            color = Color(0xFFECEFF1), fontSize = 13.sp, lineHeight = 19.sp
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(10.dp),
                                color    = Color(0xFF1A2E1A),
                                border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF388E3C)),
                                onClick  = { selectedQuestion = null; showAnswer = false }
                            ) {
                                Text(
                                    text       = "Otra pregunta",
                                    color      = Color(0xFF81C784),
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign  = TextAlign.Center,
                                    modifier   = Modifier.padding(vertical = 12.dp)
                                )
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(10.dp),
                                color    = Color(0xFF2E7D32),
                                onClick  = { onQuestionSelected(selectedQuestion!!) }
                            ) {
                                Text(
                                    text       = "Ver en chat",
                                    color      = Color.White,
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign  = TextAlign.Center,
                                    modifier   = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── CAPA 6: UI fija ───────────────────────────────────────────────────
        Surface(
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart),
            shape    = RoundedCornerShape(10.dp),
            color    = Color(0xFF0D1F35).copy(alpha = 0.85f),
            border   = androidx.compose.foundation.BorderStroke(
                1.dp, Color(0xFF2ECC71).copy(alpha = 0.35f)),
            onClick  = onBack
        ) {
            androidx.compose.material3.Icon(
                painter            = androidx.compose.ui.res.painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Back",
                tint               = Color.White,
                modifier           = Modifier.padding(10.dp).size(22.dp)
            )
        }

        if (!showAnswer) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
                shape    = RoundedCornerShape(20.dp),
                color    = Color(0xFF0D1F35).copy(alpha = 0.85f),
                border   = androidx.compose.foundation.BorderStroke(
                    1.dp, Color(0xFF2ECC71).copy(alpha = 0.3f))
            ) {
                Text(
                    "Apunta la mano · mantén 1.5s para seleccionar",
                    color    = Color(0xFF80CBC4),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

// ── Cursor de mano (estilo Kinect) ────────────────────────────────────────────
private fun DrawScope.drawHandCursor(tip: Offset, color: Color, isAiming: Boolean) {
    val s  = density                           // px por dp
    val fW = 11f * s;  val fH = 30f * s       // dedo índice: 11dp × 30dp
    val pW = 32f * s;  val pH = 20f * s       // palma: 32dp × 20dp
    val sw = 2.2f * s                          // grosor de trazo
    val palmOffX = 6f * s                      // palma desplazada a la derecha del dedo

    // Posiciones relativas al punto caliente (tip = punta del dedo)
    val fingerLeft   = tip.x - fW / 2f
    val fingerBottom = tip.y + fH
    val palmCX   = tip.x + palmOffX
    val palmLeft = palmCX - pW / 2f
    val palmTop  = fingerBottom - 3f * s
    val palmBot  = palmTop + pH

    val outline = color.copy(alpha = if (isAiming) 1f else 0.78f)
    val fill    = Color(0xFF0A1A2E).copy(alpha = 0.88f)
    val glow    = color.copy(alpha = if (isAiming) 0.22f else 0.07f)

    // Halo difuso detrás de la mano
    drawOval(
        color   = glow,
        topLeft = Offset(palmLeft - 6f * s, tip.y - 4f * s),
        size    = Size(pW + 12f * s, palmBot - tip.y + 8f * s)
    )

    // Dedos curvados (dibujados antes de la palma para que queden cubiertos en la base)
    val bumpW = 9f * s
    listOf(18f * s, 13f * s, 9f * s).forEachIndexed { i, bumpH ->
        val bx = fingerLeft + fW + s + i * (bumpW + s)
        val by = palmTop - bumpH
        drawRoundRect(
            color        = fill,
            topLeft      = Offset(bx, by),
            size         = Size(bumpW, bumpH + 3f * s),
            cornerRadius = CornerRadius(bumpW / 2f)
        )
        drawRoundRect(
            color        = outline,
            topLeft      = Offset(bx, by),
            size         = Size(bumpW, bumpH + 3f * s),
            cornerRadius = CornerRadius(bumpW / 2f),
            style        = Stroke(sw)
        )
    }

    // Palma
    drawRoundRect(
        color        = fill,
        topLeft      = Offset(palmLeft, palmTop),
        size         = Size(pW, pH),
        cornerRadius = CornerRadius(5f * s)
    )
    drawRoundRect(
        color        = outline,
        topLeft      = Offset(palmLeft, palmTop),
        size         = Size(pW, pH),
        cornerRadius = CornerRadius(5f * s),
        style        = Stroke(sw)
    )

    // Pulgar
    val tW = 14f * s;  val tH = 11f * s
    val tLeft = palmLeft - tW + 5f * s;  val tTop = palmTop + 6f * s
    drawOval(color = fill,    topLeft = Offset(tLeft, tTop), size = Size(tW, tH))
    drawOval(color = outline, topLeft = Offset(tLeft, tTop), size = Size(tW, tH), style = Stroke(sw))

    // Dedo índice (encima de todo para que el trazo quede limpio)
    drawRoundRect(
        color        = fill,
        topLeft      = Offset(fingerLeft, tip.y),
        size         = Size(fW, fH),
        cornerRadius = CornerRadius(fW / 2f)
    )
    drawRoundRect(
        color        = outline,
        topLeft      = Offset(fingerLeft, tip.y),
        size         = Size(fW, fH),
        cornerRadius = CornerRadius(fW / 2f),
        style        = Stroke(sw)
    )

    // Punto caliente en la punta del dedo
    drawCircle(color = outline, radius = 3f * s, center = tip)
}

// ── Chip de pregunta ──────────────────────────────────────────────────────────
@Composable
private fun ChipItem(
    question:  Question,
    isHovered: Boolean,
    modifier:  Modifier = Modifier
) {
    val bgColor  = if (isHovered) Color(0xFF1B5E20) else Color(0xFF0D1F35).copy(alpha = 0.90f)
    val border   = if (isHovered) Color(0xFF4CAF50) else Color(0xFF2ECC71).copy(alpha = 0.55f)
    val dotColor = if (isHovered) Color(0xFF4CAF50) else Color(0xFF2ECC71).copy(alpha = 0.7f)
    val txtColor = if (isHovered) Color.White       else Color(0xFFB0BEC5)
    val weight   = if (isHovered) FontWeight.SemiBold else FontWeight.Normal

    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(14.dp),
        color    = bgColor,
        border   = androidx.compose.foundation.BorderStroke(
            if (isHovered) 2.dp else 1.dp, border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = question.questionText,
                color      = txtColor,
                fontSize   = 11.sp,
                fontWeight = weight,
                lineHeight = 15.sp,
                maxLines   = 3
            )
        }
    }
}