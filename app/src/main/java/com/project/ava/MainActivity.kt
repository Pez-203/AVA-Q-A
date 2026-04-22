package com.project.ava

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.project.ava.data.AppDatabase
import com.project.ava.data.Question
import com.project.ava.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var database: AppDatabase
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var isProcessing = false
    private var bottomSheetDialog: BottomSheetDialog? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getInstance(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.adminTrigger.setOnLongClickListener {
            startActivity(Intent(this, AdminActivity::class.java))
            true
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.cameraPreview.surfaceProvider
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
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                database.categoryDao().getWithQuestionsByQrCode(qrCode)
            }

            if (result != null) {
                showAvaOverlay()
                showBottomSheet(result.category.title, result.questions)
            } else {
                Toast.makeText(this@MainActivity, "Código no registrado", Toast.LENGTH_SHORT).show()
                isProcessing = false
            }
        }
    }

    private fun showAvaOverlay() {
        binding.avaOverlay.alpha = 0f
        binding.avaOverlay.visibility = View.VISIBLE
        binding.avaOverlay.animate()
            .alpha(1f)
            .setDuration(500)
            .start()
        binding.scanInstruction.visibility = View.GONE
    }

    private fun hideAvaOverlay() {
        binding.avaOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                binding.avaOverlay.visibility = View.INVISIBLE
                binding.scanInstruction.visibility = View.VISIBLE
            }
            .start()
    }

    private fun showBottomSheet(title: String, questions: List<Question>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_questions, null)

        view.findViewById<TextView>(R.id.categoryTitle).text = title

        val container = view.findViewById<LinearLayout>(R.id.questionsContainer)
        for (question in questions) {
            val questionView =
                layoutInflater.inflate(R.layout.item_question_answer, container, false)
            val questionText = questionView.findViewById<TextView>(R.id.questionText)
            val answerText = questionView.findViewById<TextView>(R.id.answerText)

            questionText.text = question.questionText
            answerText.text = question.answerText
            answerText.visibility = View.GONE

            questionView.setOnClickListener {
                answerText.visibility =
                    if (answerText.visibility == View.GONE) View.VISIBLE else View.GONE
            }

            container.addView(questionView)
        }

        view.findViewById<MaterialButton>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            hideAvaOverlay()
            isProcessing = false
            bottomSheetDialog = null
        }

        dialog.setContentView(view)
        dialog.show()
        bottomSheetDialog = dialog
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scope.cancel()
    }
}
