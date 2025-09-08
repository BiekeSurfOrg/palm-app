package com.example.palm_app.ui.ble_advertising

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.example.palm_app.R
import com.example.palm_app.util.PermissionHelper
import com.example.palm_app.ble.BlePeripheralService
import com.example.palm_app.network.ApiService
import com.example.palm_app.ui.home.HomeFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONException
import android.graphics.Bitmap
import android.widget.ImageView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

class BleAdvertisingFragment : Fragment() {

    private var _binding: View? = null
    private lateinit var qrDataDisplayTextView: TextView
    private lateinit var advertiseButton: Button
    private lateinit var connectedDeviceAddressTextView: TextView
    private lateinit var userIdTextView: TextView // Added for User ID
    private lateinit var manufacturerPayloadTextView: TextView
    private lateinit var manufacturerPayloadASCIITextView: TextView
    //private val args: BleAdvertisingFragmentArgs by navArgs()
    private var receiverToken: Intent? = null

    private lateinit var ble_qr_code_imageview: ImageView
    private var isAdvertising = false
    private var qrDataForGatt: String? = null

    // Register once; reuse
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (PermissionHelper.allGranted(requireContext())) {
            if (!isAdvertising) startPeripheralService(qrDataForGatt)
        } else {
            toast("Bluetooth permissions are required.")
        }
    }

    private val gattConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "gattConnectionReceiver onReceive - Action: ${intent?.action}")
            when (intent?.action) {
//                ACTION_ADVERTISING_STARTED -> {
//                    val displayId = intent.getStringExtra(EXTRA_DISPLAY_ID) ?: "—"
//                    connectedDeviceAddressTextView.text = "Advertising • ID: $displayId"
//                    Log.d(TAG, "Advertising started, displayId=$displayId")
//                    Log.d(TAG, "ACTION_ADVERTISING_STARTED")
//                    val deviceAddress = intent.getStringExtra(ACTION_ADVERTISING_STARTED)
//                    Log.d(TAG, "ACTION_ADVERTISING_STARTED received, address: $deviceAddress")
//                    connectedDeviceAddressTextView.text = "Device: ${deviceAddress ?: "None"}"
//                }
//                ACTION_DEVICE_CONNECTED -> {
//                    Log.d(TAG, "ACTION_DEVICE_CONNECTED")
//                    val deviceAddress = intent.getStringExtra(ACTION_DEVICE_CONNECTED)
//                    Log.d(TAG, "ACTION_DEVICE_CONNECTED received, address: $deviceAddress")
//                    connectedDeviceAddressTextView.text = "Device: ${deviceAddress ?: "None"}"
//                }
//                ACTION_DEVICE_DISCONNECTED -> {
//                    Log.d(TAG, "ACTION_DEVICE_DISCONNECTED")
//                    val deviceAddress = intent.getStringExtra(ACTION_DEVICE_DISCONNECTED)
//                    connectedDeviceAddressTextView.text = "Device: ${deviceAddress ?: "None"}"
//                    Log.d(TAG, "ACTION_DEVICE_DISCONNECTED $deviceAddress received")
//                    //connectedDeviceAddressTextView.text = "Device: None"
//                }
                EXTRA_DEVICE_ADDRESS -> {
                    Log.d(TAG, "EXTRA_DEVICE_ADDRESS")
                    val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                    connectedDeviceAddressTextView.text = "Device: ${deviceAddress ?: "None"}"
                    Log.d(TAG, "EXTRA_DEVICE_ADDRESS $deviceAddress received")
                    //connectedDeviceAddressTextView.text = "Device: None"
                }
                ACTION_MANUFACTURER_DATA_READY -> {
                    val payload = intent.getStringExtra(EXTRA_MANUFACTURER_DATA_STRING)
                    Log.d(TAG, "ACTION_MANUFACTURER_DATA_READY received, payload: $payload")
                    manufacturerPayloadTextView.text = "Manufacturer Payload: ${payload ?: "N/A"}"
                    // payload transform to : "Manufacturer Data ASCII: Version: 1, Tag: PALMKI, Counter: 53975"
                    manufacturerPayloadASCIITextView.text = if (payload != null) {
                        val bytesStr = payload.split(":")
                        if (bytesStr.size >= 9) { // Version (1) + Tag (6) + Counter (2) = 9 bytes
                            try {
                                val version = bytesStr[0].toInt(16)
                                val tag = bytesStr.subList(1, 7).joinToString("") {
                                    it.toInt(16).toChar().toString()
                                }
                                // Counter is 2 bytes, Little Endian (LSB first in mfg_bytes[7], then MSB in mfg_bytes[8])
                                // So, bytesStr[7] is LSB_hex, bytesStr[8] is MSB_hex
                                val counterLsbValue = bytesStr[7].toInt(16)
                                val counterMsbValue = bytesStr[8].toInt(16)
                                val counter = (counterMsbValue shl 8) or counterLsbValue // Correct calculation: (MSB * 256) + LSB


                                "Manufacturer Data ASCII: Version: $version, Tag: $tag, Counter: $counter"
                            } catch (e: NumberFormatException) {
                                Log.e(TAG, "Error parsing manufacturer payload hex components: $payload", e)
                                "Manufacturer Data ASCII: Format Error"
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing manufacturer payload: $payload", e)
                                "Manufacturer Data ASCII: Processing Error"
                            }
                        } else {
                            Log.w(TAG, "Manufacturer payload too short for parsing: $payload")
                            "Manufacturer Data ASCII: Payload too short"
                        }
                    } else {
                        "Manufacturer Data ASCII: N/A"
                    }
                }
            }
        }
    }

    private fun generateQrCodeBitmap(text: String, width: Int = 400, height: Int = 400): Bitmap? {
        if (text.isBlank()) {
            Log.w(TAG, "QR code generation skipped: text is blank.")
            return null
        }
        val writer = QRCodeWriter()
        return try {
            val bitMatrix = writer.encode(
                text,
                BarcodeFormat.QR_CODE,
                width,
                height,
                mapOf(EncodeHintType.MARGIN to 1) // Keep a small margin
            )
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            Log.d(TAG, "QR code generated successfully for text: $text")
            bmp
        } catch (e: Exception) {
            Log.e(TAG, "Error generating QR code for text: $text", e)
            null
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_ble_advertising, container, false)
        _binding = view
        qrDataDisplayTextView = view.findViewById(R.id.qr_data_display_textview)
        ble_qr_code_imageview = view.findViewById(R.id.ble_qr_code_imageview)
        advertiseButton = view.findViewById(R.id.advertise_button)
        connectedDeviceAddressTextView = view.findViewById(R.id.connectedDeviceAddressTextView)
        userIdTextView = view.findViewById(R.id.userIdTextView) // Initialize userIdTextView
        manufacturerPayloadTextView = view.findViewById(R.id.manufacturerPayloadTextView) // Initialize connectedDeviceManufacturerPayload
        manufacturerPayloadASCIITextView = view.findViewById(R.id.manufacturerPayloadASCIITextView) // Initialize connectedDeviceManufacturerPayloadASCII
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "BleAdvertisingFragment onViewCreated")

        connectedDeviceAddressTextView.text = "Device: None" // Initialize connected device text

        // Kick off a single, structured pipeline
        viewLifecycleOwner.lifecycleScope.launch {
            val palmHash = getStringFromPrefs(KEY_PALM_HASH)
            val userIdString = getStringFromPrefs(KEY_USERID)

            if (userIdString.isNullOrBlank()) {
                Log.w(TAG, "No userId in prefs; cannot fetch identity/ble data")
                userIdTextView.text = "User ID: N/A" // Update TextView
                initUiAfterDataResolved(gattData = null)
                return@launch
            }

            val userId = userIdString.toIntOrNull()
            if (userId == null) {
                Log.e(TAG, "Invalid userId in prefs: $userIdString")
                userIdTextView.text = "User ID: Invalid" // Update TextView
                initUiAfterDataResolved(gattData = null)
                return@launch
            }

            userIdTextView.text = "User ID: $userId" // Update TextView with valid userId

            // 1) Fetch identity (IO thread)
            val identityJson = fetchIdentityData(userId)
            if (identityJson == null) {
                initUiAfterDataResolved(gattData = null)
                return@launch
            }

            // 2) Parse token (Main thread safe; cheap operation)
            val token = extractToken(identityJson)
            if (token.isNullOrBlank()) {
                Log.e(TAG, "Token missing from identity response")
                initUiAfterDataResolved(gattData = null)
                return@launch
            }

            // 3) Post BLE ID to backend (IO)
            val bleIdData = postBleId(token)
            if (bleIdData == null) {
                initUiAfterDataResolved(gattData = null)
                return@launch
            }

            // Persist BLE data (IO)
            withContext(Dispatchers.IO) {
                saveStringToPrefs(KEY_BLE_DATA, bleIdData)
            }

            // 4) Merge with palm hash (on Main; cheap)
            val gattData = mergeJsonStrings(bleIdData, palmHash)

            // 5) Finalize UI and optionally start advertising
            initUiAfterDataResolved(gattData)
        }

        // Toggle button remains the same
        advertiseButton.setOnClickListener {
            if (isAdvertising) {
                stopPeripheralService()
            } else {
                if (PermissionHelper.allGranted(requireContext())) {
                    startPeripheralService(qrDataForGatt)
                } else {
                    permissionsLauncher.launch(PermissionHelper.required())
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        val filter = IntentFilter().apply {
            addAction(ACTION_DEVICE_CONNECTED)
            addAction(ACTION_DEVICE_DISCONNECTED)
            addAction(ACTION_ADVERTISING_STARTED)
            addAction(EXTRA_DEVICE_ADDRESS)
            addAction(ACTION_MANUFACTURER_DATA_READY)
        }
        receiverToken = androidx.core.content.ContextCompat.registerReceiver(
            requireContext(),
            gattConnectionReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // ask the service for the current status (same as you do now)
        val i = Intent(requireContext(), BlePeripheralService::class.java).apply {
            action = ACTION_REQUEST_CONNECTION_STATUS
        }
        requireContext().startService(i)
        Log.d(TAG, "ACTION_REQUEST_CONNECTION_STATUS command sent to service")
    }

//    override fun onResume() {
//        super.onResume()
//        Log.d(TAG, "onResume called")
//        val intentFilter = IntentFilter().apply {
//            addAction(ACTION_DEVICE_CONNECTED)
//            addAction(ACTION_DEVICE_DISCONNECTED)
//        }
//        requireActivity().registerReceiver(gattConnectionReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
//        // Changed to startService:
//        val requestStatusIntent = Intent(requireContext(), BlePeripheralService::class.java)
//        requestStatusIntent.action = ACTION_REQUEST_CONNECTION_STATUS // Ensure this constant matches in BlePeripheralService
//        requireContext().startService(requestStatusIntent)
//        Log.d(TAG, "ACTION_REQUEST_CONNECTION_STATUS command sent to service") // Log message updated
//    }


    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
        requireActivity().unregisterReceiver(gattConnectionReceiver)
    }

    /**
     * Called once the sequential pipeline above completes (success or fail).
     * Updates UI and handles permission/start logic without racing async state.
     */
    private fun initUiAfterDataResolved(gattData: String?) {
        if (!isAdded || view == null) return
        qrDataForGatt = gattData
        qrDataDisplayTextView.text = gattData
        if (gattData != null) {
            val qrBitmap = generateQrCodeBitmap(gattData)
            ble_qr_code_imageview.setImageBitmap(qrBitmap)
            if (qrBitmap == null) {
                Log.w(TAG, "QR Code generation failed or data was blank. ImageView may be blank.")
                // Optionally set a placeholder or error image:
                // ble_qr_code_imageview.setImageResource(R.drawable.your_qr_error_placeholder)
            }
        } else {
            Log.d(TAG, "gattData is null, clearing QR code ImageView.")
            ble_qr_code_imageview.setImageBitmap(null)
            // Optionally set a placeholder:
            // ble_qr_code_imageview.setImageResource(R.drawable.your_qr_placeholder)
        }
        updateButtonUI()

        // Request permissions or start immediately if already granted
        if (!PermissionHelper.allGranted(requireContext())) {
            permissionsLauncher.launch(PermissionHelper.required())
        } else if (!isAdvertising) {
            startPeripheralService(qrDataForGatt)
        }
    }

    /** IO-bound identity fetch; clean suspend with no UI work inside */
    private suspend fun fetchIdentityData(userId: Int): String? = withContext(Dispatchers.IO) {
        when (val result = ApiService.fetchIdentity(userId)) {
            is ApiService.FetchIdentityResult.Success -> {
                val json = result.jsonResponse
                // Save in prefs (still IO)
                saveStringToPrefs(KEY_ID_RESPONSE, json)
                Log.d(TAG, "Fetched identity for userId=$userId")
                json
            }
            is ApiService.FetchIdentityResult.Error -> {
                Log.e(TAG, "fetchIdentity error: ${result.errorMessage}")
                // Main-thread toast
                withContext(Dispatchers.Main) { toast(result.errorMessage) }
                null
            }
        }
    }

    /** IO-bound BLE ID post; returns server JSON or null. */
    private suspend fun postBleId(token: String): String? = withContext(Dispatchers.IO) {
        when (val result = ApiService.postBleId(token)) {
            is ApiService.PostBleIdResult.Success -> {
                Log.d(TAG, "postBleId ok")
                result.jsonResponse
            }
            is ApiService.PostBleIdResult.Error -> {
                Log.e(TAG, "postBleId error: ${result.errorMessage}")
                withContext(Dispatchers.Main) { toast(result.errorMessage) }
                null
            }
        }
    }

    private fun extractToken(identityJson: String): String? =
        try {
            JSONObject(identityJson).optString("token", null).also {
                Log.d(TAG, "Extracted token: ${it?.let { "****" } ?: "null"}")
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Token parse error: ${e.message}")
            null
        }

    private fun mergeJsonStrings(jsonString1: String?, jsonString2: String?): String? {
        val merged = JSONObject()
        if (!jsonString1.isNullOrBlank()) {
            try {
                val o1 = JSONObject(jsonString1)
                o1.keys().forEach { k -> merged.put(k, o1.get(k)) }
            } catch (e: JSONException) {
                Log.e(TAG, "merge json1 error: ${e.message}")
            }
        }
        if (!jsonString2.isNullOrBlank()) {
            try {
                val o2 = JSONObject(jsonString2)
                o2.keys().forEach { k -> merged.put(k, o2.get(k)) }
            } catch (e: JSONException) {
                Log.e(TAG, "merge json2 error: ${e.message}")
            }
        }
        return if (merged.length() == 0) null else merged.toString()
    }

    private fun startPeripheralService(payloadStr: String?) {
        val bytes = (payloadStr ?: "").toByteArray(Charsets.UTF_8)
        BlePeripheralService.start(requireContext(), bytes)
        isAdvertising = true
        updateButtonUI()
        toast("BLE peripheral running in background")
    }

    private fun stopPeripheralService() {
        BlePeripheralService.stop(requireContext())
        isAdvertising = false
        updateButtonUI()
        toast("BLE peripheral stopped")
    }

    private fun updateButtonUI() {
        if (!isAdded) return
        if (isAdvertising) {
            advertiseButton.text = getString(R.string.stop_advertising_text)
            advertiseButton.setBackgroundColor(Color.RED)
        } else {
            advertiseButton.text = getString(R.string.start_advertising_text)
            advertiseButton.setBackgroundColor(Color.GREEN)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called.")
        _binding = null
    }

    // ---- Prefs & utils -------------------------------------------------------

    private fun saveStringToPrefs(key: String, value: String) {
        // Prefs write is cheap but still push to IO for correctness on older devices
        // Caller ensures proper dispatcher.
        val sp = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(key, value).apply()
        Log.d(TAG, "Saved string to SharedPreferences with key '$key'")
    }

    private fun getStringFromPrefs(key: String): String? {
        val sp = requireContext().getSharedPreferences(HomeFragment.PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(key, null).also {
            Log.d(TAG, if (it != null) "Prefs hit for '$key'" else "Prefs miss for '$key'")
        }
    }

    private fun toast(msg: String) {
        if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "BleAdvertisingFragment"
        const val PREFS_NAME = "PalmAppPrefs"
        const val KEY_ID_RESPONSE = "identity"
        const val KEY_USERID = "user_id"
        const val KEY_BLE_DATA = "ble_data"
        const val KEY_PALM_HASH = "palm_hash"

        const val ACTION_DEVICE_CONNECTED = "com.example.palm_app.DEVICE_CONNECTED"
        const val ACTION_DEVICE_DISCONNECTED = "com.example.palm_app.DEVICE_DISCONNECTED"
        const val EXTRA_DEVICE_ADDRESS = "DEVICE_ADDRESS"
        const val ACTION_REQUEST_CONNECTION_STATUS = "com.example.palm_app.REQUEST_CONNECTION_STATUS"
        const val ACTION_ADVERTISING_STARTED = "com.example.palm_app.ADVERTISING_STARTED"
        const val EXTRA_DISPLAY_ID = "extra_display_id"

        const val ACTION_MANUFACTURER_DATA_READY = "com.example.palm_app.MANUFACTURER_DATA_READY"
        const val EXTRA_MANUFACTURER_DATA_STRING = "com.example.palm_app.EXTRA_MANUFACTURER_DATA_STRING"

    }
}

