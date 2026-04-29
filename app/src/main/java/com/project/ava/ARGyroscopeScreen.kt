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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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

        var refPitch = Float.NaN
        var refRoll  = Float.NaN
        val sens = 4f
        val max  = 18f

        val listener = object : SensorEventListener {
            private val rm  = FloatArray(9)
            private val ori = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rm, e.values)
                SensorManager.getOrientation(rm, ori)
                val pitch = Math.toDegrees(ori[1].toDouble()).toFloat()
                val roll  = Math.toDegrees(ori[2].toDouble()).toFloat()
                if (refPitch.isNaN()) { refPitch = pitch; refRoll = roll; return }
                val dx = (-(roll - refRoll) * sens).coerceIn(-max, max)
                val dy = ((pitch - refPitch) * sens).coerceIn(-max, max)
                raw.value = Offset(dx, dy)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(listener) }
    }
    return raw
}

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
    val px by animateFloatAsState(parallaxRaw.x, spring(stiffness = Spring.StiffnessLow), label = "px")
    val py by animateFloatAsState(parallaxRaw.y, spring(stiffness = Spring.StiffnessLow), label = "py")

    var hoveredIndex     by remember { mutableStateOf(-1) }
    var hoverProgress    by remember { mutableStateOf(0f) }
    var selectedQuestion by remember { mutableStateOf<Question?>(null) }
    var showAnswer       by remember { mutableStateOf(false) }

    // Posiciones en pantalla de cada chip (para detectar hover con el retículo)
    val chipCenters = remember { mutableStateMapOf<Int, Offset>() }
    // Centro de pantalla (retículo) — se calcula una vez con BoxWithConstraints
    var screenCenter by remember { mutableStateOf(Offset.Zero) }

    // Detectar qué chip está bajo el retículo
    LaunchedEffect(chipCenters.toMap(), screenCenter, px, py) {
        if (screenCenter == Offset.Zero || showAnswer) return@LaunchedEffect
        var closest = -1
        var minDist = Float.MAX_VALUE
        chipCenters.forEach { (idx, center) ->
            // El chip se desplaza con parallax, ajustar su centro
            val adjustedCenter = Offset(center.x + px, center.y + py)
            val dist = sqrt(
                (adjustedCenter.x - screenCenter.x).pow(2) +
                        (adjustedCenter.y - screenCenter.y).pow(2)
            )
            if (dist < minDist) { minDist = dist; closest = idx }
        }
        // Solo activar si está suficientemente cerca (radio del retículo ~55dp)
        val density    = context.resources.displayMetrics.density
        val threshold  = 80.dp.value * density
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

        // ── CAPA 3: Cuadrícula de preguntas (con parallax) ────────────────────
        if (!showAnswer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = px.dp, y = py.dp)
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

        // ── CAPA 4: Retículo central fijo ─────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx       = size.width  / 2f
            val cy       = size.height / 2f
            val density  = context.resources.displayMetrics.density
            val r        = 55.dp.value * density
            val isAiming = hoveredIndex >= 0 && !showAnswer

            // Halo exterior
            drawCircle(
                color  = Color(0xFF2ECC71).copy(alpha = 0.10f),
                radius = r + 12.dp.toPx(),
                center = Offset(cx, cy)
            )
            // Círculo principal
            drawCircle(
                color  = Color(0xFF2ECC71).copy(alpha = if (isAiming) 1f else 0.6f),
                radius = r,
                center = Offset(cx, cy),
                style  = Stroke(if (isAiming) 2.5f.dp.toPx() else 1.5f.dp.toPx())
            )
            // Relleno suave al apuntar
            if (isAiming) drawCircle(
                Color(0xFF2ECC71).copy(alpha = 0.08f), r, Offset(cx, cy)
            )
            // Punto
            drawCircle(Color(0xFF2ECC71), 5.dp.toPx(), Offset(cx, cy))
            // Cruz
            val arm = 16.dp.toPx(); val gap = 8.dp.toPx(); val sw = 2f.dp.toPx()
            drawLine(Color(0xFF2ECC71), Offset(cx - arm - gap, cy), Offset(cx - gap, cy), sw)
            drawLine(Color(0xFF2ECC71), Offset(cx + gap, cy), Offset(cx + arm + gap, cy), sw)
            drawLine(Color(0xFF2ECC71), Offset(cx, cy - arm - gap), Offset(cx, cy - gap), sw)
            drawLine(Color(0xFF2ECC71), Offset(cx, cy + gap), Offset(cx, cy + arm + gap), sw)
            // Arco de progreso
            if (isAiming && hoverProgress > 0f) {
                drawArc(
                    color      = Color(0xFF4CAF50),
                    startAngle = -90f,
                    sweepAngle = 360f * hoverProgress,
                    useCenter  = false,
                    topLeft    = Offset(cx - r, cy - r),
                    size       = Size(r * 2f, r * 2f),
                    style      = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
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
                    "Apunta el retículo · mantén 1.5s para seleccionar",
                    color    = Color(0xFF80CBC4),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
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