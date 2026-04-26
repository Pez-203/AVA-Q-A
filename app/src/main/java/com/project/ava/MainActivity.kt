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
            finishAffinity()
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
                    Toast.makeText(this@MainActivity, "Código QR no reconocido: $qrCode", Toast.LENGTH_LONG).show()
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
