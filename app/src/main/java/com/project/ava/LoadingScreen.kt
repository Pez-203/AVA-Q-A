package com.project.ava

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoadingScreen(
    onBack: () -> Unit,
    onHelp: () -> Unit
) {
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

        Spacer(modifier = Modifier.height(32.dp))

        // Circular Progress with QR
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF2E7D32),
                strokeWidth = 2.dp,
                trackColor = Color(0xFF2E7D32).copy(alpha = 0.1f)
            )
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFFF1F8E9), shape = CircleShape)
                    .border(1.dp, Color(0xFF2E7D32), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_qr_code_dark),
                    contentDescription = null,
                    tint = Color(0xFF1E2A38),
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Cargando Recursos....",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Progress Steps
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressStep(
                icon = Icons.Default.CheckCircle,
                label = "1. Escaneado",
                isCompleted = true,
                isActive = false
            )
            
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.Gray).padding(horizontal = 4.dp))

            ProgressStep(
                icon = Icons.Default.CheckCircle,
                label = "2. Validado",
                isCompleted = true,
                isActive = false
            )

            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.Gray).padding(horizontal = 4.dp))

            ProgressStep(
                icon = Icons.Default.AccountCircle,
                label = "3. Cargando",
                isCompleted = false,
                isActive = true
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
        
        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = Color(0xFFC8E6C9), thickness = 1.dp)

        // Info Card and Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(24.dp)
        ) {
            // "Sabias que" Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sabias que..",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White, CircleShape)
                                .border(1.dp, Color.Gray, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lightbulb),
                                contentDescription = null,
                                tint = Color(0xFF1E2A38),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AVA puede responder preguntas del sector escaneado",
                        fontSize = 16.sp,
                        color = Color(0xFF1E2A38).copy(alpha = 0.8f)
                    )
                }
            }

            // Character Image
            Image(
                painter = painterResource(id = R.drawable.ava_success),
                contentDescription = "AVA",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(200.dp)
                    .offset(y = 20.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun ProgressStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isCompleted: Boolean,
    isActive: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(
                    if (isCompleted || isActive) Color(0xFF2E7D32) else Color(0xFFE0E0E0),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (isCompleted) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check_circle),
                contentDescription = null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(20.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.dp, Color.Gray, CircleShape)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoadingScreenPreview() {
    LoadingScreen(onBack = {}, onHelp = {})
}
