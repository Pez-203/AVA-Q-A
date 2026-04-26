package com.project.ava

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ScannerScreen(
    onPreviewViewCreated: (PreviewView) -> Unit,
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

        // Scanning Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E2A38))
        ) {
            // Camera Preview
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        onPreviewViewCreated(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlays
            ScannerOverlay()

            Text(
                text = "Alinea el código dentro del marco",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                textAlign = TextAlign.Center
            )
        }

        // Instruction and Button Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Transparent, shape = RoundedCornerShape(32.dp))
                        .padding(2.dp)
                        .background(Color.White, shape = RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_qr_code_dark),
                        contentDescription = null,
                        tint = Color(0xFF1E2A38),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Escanea un código QR válido",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "Apunta tu cámara hacia el código para continuar",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { /* Already active */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera_white),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "ESCANEAR QR",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ScannerOverlay() {
    val greenColor = Color(0xFF2E7D32)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val cornerSize = 40.dp.toPx()
        val strokeWidth = 4.dp.toPx()

        // Corners
        // Top-Left
        drawLine(greenColor, Offset(0f, 0f), Offset(cornerSize, 0f), strokeWidth)
        drawLine(greenColor, Offset(0f, 0f), Offset(0f, cornerSize), strokeWidth)

        // Top-Right
        drawLine(greenColor, Offset(width, 0f), Offset(width - cornerSize, 0f), strokeWidth)
        drawLine(greenColor, Offset(width, 0f), Offset(width, cornerSize), strokeWidth)

        // Bottom-Left
        drawLine(greenColor, Offset(0f, height), Offset(cornerSize, height), strokeWidth)
        drawLine(greenColor, Offset(0f, height), Offset(0f, height - cornerSize), strokeWidth)

        // Bottom-Right
        drawLine(greenColor, Offset(width, height), Offset(width - cornerSize, height), strokeWidth)
        drawLine(greenColor, Offset(width, height), Offset(width, height - cornerSize), strokeWidth)

        // Horizontal Scanning Line
        drawLine(
            greenColor,
            Offset(40.dp.toPx(), height / 2),
            Offset(width - 40.dp.toPx(), height / 2),
            2.dp.toPx()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ScannerScreenPreview() {
    ScannerScreen(onPreviewViewCreated = {}, onBack = {}, onHelp = {})
}
