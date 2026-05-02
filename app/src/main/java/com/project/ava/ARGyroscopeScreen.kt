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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

// Hexágono plano (flat-top) alrededor del avatar.
// Anillo interior R=230dp: vértices a 0°,60°,120°,180°,240°,300°.
//   Las 4 tarjetas diagonales (x=±115) quedan ~22dp fuera del borde en 360dp de pantalla.
//   Las 2 laterales (x=±230) requieren giro; correcto en AR.
//   Gap mínimo entre cajas (175×82dp) con texto de 3 líneas: ≥91dp — sin solapamiento.
// Anillo exterior R=370dp: hexágono punteado (vértices a ±30°,±90°,±150°), desfasado 30°.
//   Sin solapamiento verificado par a par (ninguna caja Y se superpone con la vecina).
private val Q_OFFSETS = listOf(
    // interior (R=230) — derechas primero, sentido horario
    Offset( 115f, -199f), Offset( 230f,    0f),
    Offset( 115f,  199f), Offset(-115f,  199f),
    Offset(-230f,    0f), Offset(-115f, -199f),
    // exterior (R=370, +30°) — arriba primero, sentido horario
    Offset(   0f, -370f), Offset( 320f, -185f),
    Offset( 320f,  185f), Offset(   0f,  370f),
    Offset(-320f,  185f), Offset(-320f, -185f),
)

// Ancla de pantalla para cada grupo QR (dp desde el centro)
private val GROUP_ANCHORS = listOf(
    Offset(   0f,    0f),
    Offset(-220f, -280f),
    Offset( 220f, -280f),
    Offset(-220f,  280f),
    Offset( 220f,  280f),
)

private fun qOffset(idx: Int): Offset =
    if (idx < Q_OFFSETS.size) Q_OFFSETS[idx]
    else Offset(0f, 300f + (idx - Q_OFFSETS.size) * 90f)

private fun groupAnchor(gIdx: Int): Offset =
    if (gIdx < GROUP_ANCHORS.size) GROUP_ANCHORS[gIdx]
    else Offset(0f, 380f * (gIdx - GROUP_ANCHORS.size + 1))

