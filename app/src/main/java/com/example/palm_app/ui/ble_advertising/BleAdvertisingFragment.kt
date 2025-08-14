package com.example.palm_app.ui.ble_advertising

import android.content.Context
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

class BleAdvertisingFragment : Fragment() {

    private var _binding: View? = null
    private lateinit var qrDataDisplayTextView: TextView
    private lateinit var advertiseButton: Button
    private val args: BleAdvertisingFragmentArgs by navArgs()

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_ble_advertising, container, false)
        _binding = view
        qrDataDisplayTextView = view.findViewById(R.id.qr_data_display_textview)
        advertiseButton = view.findViewById(R.id.advertise_button)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "BleAdvertisingFragment onViewCreated")

        // Kick off a single, structured pipeline
        viewLifecycleOwner.lifecycleScope.launch {
            val palmHash = getStringFromPrefs(KEY_PALM_HASH)
            val userIdString = getStringFromPrefs(KEY_USERID)

            if (userIdString.isNullOrBlank()) {
                Log.w(TAG, "No userId in prefs; cannot fetch identity/ble data")
                // Still allow manual start (will advertise with empty payload)
                initUiAfterDataResolved(gattData = null)
                return@launch
            }

            val userId = userIdString.toIntOrNull()
            if (userId == null) {
                Log.e(TAG, "Invalid userId in prefs: $userIdString")
                initUiAfterDataResolved(gattData = null)
                return@launch
            }

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

    /**
     * Called once the sequential pipeline above completes (success or fail).
     * Updates UI and handles permission/start logic without racing async state.
     */
    private fun initUiAfterDataResolved(gattData: String?) {
        if (!isAdded || view == null) return
        qrDataForGatt = gattData
        qrDataDisplayTextView.text = gattData
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
    }
}
