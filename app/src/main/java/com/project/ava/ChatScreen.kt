package com.project.ava

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.project.ava.data.Question
import kotlinx.coroutines.launch

private data class ChatMessage(val text: String, val isUser: Boolean)

@Composable
private fun CameraPreviewView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                try {
                    val provider = future.get()
                    val preview = Preview.Builder().build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview
                    )
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

@Composable
private fun ScannerCornersOverlay(modifier: Modifier = Modifier) {
    val green = Color(0xFF2E7D32)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = 36.dp.toPx()
        val stroke = 3.5f.dp.toPx()
        drawLine(green, Offset(0f, 0f), Offset(corner, 0f), stroke)
        drawLine(green, Offset(0f, 0f), Offset(0f, corner), stroke)
        drawLine(green, Offset(w, 0f), Offset(w - corner, 0f), stroke)
        drawLine(green, Offset(w, 0f), Offset(w, corner), stroke)
        drawLine(green, Offset(0f, h), Offset(corner, h), stroke)
        drawLine(green, Offset(0f, h), Offset(0f, h - corner), stroke)
        drawLine(green, Offset(w, h), Offset(w - corner, h), stroke)
        drawLine(green, Offset(w, h), Offset(w, h - corner), stroke)
    }
}

@Composable
fun ChatScreen(
    categoryTitle: String,
    questions: List<Question>,
    // Pregunta pre-seleccionada desde la pantalla AR (puede ser null)
    initialQuestion: Question? = null,
    onBack: () -> Unit,
    onHelp: () -> Unit
) {
    val chatHistory = remember { mutableStateListOf<ChatMessage>() }
    var isMenuExpanded by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Si viene una pregunta pre-seleccionada desde AR, agregarla al historial
    // una sola vez al entrar a la pantalla
    LaunchedEffect(initialQuestion) {
        if (initialQuestion != null && chatHistory.isEmpty()) {
            chatHistory.add(ChatMessage(initialQuestion.questionText, isUser = true))
            chatHistory.add(ChatMessage(initialQuestion.answerText, isUser = false))
            isMenuExpanded = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Color(0xFF2E7D32))
                .padding(16.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_back),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.TopStart)
                    .clickable { onBack() }
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_help),
                contentDescription = "Help",
                tint = Color.White,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.TopEnd)
                    .clickable { onHelp() }
            )
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chat_white),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "App AVA Q&A",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Contenido principal ──────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Área superior: cámara + personaje
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E2A38))
                ) {
                    CameraPreviewView(modifier = Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A2433).copy(alpha = 0.40f))
                    )
                    ScannerCornersOverlay(modifier = Modifier.fillMaxSize())
                    Image(
                        painter = painterResource(id = R.drawable.ava_think),
                        contentDescription = "AVA",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 28.dp),
                        contentScale = ContentScale.Fit
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        color = Color.Black.copy(alpha = 0.60f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_help),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "¿Tienes alguna duda? Estoy aquí para ayudarte.",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Historial acumulativo del chat
            items(chatHistory) { message ->
                ChatBubble(text = message.text, isUser = message.isUser)
            }
        }

        // ── Panel desplegable "Preguntas" ─────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFC8E6C9),
            tonalElevation = 4.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isMenuExpanded = !isMenuExpanded }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Preguntas",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isMenuExpanded)
                            Icons.Default.KeyboardArrowDown
                        else
                            Icons.Default.KeyboardArrowUp,
                        contentDescription = null
                    )
                }

                if (isMenuExpanded) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF1F8E9)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Preguntas Frecuentes",
                                color = Color(0xFF2E7D32),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            questions.forEach { question ->
                                FaqMenuItem(
                                    question = question.questionText,
                                    onClick = {
                                        chatHistory.add(
                                            ChatMessage(question.questionText, isUser = true)
                                        )
                                        chatHistory.add(
                                            ChatMessage(question.answerText, isUser = false)
                                        )
                                        isMenuExpanded = false
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(
                                                listState.layoutInfo.totalItemsCount - 1
                                            )
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FaqMenuItem(question: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC8E6C9))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = question,
                modifier = Modifier.weight(1f),
                color = Color(0xFF2E7D32),
                fontSize = 16.sp
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Black
            )
        }
    }
}

@Composable
fun ChatBubble(text: String, isUser: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) Color(0xFF66BB6A) else Color(0xFFE8F5E9),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 0.dp,
                bottomEnd = if (isUser) 0.dp else 12.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                color = if (isUser) Color.White else Color(0xFF2E7D32),
                fontSize = 16.sp
            )
        }
    }
}

@ComposePreview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    ChatScreen(
        categoryTitle = "Práctica 1",
        questions = listOf(
            Question(1, 1, "¿Cómo funciona la práctica?", "La práctica consiste en escanear..."),
            Question(2, 1, "¿Dónde encuentro los materiales?", "Los materiales están en el locker..."),
            Question(3, 1, "¿Cuál es el objetivo?", "El objetivo es identificar...")
        ),
        initialQuestion = null,
        onBack = {},
        onHelp = {}
    )
}
