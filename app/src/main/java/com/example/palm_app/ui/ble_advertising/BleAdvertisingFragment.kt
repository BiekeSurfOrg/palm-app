package com.example.palm_app.ui.ble_advertising

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.palm_app.R
import java.nio.charset.StandardCharsets
import com.example.palm_app.util.PermissionHelper
import com.example.palm_app.ble.BlePeripheralService

class BleAdvertisingFragment : Fragment() {


    private var _binding: View? = null // Using View directly as we only need specific views
    private lateinit var qrDataDisplayTextView: TextView
    private lateinit var advertiseButton: Button
    private val args: BleAdvertisingFragmentArgs by navArgs()

    private var isAdvertising = false

    private var qrDataForGatt: String? = null // To store the qrContent

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ble_advertising, container, false)
        _binding = view
        qrDataDisplayTextView = view.findViewById(R.id.qr_data_display_textview)
        advertiseButton = view.findViewById(R.id.advertise_button)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "BleAdvertisingFragment onViewCreated")

        // 1) Show the QR/debug text like before
        val receivedQrData = args.qrData
        qrDataForGatt = receivedQrData
        when {
            !receivedQrData.isNullOrEmpty() -> {
                qrDataDisplayTextView.text = receivedQrData
                Log.d(TAG, "TextView updated with QR Data: $receivedQrData")
            }
            receivedQrData == null -> {
                qrDataDisplayTextView.text = "No QR data received (was null)"
                Log.d(TAG, "TextView updated: QR Data was null.")
            }
            else -> {
                qrDataDisplayTextView.text = "No QR data received (was empty string)"
                Log.d(TAG, "TextView updated: QR Data was an empty string.")
            }
        }

        // 2) Request permissions (Nearby Devices + Notifications on API 33+) using PermissionHelper
        val permissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            if (PermissionHelper.allGranted(requireContext())) {
                // Start the foreground BLE service headless
                if (!isAdvertising) startPeripheralService(qrDataForGatt)

            } else {
                Toast.makeText(context, "Bluetooth permissions are required.", Toast.LENGTH_LONG).show()
            }
        }

        if (!PermissionHelper.allGranted(requireContext())) {
            permissionsLauncher.launch(PermissionHelper.required())
        } else {
            // Already granted earlier — just start service
            if (!isAdvertising) startPeripheralService(qrDataForGatt)

        }

        // 3) Start/Stop button now toggles the service (no Fragment-level BLE anymore)
        advertiseButton.setOnClickListener {
            if (isAdvertising) {
                stopPeripheralService()
            } else {
                if (PermissionHelper.allGranted(requireContext())) {
                    if (!isAdvertising) startPeripheralService(qrDataForGatt)

                } else {
                    permissionsLauncher.launch(PermissionHelper.required())
                }
            }
        }

        // 4) Update UI once
        updateButtonUI()
    }

    private fun startPeripheralService(payloadStr: String?) {
        val bytes = (payloadStr ?: "").toByteArray(StandardCharsets.UTF_8)
        BlePeripheralService.start(requireContext(), bytes)
        isAdvertising = true  // reuse your flag to mean "service running"
        updateButtonUI()
        Toast.makeText(context, "BLE peripheral running in background", Toast.LENGTH_SHORT).show()
    }

    private fun stopPeripheralService() {
        BlePeripheralService.stop(requireContext())
        isAdvertising = false
        updateButtonUI()
        Toast.makeText(context, "BLE peripheral stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateButtonUI() {
        if (isAdvertising) {
            advertiseButton.text = getString(R.string.stop_advertising_text)
            advertiseButton.setBackgroundColor(Color.RED)
        } else {
            advertiseButton.text = getString(R.string.start_advertising_text)
            advertiseButton.setBackgroundColor(Color.GREEN)
        }
    }

    override fun onStop() {
        super.onStop()
        // Consider stopping advertising here if fragment is not visible to save resources,
        // but ensure it doesn't conflict with ongoing connections if desired.
        // if (isAdvertising) {
        //    stopBleAdvertising()
        // }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called.")
        _binding = null
    }

    companion object {
        private const val TAG = "BleAdvertisingFragment"
    }
}
