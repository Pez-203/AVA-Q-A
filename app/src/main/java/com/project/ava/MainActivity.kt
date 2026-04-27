package com.project.ava

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.project.ava.data.AppDatabase
import com.project.ava.data.Question
import com.project.ava.data.QuestionRepository
import com.project.ava.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.project.ava.data.CategoryWithQuestions
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var database: AppDatabase
    private lateinit var repository: QuestionRepository
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var isProcessing = false

    private var composeScreenState by mutableStateOf<String?>(null) // null, "loading", "chat"
    private var currentData: CategoryWithQuestions? = null
    private var showExitDialog by mutableStateOf(false)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startScanning()
        } else {
            Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getInstance(this)
        repository = QuestionRepository(database)
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.composeView.setContent {
            val screen = composeScreenState
            val data = currentData

            if (showExitDialog) {
                ExitConfirmationDialog(
                    onConfirm = { finishAffinity() },
                    onDismiss = {
                        showExitDialog = false
                        if (composeScreenState == null) {
                            binding.composeView.visibility = View.GONE
                        }
                    }
                )
            }

            when (screen) {
                "loading" -> {
                    LoadingScreen(
                        onBack = { resetToInitial() },
                        onHelp = {
                            Toast.makeText(this@MainActivity, "Cargando recursos...", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                "chat" -> {
                    if (data != null) {
                        ChatScreen(
                            categoryTitle = data.category.title,
                            questions = data.questions,
                            onBack = { resetToInitial() },
                            onHelp = {
                                Toast.makeText(this@MainActivity, "Ayuda activa.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        binding.composeView.visibility = View.GONE
        binding.initialUi.visibility = View.VISIBLE

        binding.btnMenu.setOnClickListener {
            binding.composeView.visibility = View.VISIBLE
            showExitDialog = true
        }

        binding.btnScan.setOnClickListener {
            checkCameraPermission()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startScanning()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScanning() {
        // binding.initialUi.visibility = View.GONE // Mantener visible el resto de la UI
        binding.cameraPreview.visibility = View.VISIBLE
        binding.scanInstructionArea.visibility = View.VISIBLE
        startCamera(binding.cameraPreview)
    }

    private fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(options)

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy, scanner)
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProviderFuture.get().unbindAll()
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun processImage(
        imageProxy: ImageProxy,
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner
    ) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { qrText ->
                        if (!isProcessing) {
                            isProcessing = true
                            handleQrCode(qrText)
                        }
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleQrCode(qrCode: String) {
        scope.launch(Dispatchers.Main) {
            try {
                stopCamera()
                
                // Mostrar pantalla de carga
                binding.cameraPreview.visibility = View.INVISIBLE
                binding.scanInstructionArea.visibility = View.GONE
                
                binding.composeView.visibility = View.VISIBLE
                composeScreenState = "loading"
                
                // Cargar datos
                val result = repository.getCategoryWithQuestions(qrCode)
                
                // Retraso artificial para que se vea la pantalla de carga (según requerimiento)
                delay(2000)
                
                if (result != null) {
                    currentData = result
                    composeScreenState = "chat"
                } else {
                    // Si no se encuentra en la base de datos, mostramos error y volvemos al inicio
                    Toast.makeText(this@MainActivity, "Categoría no encontrada: $qrCode", Toast.LENGTH_LONG).show()
                    resetToInitial()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                resetToInitial()
            }
        }
    }

    private fun resetToInitial() {
        composeScreenState = null
        currentData = null
        binding.composeView.visibility = View.GONE
        binding.cameraPreview.visibility = View.INVISIBLE
        binding.scanInstructionArea.visibility = View.GONE
        binding.initialUi.visibility = View.VISIBLE
        isProcessing = false
    }

    override fun onBackPressed() {
        if (composeScreenState != null) {
            resetToInitial()
        } else if (binding.cameraPreview.visibility == View.VISIBLE) {
            stopCamera()
            binding.cameraPreview.visibility = View.INVISIBLE
            binding.scanInstructionArea.visibility = View.GONE
            binding.initialUi.visibility = View.VISIBLE
            isProcessing = false
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scope.cancel()
    }
}

@Composable
fun ExitConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
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
                    text = "Salir",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Estas a punto de salir de la aplicación, ¿estas seguro?",
                    fontSize = 18.sp,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE0E0E6),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Cancelar",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF66BB6A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Salir",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
