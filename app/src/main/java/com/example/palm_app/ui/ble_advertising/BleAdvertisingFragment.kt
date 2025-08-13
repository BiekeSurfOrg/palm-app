package com.example.palm_app.ui.ble_advertising

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.palm_app.R
import java.nio.charset.StandardCharsets
import java.util.UUID

class BleAdvertisingFragment : Fragment() {

    private val PALMKI_SERVICE_UUID_INSTANCE = UUID.fromString("e2a2b8e0-0b6c-4b6d-8868-c2b53f6c8d7b")
    private val QR_DATA_CHARACTERISTIC_UUID_INSTANCE = UUID.fromString("c3b3c9f0-1c7d-4e7e-8a8b-9e0f1d0a2b3c")

    private var _binding: View? = null // Using View directly as we only need specific views
    private lateinit var qrDataDisplayTextView: TextView
    private lateinit var advertiseButton: Button
    private lateinit var nextToGattCheckButton: Button

    private val args: BleAdvertisingFragmentArgs by navArgs()

    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isAdvertising = false
    private val MANUFACTURER_ID = 0xFFFF
    private val ADVERTISING_DATA = "PALMKI"

    private var gattServer: BluetoothGattServer? = null
    private var connectedDevice: BluetoothDevice? = null
    private var qrDataForGatt: String? = null // To store the qrContent

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.i(TAG, "BLE Advertising started successfully.")
            isAdvertising = true
            updateButtonUI()
            Toast.makeText(context, "Advertising started", Toast.LENGTH_SHORT).show()
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "BLE Advertising onStartFailure: $errorCode")
            isAdvertising = false
            updateButtonUI()
            var reason = "Unknown error"
            when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> reason = "Data too large"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> reason = "Too many advertisers"
                ADVERTISE_FAILED_ALREADY_STARTED -> reason = "Already started"
                ADVERTISE_FAILED_INTERNAL_ERROR -> reason = "Internal error"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> reason = "Feature unsupported"
            }
            Toast.makeText(context, "Advertising failed to start: $reason", Toast.LENGTH_LONG).show()
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (device == null) return

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Device connected: ${device.address}")
                connectedDevice = device
                activity?.runOnUiThread {
                    Toast.makeText(context, "Device connected: ${device.address}", Toast.LENGTH_SHORT).show()
//                    if (isAdded && findNavController().currentDestination?.id == R.id.nav_ble_advertising) {
//                        findNavController().navigate(R.id.action_bleAdvertisingFragment_to_gattCheckFragment)
//                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Device disconnected: ${device.address}")
                connectedDevice = null
                activity?.runOnUiThread {
                    Toast.makeText(context, "Device disconnected: ${device.address}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            super.onServiceAdded(status, service)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Service added successfully: ${service?.uuid}")
            } else {
                Log.w(TAG, "Failed to add service. Status: $status")
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if (device == null || characteristic == null) {
                Log.w(TAG, "onCharacteristicReadRequest: device or characteristic is null. Cannot process read request.")
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                    Log.d(TAG, "Sent GATT_FAILURE because device or characteristic was null.")
                } else {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission not granted, cannot send GATT_FAILURE response for null device/characteristic.")
                }
                return
            }

            Log.i(TAG, "GATT Read Request Details: Device=${device.address}, RequestId=$requestId, Offset=$offset, CharUUID=${characteristic.uuid}")

            if (QR_DATA_CHARACTERISTIC_UUID_INSTANCE == characteristic.uuid) {
                val dataStringToSend = qrDataForGatt ?: "" // Default to empty string if null
                val valueToSend = dataStringToSend.toByteArray(StandardCharsets.UTF_8)

                Log.i(TAG, "GATT Read: Preparing to send data for QR_DATA_CHARACTERISTIC.")
                Log.d(TAG, "GATT Read: String data prepared: '$dataStringToSend'")
                Log.d(TAG, "GATT Read: Byte array size: ${valueToSend.size} bytes.")
                Log.d(TAG, "GATT Read: Offset requested: $offset")

                val effectiveValueToSend = if (offset > valueToSend.size) byteArrayOf() else valueToSend.copyOfRange(offset, valueToSend.size)
                Log.d(TAG, "GATT Read: Effective byte array size after offset: ${effectiveValueToSend.size} bytes.")


                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset, // The offset is handled by the framework when passing the full byte array,
                        // but for clarity, we're passing the already offsetted/ranged array.
                        // Or, more typically: send the full 'valueToSend' and the framework handles the offset.
                        // Let's use the common practice: send 'valueToSend' directly.
                        effectiveValueToSend // No, the Android framework expects the *full* value and it handles the offset.
                        // The `offset` parameter in sendResponse is for your reference or if you were to send a chunk from a larger source.
                        // The actual bytes sent will be from `valueToSend[offset]` onwards.
                        // Let's stick to the previous correct implementation for `sendResponse` value.
                    )
                    Log.i(TAG, "GATT Read: Called sendResponse with GATT_SUCCESS for QR_DATA_CHARACTERISTIC.")
                    // For more detailed logging of actual bytes, you could convert effectiveValueToSend to hex string, but it can be verbose.
                    // Log.v(TAG, "GATT Read: Effective bytes (hex): ${effectiveValueToSend.toHexString()}") // Requires a helper toHexString()
                } else {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission not granted. Cannot send GATT_SUCCESS response for QR_DATA_CHARACTERISTIC.")
                }
            } else {
                Log.w(TAG, "GATT Read: Request for unknown/unhandled characteristic: ${characteristic.uuid}")
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                    Log.d(TAG, "Sent GATT_READ_NOT_PERMITTED for unknown characteristic.")
                } else {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission not granted, cannot send GATT_READ_NOT_PERMITTED response.")
                }
            }
        }



        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            Log.w(TAG, "Write request received for ${characteristic?.uuid}, but not implemented.")
            if (responseNeeded) {
                 if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
                }
            }
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor?) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            Log.w(TAG, "Descriptor read request for ${descriptor?.uuid}, but not implemented.")
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            Log.w(TAG, "Descriptor write request for ${descriptor?.uuid}, but not implemented.")
            if (responseNeeded) {
                 if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
                }
            }
        }
    }


    private val requestBluetoothPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach { (permission, isGranted) ->
                Log.d(TAG, "Permission $permission granted: $isGranted")
                if (!isGranted) {
                    allGranted = false
                }
            }
            if (allGranted) {
                Log.d(TAG, "All Bluetooth permissions granted.")
                setupBluetooth() // This will also try to open GATT server
                // If user intended to start advertising, they might need to click again,
                // or we could add a flag to auto-start if permissions were pending.
            } else {
                Log.w(TAG, "One or more Bluetooth permissions were denied.")
                Toast.makeText(context, "Bluetooth permissions are required.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ble_advertising, container, false)
        _binding = view
        qrDataDisplayTextView = view.findViewById(R.id.qr_data_display_textview)
        advertiseButton = view.findViewById(R.id.advertise_button)
        nextToGattCheckButton = view.findViewById(R.id.next_to_gatt_check_button)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "BleAdvertisingFragment onViewCreated")

        val receivedQrData = args.qrData
        qrDataForGatt = receivedQrData // Store for GATT server
        Log.d(TAG, "Attempting to display QR Data. Value is: '$receivedQrData'")

        if (receivedQrData != null && receivedQrData.isNotEmpty()) {
            qrDataDisplayTextView.text = receivedQrData
            Log.d(TAG, "TextView updated with QR Data: $receivedQrData")
        } else if (receivedQrData == null) {
            qrDataDisplayTextView.text = "No QR data received (was null)"
            Log.d(TAG, "TextView updated: QR Data was null.")
        } else { // receivedQrData is empty string ""
            qrDataDisplayTextView.text = "No QR data received (was empty string)"
            Log.d(TAG, "TextView updated: QR Data was an empty string.")
        }

        if (checkAndRequestPermissions()) {
            setupBluetooth()
        }

        advertiseButton.setOnClickListener {
            if (isAdvertising) {
                stopBleAdvertising()
            } else {
                startBleAdvertising()
            }
        }

        nextToGattCheckButton.setOnClickListener {
            if (isAdvertising) {
                stopBleAdvertising() // Stop advertising before navigating
            }
            // Ensure GATT server is closed if we are navigating away from its primary screen
            // closeGattServer() // Or manage its lifecycle more globally if needed across screens
            Log.i(TAG, "Navigating to GATT check screen.")
            if (isAdded && findNavController().currentDestination?.id == R.id.nav_ble_advertising) {
                 findNavController().navigate(R.id.action_bleAdvertisingFragment_to_gattCheckFragment)
            }
        }
        updateButtonUI() // Set initial button state
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else { // Below Android 12
            // BLUETOOTH and BLUETOOTH_ADMIN are usually sufficient and granted at install time from Manifest
            // For robust pre-S BLE, ACCESS_FINE_LOCATION might also be needed depending on operations and target SDKs.
            // However, modern guidance focuses on S+ permissions.
        }

        return if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $permissionsToRequest")
            requestBluetoothPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            false // Permissions are being requested
        } else {
            Log.d(TAG, "All necessary permissions already granted.")
            true // Permissions already granted
        }
    }

    private fun setupBluetooth() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        if (bluetoothManager == null) {
            Log.e(TAG, "BluetoothManager not available.")
            Toast.makeText(context, "BluetoothManager not available.", Toast.LENGTH_LONG).show()
            advertiseButton.isEnabled = false
            return
        }
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(context, "Bluetooth is not available or not enabled.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Bluetooth adapter is null or disabled.")
            advertiseButton.isEnabled = false
            return
        }

        bluetoothLeAdvertiser = bluetoothAdapter!!.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Toast.makeText(context, "BLE Advertising not supported on this device.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "BluetoothLeAdvertiser is null.")
            advertiseButton.isEnabled = false
        } else {
            advertiseButton.isEnabled = true
             // Try to open GATT server now that we know BLE is supported and adapter is enabled
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                openGattServer(bluetoothManager)
            } else {
                Log.w(TAG, "BLUETOOTH_CONNECT permission not granted at setupBluetooth, GATT server not opened yet.")
                 // We might need to request permissions again or prompt user
                 checkAndRequestPermissions() // Re-check, might trigger request if not already done by onViewCreated
            }
        }
    }

    private fun openGattServer(bluetoothManager: BluetoothManager) {
        if (gattServer != null) {
            Log.d(TAG, "GATT Server already open.")
            return
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission NOT granted. Cannot open GATT server.")
            // Optionally, re-trigger permission request if appropriate context
            // checkAndRequestPermissions()
            return
        }
        try {
            gattServer = bluetoothManager.openGattServer(requireContext(), gattServerCallback)
            if (gattServer == null) {
                Log.e(TAG, "Unable to open GATT server (openGattServer returned null).")
                Toast.makeText(context, "Failed to open GATT server.", Toast.LENGTH_SHORT).show()
                return
            }
            Log.i(TAG, "GATT Server opened. Adding Palmki service.")
            addPalmkiService()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException opening GATT server. BLUETOOTH_CONNECT permission issue?", e)
            Toast.makeText(context, "Permission error opening GATT server.", Toast.LENGTH_LONG).show()
        }
    }

    private fun addPalmkiService() {
        if (gattServer == null) {
            Log.e(TAG, "GATT server is null, cannot add service.")
            return
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission NOT granted. Cannot add service to GATT server.")
            return
        }

        val service = BluetoothGattService(PALMKI_SERVICE_UUID_INSTANCE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val qrCharacteristic = BluetoothGattCharacteristic(
            QR_DATA_CHARACTERISTIC_UUID_INSTANCE,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(qrCharacteristic)

        try {
            gattServer?.addService(service)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException adding service. BLUETOOTH_CONNECT permission issue?", e)
        }
    }

    private fun startBleAdvertising() {
        if (!checkAndRequestPermissions()) {
            Toast.makeText(context, "Requesting necessary Bluetooth permissions.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "Permissions not granted yet. Advertising not started.")
            return
        }

        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "bluetoothLeAdvertiser is null, attempting to re-initialize.")
            setupBluetooth() // Try to re-initialize (this also tries to open GATT server)
            if (bluetoothLeAdvertiser == null) {
                 Log.e(TAG, "bluetoothLeAdvertiser is still null after re-init. Cannot start advertising.")
                 Toast.makeText(context, "Cannot start advertising. Advertiser not available.", Toast.LENGTH_LONG).show()
                 return
            }
        }

        // Ensure GATT server is open if it wasn't due to permissions being granted just now or a previous failure
        if (gattServer == null && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            openGattServer(bluetoothManager)
        }
         if (gattServer == null) {
            Log.e(TAG, "GATT Server is not open. Cannot ensure service is advertised.")
            // Depending on requirements, you might prevent advertising or advertise without the service UUID.
        }


        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val manufacturerData = ADVERTISING_DATA.toByteArray(StandardCharsets.UTF_8)
        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(MANUFACTURER_ID, manufacturerData)
            .addServiceUuid(ParcelUuid(PALMKI_SERVICE_UUID_INSTANCE)) // Advertise our service UUID

        if (manufacturerData.size + ParcelUuid(PALMKI_SERVICE_UUID_INSTANCE).toString().toByteArray().size > 25) { // Rough check
            Log.w(TAG, "Advertising data might be too large with service UUID.")
        }
        val advertiseData = dataBuilder.build()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_ADVERTISE permission not granted for startAdvertising.")
            Toast.makeText(context, "Bluetooth Advertise permission needed.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException on startAdvertising. BLUETOOTH_ADVERTISE permission issue?", e)
            Toast.makeText(context, "Permission error on starting advertising.", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopBleAdvertising() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.w(TAG, "BLUETOOTH_ADVERTISE permission not granted for stop. Proceeding with stop attempt if advertiser exists.")
        }

        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            Log.i(TAG, "BLE Advertising stopped.")
        } catch (e: SecurityException) {
             Log.e(TAG, "SecurityException on stopAdvertising. BLUETOOTH_ADVERTISE permission issue?", e)
        } finally {
            isAdvertising = false
            updateButtonUI()
            Toast.makeText(context, "Advertising stopped", Toast.LENGTH_SHORT).show()
        }
        // Decide if GATT server should be closed when advertising stops.
        // For now, it's closed in onDestroyView. If you want to close it here:
        // closeGattServer()
    }

    private fun closeGattServer() {
        if (gattServer == null) return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot close GATT server, BLUETOOTH_CONNECT permission missing.")
            // Even if permission is missing, gattServer might be non-null but unusable.
            // Setting to null helps prevent further attempts.
            gattServer = null
            return
        }
        try {
            connectedDevice = null
            gattServer?.close()
            Log.i(TAG, "GATT Server closed.")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException closing GATT server. BLUETOOTH_CONNECT permission issue?", e)
        } finally {
            gattServer = null
        }
    }


    private fun updateButtonUI() {
        if (isAdvertising) {
            advertiseButton.text = getString(R.string.stop_advertising_text)
            advertiseButton.setBackgroundColor(Color.RED)
        } else {
            advertiseButton.text = getString(R.string.start_advertising_text)
            advertiseButton.setBackgroundColor(Color.GREEN)
        }
        // Enable next button only if not advertising? Or always enabled?
        // nextToGattCheckButton.isEnabled = !isAdvertising
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
        if (isAdvertising && bluetoothLeAdvertiser != null) {
            try {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                }
                Log.i(TAG, "Advertising stopped in onDestroyView.")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException on stopAdvertising in onDestroyView.", e)
            }
        }

        closeGattServer() // Ensure GATT server is closed
        _binding = null
        Log.d(TAG, "onDestroyView called.")
    }

    companion object {
        private const val TAG = "BleAdvertisingFragment"
    }
}