// ── Giroscopio: rastrea el desplazamiento del mundo desde el momento de apertura ─
@Composable
private fun rememberParallaxOffset(): State<Offset> {
    val context = LocalContext.current
    val state   = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(Unit) {
        val sm     = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // 500 dp/rad → 10° ≈ 87dp (rango natural tipo Pokémon GO)
        // asin está acotado a [-π/2, π/2], así que el rango máximo es ±785dp — sin clamp necesario
        val sens = 500f

        val listener = object : SensorEventListener {
            private val rm    = FloatArray(9)
            private val refRm = FloatArray(9)
            private var smoothDx = 0f
            private var smoothDy = 0f
            private val alpha    = 0.2f
            private var warmupCount = 0
            private val WARMUP = 8  // ~160ms para estabilizar el sensor antes de capturar referencia

            override fun onSensorChanged(e: SensorEvent) {
                if (e.values.size < 3) return
                SensorManager.getRotationMatrixFromVector(rm, e.values)

                warmupCount++
                if (warmupCount < WARMUP) return   // state.value permanece Offset.Zero

                if (warmupCount == WARMUP) {
                    // Capturar la orientación estabilizada como referencia
                    // No se usa remapCoordinateSystem — la comparación de matrices es
                    // independiente del dispositivo: no importa cómo el fabricante
                    // definió los ejes internos del sensor.
                    rm.copyInto(refRm)
                    return
                }

                // R_delta = R_ref^T × R_cur
                // Solo se necesitan los elementos [2] y [5] de R_delta:
                //   Rd[2] = suma_k(refRm[k*3+0] × rm[k*3+2]) → ángulo horizontal desde ref
                //   Rd[5] = suma_k(refRm[k*3+1] × rm[k*3+2]) → ángulo vertical desde ref
                // Derivación: rd2 ≈ sin(yaw_delta), rd5 ≈ sin(pitch_delta)
                val rd2 = refRm[0]*rm[2] + refRm[3]*rm[5] + refRm[6]*rm[8]
                val rd5 = refRm[1]*rm[2] + refRm[4]*rm[5] + refRm[7]*rm[8]

                val targetDx =  asin(rd2.coerceIn(-1f, 1f)) * sens
                // rd5 > 0 cuando la cámara sube → objetos deben bajar en pantalla
                // → wy debe ser negativo → negar para que la dirección sea correcta
                val targetDy = -asin(rd5.coerceIn(-1f, 1f)) * sens

                smoothDx += alpha * (targetDx - smoothDx)
                smoothDy += alpha * (targetDy - smoothDy)

                state.value = Offset(smoothDx, smoothDy)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        // SENSOR_DELAY_GAME (~20ms) reduce CPU y ruido vs SENSOR_DELAY_FASTEST
        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}

// ── Pantalla AR ────────────────────────────────────────────────────────────────
@Composable
fun ARGyroscopeScreen(
    questions: List<Question>,
    onQuestionSelected: (Question) -> Unit,
    onBack: () -> Unit,
    additionalGroups: List<List<Question>> = emptyList()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val parallaxState  = rememberParallaxOffset()

    // Sin spring: el suavizado del sensor (alpha=0.2) es suficiente.
    // Agregar spring encima añadía ~450ms de lag extra — el causante de la "lentitud".
    val wx = parallaxState.value.x
    val wy = parallaxState.value.y

    val allGroups: List<List<Question>> = remember(questions, additionalGroups) {
        buildList {
            if (questions.isNotEmpty()) add(questions)
            addAll(additionalGroups)
        }
    }

    var expandedGroup by remember { mutableStateOf(-1) }
    var expandedCard  by remember { mutableStateOf(-1) }
    var hoverProgress by remember { mutableStateOf(0f) }

    // Cicla entre los 4 estados "explaining" cuando AVA está en reposo
    var explainIdx by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(2500L); explainIdx = (explainIdx + 1) % 4 }
    }

    // derivedStateOf: se recalcula solo cuando wx/wy o expandedGroup cambian de verdad,
    // no relanza un LaunchedEffect en cada frame del sensor.
    val hoveredPair by remember(allGroups) {
        derivedStateOf {
            if (expandedGroup >= 0) return@derivedStateOf -1 to -1
            val curWx = parallaxState.value.x
            val curWy = parallaxState.value.y
            var bestGroup = -1; var bestCard = -1; var minDist = Float.MAX_VALUE
            allGroups.forEachIndexed { gIdx, group ->
                val anchor = groupAnchor(gIdx)
                group.indices.forEach { cIdx ->
                    val off = qOffset(cIdx)
                    val dx  = (anchor.x + off.x) - curWx
                    val dy  = (anchor.y + off.y) - curWy
                    val d   = sqrt(dx * dx + dy * dy)
                    if (d < minDist) { minDist = d; bestGroup = gIdx; bestCard = cIdx }
                }
            }
            if (minDist < 80f) bestGroup to bestCard else -1 to -1
        }
    }
    val hoveredGroup = hoveredPair.first
    val hoveredCard  = hoveredPair.second

    val avatarRes = when {
        expandedGroup >= 0 -> R.drawable.ava_success
        hoveredGroup >= 0  -> R.drawable.ava_think
        else -> when (explainIdx) {
            0 -> R.drawable.ava_explaining1; 1 -> R.drawable.ava_explaining2
            2 -> R.drawable.ava_explaining3; else -> R.drawable.ava_explaining4
        }
    }

    // Timer de selección: solo relanza cuando el hover realmente cambia (no en cada frame)
    LaunchedEffect(hoveredPair) {
        val (hg, hc) = hoveredPair
        if (hg >= 0 && hc >= 0 && expandedGroup < 0) {
            hoverProgress = 0f
            val steps = 60; val ms = 1500L / steps
            repeat(steps) { i ->
                delay(ms)
                if (hoveredPair.first != hg || hoveredPair.second != hc) return@LaunchedEffect
                hoverProgress = (i + 1f) / steps
            }
            if (hoveredPair.first == hg && hoveredPair.second == hc) {
                expandedGroup = hg
                expandedCard  = hc
                hoverProgress = 0f
            }
        } else {
            hoverProgress = 0f
        }
    }

    // Preserva la pregunta durante el fade-out del overlay
    var lastFocusedQ by remember { mutableStateOf<Question?>(null) }
    if (expandedGroup >= 0 && expandedCard >= 0)
        allGroups.getOrNull(expandedGroup)?.getOrNull(expandedCard)?.let { lastFocusedQ = it }

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
        Box(Modifier.fillMaxSize().background(Color(0xFF060F1C).copy(alpha = 0.42f)))

        // ── CAPA 3: Tarjetas ancladas al mundo ────────────────────────────────
        val cardW     = 175.dp
        val baseCardH = 82.dp

        allGroups.forEachIndexed { gIdx, group ->
            val anchor = groupAnchor(gIdx)
            group.forEachIndexed { cIdx, question ->
                val off = qOffset(cIdx)

                val cardCX: Dp = swDp / 2 + (anchor.x + off.x - wx).dp
                val cardCY: Dp = shDp / 2 + (anchor.y + off.y - wy).dp

                val isHovered  = hoveredGroup == gIdx && hoveredCard  == cIdx
                val isExpanded = expandedGroup == gIdx && expandedCard == cIdx
                val dimmed     = expandedGroup >= 0

                val worldX = anchor.x + off.x
                val worldY = anchor.y + off.y
                val distFromCenter = sqrt((worldX - wx) * (worldX - wx) + (worldY - wy) * (worldY - wy))
                val depthT = (distFromCenter / 450f).coerceIn(0f, 1f)
                val targetScale = 1.25f - depthT * 0.60f
                val targetAlpha = 1.0f  - depthT * 0.45f
                val animatedScale by animateFloatAsState(
                    targetValue   = targetScale,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label         = "depthScale"
                )
                val animatedAlpha by animateFloatAsState(
                    targetValue   = targetAlpha,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label         = "depthAlpha"
                )

                Box(
                    modifier = Modifier
                        .absoluteOffset(x = cardCX - cardW / 2, y = cardCY - baseCardH / 2)
                        .width(cardW)
                        .scale(animatedScale)
                        .alpha(if (dimmed) 0f else animatedAlpha)
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

        // ── CAPA 3.5: Avatar anclado al mundo (mismo sistema que las tarjetas) ──
        val avSize = 150.dp
        allGroups.indices.forEach { gIdx ->
            val anchor = groupAnchor(gIdx)
            val avCX = swDp / 2 + (anchor.x - wx).dp
            val avCY = shDp / 2 + (anchor.y - wy).dp
            Box(
                modifier = Modifier
                    .absoluteOffset(x = avCX - avSize / 2, y = avCY - avSize / 2)
                    .alpha(if (expandedGroup >= 0) 0f else 1f)
            ) {
                ARAvatar(imageRes = avatarRes, size = avSize)
            }
        }

        // ── CAPA 4: Canvas (marcadores QR + líneas + retícula FIJA) ──────────
        Canvas(Modifier.fillMaxSize()) {
            val centerX = swPx / 2f
            val centerY = shPx / 2f

            allGroups.forEachIndexed { gIdx, group ->
                val anchor = groupAnchor(gIdx)
                val ancX = centerX + (anchor.x - wx) * densityVal
                val ancY = centerY + (anchor.y - wy) * densityVal

                if (expandedGroup < 0) {
                    group.indices.forEach { cIdx ->
                        val off   = qOffset(cIdx)
                        val cardX = centerX + (anchor.x + off.x - wx) * densityVal
                        val cardY = centerY + (anchor.y + off.y - wy) * densityVal
                        drawLine(
                            color       = Color(0xFF2ECC71).copy(alpha = 0.12f),
                            start       = Offset(ancX, ancY),
                            end         = Offset(cardX, cardY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                val qrS    = 24.dp.toPx()
                val qrC    = 8.dp.toPx()
                val swQ    = 2.5f.dp.toPx()
                val qAlpha = if (expandedGroup < 0) 0.85f else 0.20f
                val qCol   = Color(0xFF2ECC71).copy(alpha = qAlpha)
                drawRect(
                    color   = Color(0xFF2ECC71).copy(alpha = qAlpha * 0.35f),
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

            // Retícula FIJA en el centro — nunca se mueve
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

        // ── CAPA 7: Modo enfoque – overlay fijo en pantalla ──────────────────
        AnimatedVisibility(
            visible = expandedGroup >= 0,
            enter   = fadeIn(animationSpec  = tween(280)),
            exit    = fadeOut(animationSpec = tween(200))
        ) {
            lastFocusedQ?.let { q ->
                val overlayAvatarRes = when (explainIdx) {
                    0    -> R.drawable.ava_explaining1
                    1    -> R.drawable.ava_explaining2
                    2    -> R.drawable.ava_explaining3
                    else -> R.drawable.ava_explaining4
                }
                Box(Modifier.fillMaxSize()) {
                    // Fondo opaco sobre la cámara
                    Box(Modifier.fillMaxSize().background(Color(0xFF050D1A).copy(alpha = 0.84f)))

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 24.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // ── Izquierda: AVA explicando ──────────────────────
                        ARAvatar(imageRes = overlayAvatarRes, size = 110.dp)

                        Spacer(Modifier.width(14.dp))

                        // ── Derecha: pregunta + respuesta + botones ─────────
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Pregunta
                            Surface(
                                shape  = RoundedCornerShape(16.dp),
                                color  = Color(0xFF0D1B2A).copy(alpha = 0.96f),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF4CAF50))
                            ) {
                                Text(
                                    text       = q.questionText,
                                    color      = Color.White,
                                    fontSize   = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 23.sp,
                                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Respuesta
                            Surface(
                                shape  = RoundedCornerShape(16.dp),
                                color  = Color(0xFF0A1E0A).copy(alpha = 0.96f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2ECC71).copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text       = q.answerText,
                                    color      = Color(0xFFDCE8DC),
                                    fontSize   = 14.sp,
                                    lineHeight = 22.sp,
                                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            // Botones
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(10.dp),
                                    color    = Color(0xFF1A2E1A),
                                    border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF388E3C)),
                                    onClick  = { expandedGroup = -1; expandedCard = -1 }
                                ) {
                                    Text(
                                        text       = "Cerrar",
                                        color      = Color(0xFF81C784),
                                        fontSize   = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign  = TextAlign.Center,
                                        modifier   = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(10.dp),
                                    color    = Color(0xFF2E7D32),
                                    onClick  = { onQuestionSelected(q) }
                                ) {
                                    Text(
                                        text       = "Ver en chat",
                                        color      = Color.White,
                                        fontSize   = 14.sp,
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
        }
    }
}

// ── Avatar de AVA ──────────────────────────────────────────────────────────────
@Composable
private fun ARAvatar(imageRes: Int, size: Dp = 150.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size)
        ) {
            // Fondo circular oscuro
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFF07111F).copy(alpha = 0.88f))
            )
            // Imagen completa sin recorte: Fit muestra el personaje entero
            Image(
                painter            = painterResource(imageRes),
                contentDescription = "AVA",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .fillMaxSize()
                    .padding(10.dp)   // espacio interior → la imagen nunca toca el borde del anillo
            )
            // Anillo decorativo encima (no recorta nada)
            Box(
                Modifier
                    .fillMaxSize()
                    .border(2.5.dp, Color(0xFF2ECC71).copy(alpha = 0.92f), CircleShape)
            )
        }
        Spacer(Modifier.height(6.dp))
        Surface(
            shape  = RoundedCornerShape(8.dp),
            color  = Color(0xFF0A1628).copy(alpha = 0.88f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2ECC71).copy(alpha = 0.55f))
        ) {
            Text(
                text       = "AVA",
                color      = Color(0xFF2ECC71),
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
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
        isHovered  -> Color(0xFF1B5E20).copy(alpha = 0.90f)
        else       -> Color(0xFF0D1B2A).copy(alpha = 0.80f)
    }
    val borderColor = when {
        isExpanded -> Color(0xFF4CAF50)
        isHovered  -> Color(0xFF4CAF50)
        else       -> Color(0xFF2ECC71).copy(alpha = 0.60f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        shape    = RoundedCornerShape(16.dp),
        color    = bgColor,
        border   = androidx.compose.foundation.BorderStroke(borderW, borderColor)
    ) {
        Column(Modifier.padding(14.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(CircleShape).background(borderColor)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text       = question.questionText,
                    color      = if (isExpanded || isHovered) Color.White else Color(0xFFDEE4EA),
                    fontSize   = 15.sp,
                    fontWeight = if (isHovered || isExpanded) FontWeight.SemiBold else FontWeight.Normal,
                    lineHeight = 20.sp,
                    maxLines   = if (isExpanded) Int.MAX_VALUE else 3
                )
            }

            if (!isExpanded && isHovered && hoverProgress > 0f) {
                Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF2ECC71).copy(alpha = 0.28f))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text       = question.answerText,
                        color      = Color(0xFFECEFF1),
                        fontSize   = 14.sp,
                        lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp),
                            color    = Color(0xFF1A2E1A),
                            border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF388E3C)),
                            onClick  = onCollapse
                        ) {
                            Text(
                                text       = "Cerrar",
                                color      = Color(0xFF81C784),
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign  = TextAlign.Center,
                                modifier   = Modifier.padding(vertical = 12.dp)
                            )
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp),
                            color    = Color(0xFF2E7D32),
                            onClick  = onSendToChat
                        ) {
                            Text(
                                text       = "Ver en chat",
                                color      = Color.White,
                                fontSize   = 14.sp,
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
}
