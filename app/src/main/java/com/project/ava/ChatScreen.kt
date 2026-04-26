package com.project.ava

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project.ava.data.Question

@Composable
fun ChatScreen(
    categoryTitle: String,
    questions: List<Question>,
    onBack: () -> Unit,
    onHelp: () -> Unit
) {
    var selectedQuestion by remember { mutableStateOf<Question?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header
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

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AVA Thinking Image Area
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E2A38))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ava_think),
                        contentDescription = "AVA Thinking",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    
                    // Small overlay text/icon similar to image
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        color = Color.Black.copy(alpha = 0.6f),
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

            // Chat conversation
            if (selectedQuestion != null) {
                item {
                    ChatBubble(
                        text = selectedQuestion!!.questionText,
                        isUser = true
                    )
                }
                item {
                    ChatBubble(
                        text = selectedQuestion!!.answerText,
                        isUser = false
                    )
                }
                item {
                    ChatBubble(
                        text = "¿Tengo otra duda?",
                        isUser = true
                    )
                }
                item {
                    ChatBubble(
                        text = "Claro, puedes seleccionar otra pregunta del menú o cerrar la aplicación.",
                        isUser = false
                    )
                }
            } else {
                // FAQ Section
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF1F8E9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Preguntas Frecuentes",
                                color = Color(0xFF2E7D32),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            questions.forEach { question ->
                                FaqItem(
                                    question = question.questionText,
                                    onClick = { selectedQuestion = question }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }

        // Bottom Button/Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFC8E6C9)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { selectedQuestion = null },
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
                    text = if (selectedQuestion == null) "Otras Preguntas" else "Preguntas",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                if (selectedQuestion != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = null)
                }
            }
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
            color = if (isUser) Color(0xFF81C784) else Color(0xFFE8F5E9),
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

@Composable
fun FaqItem(question: String, onClick: () -> Unit) {
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

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    ChatScreen(
        categoryTitle = "Práctica 1",
        questions = listOf(
            Question(1, 1, "¿Cómo funciona la práctica?", "La práctica consiste en escanear..."),
            Question(2, 1, "¿Dónde encuentro los materiales?", "Los materiales están en el locker..."),
            Question(3, 1, "¿Cuál es el objetivo de esta estación?", "El objetivo es identificar...")
        ),
        onBack = {},
        onHelp = {}
    )
}
