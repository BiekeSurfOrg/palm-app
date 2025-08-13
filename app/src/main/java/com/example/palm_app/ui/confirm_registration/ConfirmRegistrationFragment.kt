package com.example.palm_app.ui.confirm_registration

import android.Manifest
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
                // Pass the scannedQrCodeContent as the qrData argument
                val action = ConfirmRegistrationFragmentDirections.actionConfirmRegistrationToBleAdvertising(qrData)
                findNavController().navigate(action)
            } ?: run {
                // Handle the case where scannedQrCodeContent is null,
                // for example, by showing a Toast message.
                Toast.makeText(requireContext(), "QR Code not scanned yet", Toast.LENGTH_SHORT).show()
            }
        }
        val scannedQrContent = view.findViewById<TextView>(R.id.scanned_qr_content)
        var isExpanded = false

        scannedQrContent.setOnClickListener {
            isExpanded = !isExpanded
            if (isExpanded) {
                scannedQrContent.maxLines = Integer.MAX_VALUE
                scannedQrContent.ellipsize = null
            } else {
                scannedQrContent.maxLines = 1
                scannedQrContent.ellipsize = TextUtils.TruncateAt.END
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
                    // Update the member variable as soon as the callback is received.
                    scannedQrCodeContent = qrContent // Store the content immediately
                    Log.d(TAG, "scannedQrCodeContent updated to: $qrContent")

                    // Now, attempt to update the UI if the view is still available.
                    activity?.runOnUiThread {
                        _binding?.let { safeBinding ->
                            safeBinding.scannedQrContent.text = qrContent // Update UI
                            Log.d(TAG, "UI TextView updated with: $qrContent")
                        } ?: run {
                            // This block executes if _binding is null.
                            Log.w(TAG, "Binding is null, cannot update UI TextView. View might be destroyed, but scannedQrCodeContent is already updated.")
                        }
                    }
                })
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll() // Unbind use cases before rebinding
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e("ConfirmRegistration", "Use case binding failed", exc)
            Toast.makeText(requireContext(), "Failed to start camera", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private const val TAG = "ConfirmRegistrationFragment"
    }
}



// Analyzer class for ML Kit Barcode Scanning
class BarcodeAnalyzer(private val onBarcodeScanned: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    private val scanner = BarcodeScanning.getClient(options)
    // Removed: private val varisProcessing = false // This variable was not used and could be a typo for "isProcessing"
    // Consider adding a flag like `private var isProcessing = false` if you need to prevent concurrent processing.

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            // Optional: Implement a flag to prevent processing multiple frames at once if needed
            // if (isProcessing) {
            //     imageProxy.close()
            //     return
            // }
            // isProcessing = true

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { rawValue ->
                            onBarcodeScanned(rawValue)
                            // Optional: If you only need one scan, you might stop further processing here.
                            // scanner.close() // Close the scanner if no longer needed.
                        }
                    }
                }
                .addOnFailureListener {
                    // Handle any errors here, e.g., log them
                    Log.e("BarcodeAnalyzer", "Barcode scanning failed", it)
                }
                .addOnCompleteListener {
                    // It's important to close the ImageProxy otherwise the camera will stop producing images.
                    imageProxy.close()
                    // Optional: Reset processing flag
                    // isProcessing = false
                }
        } else {
            // If mediaImage is null, close the ImageProxy and return.
            imageProxy.close()
        }
    }
}

