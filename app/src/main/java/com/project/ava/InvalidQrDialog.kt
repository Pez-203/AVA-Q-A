package com.project.ava

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun InvalidQrDialogContent(
    onRetry: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.8f, animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 0.8f, animationSpec = tween(400))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "QR Inválido",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "El código escaneado no pertenece a nuestro sistema. Por favor, verifica el código e intenta nuevamente.",
                        fontSize = 14.sp,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "Reintentar escaneo",
                        fontSize = 18.sp,
                        color = Color(0xFF2E7D32), // Using fime_green equivalent
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clickable { onRetry() }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InvalidQrDialog(
    onRetry: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* No dismiss on outside click */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        InvalidQrDialogContent(onRetry = onRetry)
    }
}

@Preview(showBackground = true)
@Composable
fun InvalidQrDialogPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Gray) // Simulating some background
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .align(Alignment.Center)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "QR Inválido",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "El código escaneado no pertenece a nuestro sistema. Por favor, verifica el código e intenta nuevamente.",
                    fontSize = 14.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Reintentar escaneo",
                    fontSize = 18.sp,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .padding(8.dp)
                )
            }
        }
    }
}
