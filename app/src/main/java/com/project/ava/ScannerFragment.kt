package com.project.ava

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.project.ava.data.AppDatabase
import com.project.ava.databinding.FragmentScannerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerFragment : Fragment() {
    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var database: AppDatabase
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var isProcessing = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            findNavController().popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = AppDatabase.getInstance(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.cameraCard.clipToOutline = true

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnHelp.setOnClickListener {
            showHelpDialog()
        }

        binding.btnScan.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnRetry.setOnClickListener {
            binding.errorOverlay.visibility = View.GONE
            isProcessing = false
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
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
                viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
            )
        }, ContextCompat.getMainExecutor(requireContext()))
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
                showStatus(getString(R.string.qr_success), isError = false)
                binding.root.postDelayed({
                    if (isAdded) {
                        val bundle = bundleOf("qrCode" to qrCode)
                        findNavController().navigate(R.id.action_scanner_to_loading, bundle)
                    }
                }, 800)
            } else {
                binding.errorOverlay.visibility = View.VISIBLE
            }
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        binding.statusCard.visibility = View.VISIBLE
        binding.statusText.text = message
        if (isError) {
            binding.statusCard.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.yellow_accent)
            )
            binding.statusText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.black)
            )
        } else {
            binding.statusCard.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.green_surface)
            )
            binding.statusText.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.green_primary)
            )
        }
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.help_title))
            .setMessage(getString(R.string.help_message))
            .setPositiveButton(getString(R.string.btn_accept), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        scope.cancel()
        _binding = null
    }
}
