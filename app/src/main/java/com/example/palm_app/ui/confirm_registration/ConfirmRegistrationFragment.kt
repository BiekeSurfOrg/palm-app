package com.example.palm_app.ui.confirm_registration

import android.Manifest
import android.content.Context // Added for SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.palm_app.databinding.FragmentConfirmRegistrationBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.ImageProxy
import android.widget.TextView
import com.example.palm_app.R
import android.text.TextUtils

class ConfirmRegistrationFragment : Fragment() {

    private var _binding: FragmentConfirmRegistrationBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private var scannedQrCodeContent: String? = null

    private val cameraPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to scan QR codes", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfirmRegistrationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionRequest.launch(Manifest.permission.CAMERA)
        }

        binding.nextButtonConfirm.setOnClickListener {
            scannedQrCodeContent?.let { qrData ->
                val action = ConfirmRegistrationFragmentDirections.actionConfirmRegistrationToBleAdvertising(qrData)
                findNavController().navigate(action)
            } ?: run {
                Toast.makeText(requireContext(), "QR Code not scanned yet", Toast.LENGTH_SHORT).show()
            }
        }
        val scannedQrContentTextView = view.findViewById<TextView>(R.id.scanned_qr_content) // Renamed for clarity
        var isExpanded = false

        scannedQrContentTextView.setOnClickListener {
            isExpanded = !isExpanded
            if (isExpanded) {
                scannedQrContentTextView.maxLines = Integer.MAX_VALUE
                scannedQrContentTextView.ellipsize = null
            } else {
                scannedQrContentTextView.maxLines = 1
                scannedQrContentTextView.ellipsize = TextUtils.TruncateAt.END
            }
        }
    }

    private fun startCamera() {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { qrContent ->
                    // qrContent is available from the BarcodeAnalyzer.

                    // <<< --- MODIFICATION START: Save to SharedPreferences --- >>>
                    saveStringToPrefs(KEY_PALM_HASH, qrContent)
                    Log.d(TAG, "Saved QR content to SharedPreferences with key '$KEY_PALM_HASH': $qrContent")
                    // <<< --- MODIFICATION END --- >>>

                    scannedQrCodeContent = qrContent // Store the content immediately
                    Log.d(TAG, "scannedQrCodeContent updated to: $qrContent")

                    activity?.runOnUiThread {
                        _binding?.let { safeBinding ->
                            safeBinding.scannedQrContent.text = qrContent // Update UI
                            Log.d(TAG, "UI TextView updated with: $qrContent")
                        } ?: run {
                            Log.w(TAG, "Binding is null, cannot update UI TextView. View might be destroyed.")
                        }
                    }
                })
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc) // Use TAG for consistency
            Toast.makeText(requireContext(), "Failed to start camera", Toast.LENGTH_SHORT).show()
        }
    }

    // <<< --- MODIFICATION START: SharedPreferences helper function --- >>>
    private fun saveStringToPrefs(key: String, value: String) {
        val sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString(key, value)
            apply()
        }
    }
    // <<< --- MODIFICATION END --- >>>

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private const val TAG = "ConfirmRegistrationFragment"
        // <<< --- MODIFICATION START: SharedPreferences constants --- >>>
        const val PREFS_NAME = "PalmAppPrefs" // Same as HomeFragment
        const val KEY_PALM_HASH = "palm_hash"  // Key for storing the QR content
        // <<< --- MODIFICATION END --- >>>
    }
}

// Analyzer class for ML Kit Barcode Scanning
class BarcodeAnalyzer(private val onBarcodeScanned: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    private val scanner = BarcodeScanning.getClient(options)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { rawValue ->
                            onBarcodeScanned(rawValue)
                            // Optional: If you only need one scan, you might stop further processing here.
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("BarcodeAnalyzer", "Barcode scanning failed", it)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
