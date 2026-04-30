package com.project.ava

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
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

    // Estados: null → "loading" → "argyro" → "chat"
    private var composeScreenState by mutableStateOf<String?>(null)
    private var currentData: CategoryWithQuestions? = null
    private var showExitDialog by mutableStateOf(false)
    private var isPermanentlyDenied by mutableStateOf(false)

    // Pregunta pre-seleccionada desde la pantalla AR giroscopio
    // Si es no-null cuando se abre ChatScreen, se añade automáticamente al historial
    private var preSelectedQuestion by mutableStateOf<Question?>(null)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isPermanentlyDenied = false
            hidePermissionDeniedWarning()
            startScanning()
        } else {
            isPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.CAMERA
            )
            showPermissionDeniedWarning()
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
            val data   = currentData

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
                // ── 1. Pantalla de carga ──────────────────────────────────────
                "loading" -> {
                    LoadingScreen(
                        onBack = { resetToInitial() },
                        onHelp = {
                            Toast.makeText(
                                this@MainActivity,
                                "Cargando recursos...",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }

                // ── 3. Selección AR con giroscopio ────────────────────────────
                "argyro" -> {
                    if (data != null) {
                        ARGyroscopeScreen(
                            questions = data.questions,
                            onQuestionSelected = { question ->
                                // Guarda la pregunta elegida y va al chat
                                preSelectedQuestion = question
                                composeScreenState = "chat"
                            },
                            onBack = { resetToInitial() }
                        )
                    }
                }

                // ── 4. Chat con historial ─────────────────────────────────────
                "chat" -> {
                    if (data != null) {
                        ChatScreen(
                            categoryTitle     = data.category.title,
                            questions         = data.questions,
                            initialQuestion   = preSelectedQuestion,
                            onBack            = { resetToInitial() },
                            onHelp            = {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Ayuda activa.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }
        }

        binding.composeView.visibility = View.GONE
        binding.initialUi.visibility   = View.VISIBLE

        binding.btnMenu.setOnClickListener {
            binding.composeView.visibility = View.VISIBLE
            showExitDialog = true
        }
        binding.btnScan.setOnClickListener { checkCameraPermission() }

        setupPermissionWarning()
    }

    private fun setupPermissionWarning() {
        binding.permissionDeniedComposeView.setContent {
            PermissionDeniedWarning(
                isPermanentlyDenied = isPermanentlyDenied,
                onRetry             = { checkCameraPermission() },
                onOpenSettings      = { openAppSettings() }
            )
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    private fun showPermissionDeniedWarning() {
        binding.cameraPreview.visibility           = View.INVISIBLE
        binding.scanInstructionArea.visibility     = View.GONE
        binding.scanLine.visibility                = View.GONE
        binding.whiteDot.visibility                = View.GONE
        binding.permissionDeniedComposeView.visibility = View.VISIBLE
    }

    private fun hidePermissionDeniedWarning() {
        binding.permissionDeniedComposeView.visibility = View.GONE
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            isPermanentlyDenied = false
            hidePermissionDeniedWarning()
            startScanning()
        } else {
            isPermanentlyDenied =
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.CAMERA
                ) && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScanning() {
        binding.permissionDeniedComposeView.visibility = View.GONE
        binding.cameraPreview.visibility               = View.VISIBLE
        binding.scanInstructionArea.visibility         = View.VISIBLE
        binding.scanLine.visibility                    = View.VISIBLE
        binding.whiteDot.visibility                    = View.VISIBLE
        startCamera(binding.cameraPreview)
    }

    private fun startCamera(previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview  = Preview.Builder().build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor) { proxy -> processImage(proxy, scanner) } }

            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        ProcessCameraProvider.getInstance(this).addListener({
            ProcessCameraProvider.getInstance(this).get().unbindAll()
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun processImage(
        imageProxy: ImageProxy,
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner
    ) {
        if (isProcessing) { imageProxy.close(); return }
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }

        InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            .let { img ->
                scanner.process(img)
                    .addOnSuccessListener { barcodes ->
                        barcodes.forEach { barcode ->
                            barcode.rawValue?.let { qr ->
                                if (!isProcessing) { isProcessing = true; handleQrCode(qr) }
                            }
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
            }
    }

    private fun handleQrCode(qrCode: String) {
        scope.launch(Dispatchers.Main) {
            try {
                stopCamera()
                binding.cameraPreview.visibility       = View.INVISIBLE
                binding.scanInstructionArea.visibility = View.GONE

                binding.composeView.visibility = View.VISIBLE
                composeScreenState = "loading"

                val result = repository.getCategoryWithQuestions(qrCode)
                delay(2000)

                if (result != null) {
                    currentData         = result
                    preSelectedQuestion = null   // limpiar selección previa
                    composeScreenState  = "argyro"   // va directo a la pantalla de giroscopio
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Categoría no encontrada: $qrCode",
                        Toast.LENGTH_LONG
                    ).show()
                    resetToInitial()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                resetToInitial()
            }
        }
    }

    private fun resetToInitial() {
        composeScreenState  = null
        currentData         = null
        preSelectedQuestion = null
        binding.composeView.visibility         = View.GONE
        binding.cameraPreview.visibility       = View.INVISIBLE
        binding.scanInstructionArea.visibility = View.GONE
        binding.initialUi.visibility           = View.VISIBLE
        isProcessing = false
    }

    override fun onBackPressed() {
        when (composeScreenState) {
            "argyro" -> resetToInitial()
            "chat"   -> resetToInitial()
            else     -> if (binding.cameraPreview.visibility == View.VISIBLE) {
                stopCamera()
                binding.cameraPreview.visibility       = View.INVISIBLE
                binding.scanInstructionArea.visibility = View.GONE
                binding.initialUi.visibility           = View.VISIBLE
                isProcessing = false
            } else super.onBackPressed()
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
            modifier  = Modifier.fillMaxWidth().padding(16.dp),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Salir", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = Color.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Text(
                    "Estas a punto de salir de la aplicación, ¿estas seguro?",
                    fontSize = 18.sp, color = Color.Black,
                    textAlign = TextAlign.Center, lineHeight = 24.sp
                )
                Spacer(Modifier.height(32.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE0E0E6), contentColor = Color.Black),
                        shape    = RoundedCornerShape(12.dp)
                    ) { Text("Cancelar", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF66BB6A), contentColor = Color.White),
                        shape    = RoundedCornerShape(12.dp)
                    ) { Text("Salir", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
