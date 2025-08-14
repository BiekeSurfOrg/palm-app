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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.palm_app.R
import java.nio.charset.StandardCharsets
import com.example.palm_app.util.PermissionHelper
import com.example.palm_app.ble.BlePeripheralService
import com.example.palm_app.network.ApiService
import com.example.palm_app.ui.home.HomeFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONException // Important for error handling

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
        var identityData : String? = null
        var gattData : String? = null
        var token: String? = null
        // retrieving data from shared preferences
        val palmHash = getStringFromPrefs(Companion.KEY_PALM_HASH)
        val userIdString = getStringFromPrefs(Companion.KEY_USERID)
        Log.d(TAG, "palmHash: $palmHash")
        Log.d(TAG, "userIdString: $userIdString")
        // Fetch the BLE data needed to identify the user to the backend
        if (userIdString != null) {
            // fetch new identity data and token to be able to do second call
            val userId = userIdString.toInt()
            lifecycleScope.launch { // Coroutine for UI-related tasks after network call
                identityData = fetchIdentityData(userId)
            }
            if (identityData != null) {
                try {
                    val jsonObject = JSONObject(identityData)
                    token = jsonObject.getString("token")
                    Log.d(TAG, "Jsonparse : Successfully extracted token: $token")
                } catch (e: JSONException) {
                    // Handle cases where identityData is not valid JSON,
                    // or the "token" key doesn't exist, or it's not a string.
                    Log.e("JSONParse", "Error parsing JSON or extracting token: ${e.message}")
                    // Set token to null or handle the error as appropriate for your app
                    token = null
                }
            } else {
                Log.w("JSONParse", "identityData string is null, cannot extract token.")
            }
            // use token to get BLE data message and encryptedSymmetric
            if (token != null) {
                 lifecycleScope.launch {
                     val result = ApiService.postBleId(token)
                     withContext(Dispatchers.Main) { // Switch back to Main thread for UI updates
                         when (result) {
                             is ApiService.PostBleIdResult.Success -> {
                                 val bleIdData = result.jsonResponse
                                 Log.d(TAG, "Successfully posted BLE ID. Response: $bleIdData")
                                 saveStringToPrefs(KEY_BLE_DATA, bleIdData)
                                 gattData = mergeJsonStrings(bleIdData, palmHash)
                             }
                             is ApiService.PostBleIdResult.Error -> {
                                 val errorMessage = result.errorMessage
                                 Log.e(TAG, "Failed to post BLE ID: $errorMessage")
                                 Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                             }
                         }
                     }
                 }
            }


        }

        // 1) Show the QR/debug text like before
        qrDataDisplayTextView.text = gattData
        qrDataForGatt = gattData

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

    private fun mergeJsonStrings(jsonString1: String?, jsonString2: String?): String? {
        val mergedObject = JSONObject()

        // Attempt to merge jsonString1
        if (!jsonString1.isNullOrEmpty()) {
            try {
                val jsonObject1 = JSONObject(jsonString1)
                val keys1 = jsonObject1.keys()
                while (keys1.hasNext()) {
                    val key = keys1.next()
                    mergedObject.put(key, jsonObject1.get(key))
                }
            } catch (e: JSONException) {
                Log.e(TAG, "Error parsing jsonString1 for merge ('$jsonString1'): ${e.message}")
                // If strict merging is required, you might return null here or throw an exception
            }
        }

        // Attempt to merge jsonString2 (will overwrite common keys from jsonString1)
        if (!jsonString2.isNullOrEmpty()) {
            try {
                val jsonObject2 = JSONObject(jsonString2)
                val keys2 = jsonObject2.keys()
                while (keys2.hasNext()) {
                    val key = keys2.next()
                    mergedObject.put(key, jsonObject2.get(key))
                }
            } catch (e: JSONException) {
                Log.e(TAG, "Error parsing jsonString2 for merge ('$jsonString2'): ${e.message}")
                // If strict merging is required, you might return null here or throw an exception
            }
        }

        // If the merged object is empty (e.g., both inputs were null, empty, or invalid JSON), return null.
        return if (mergedObject.length() == 0) {
            null
        } else {
            mergedObject.toString()
        }
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

    private suspend fun fetchIdentityData(userId: Int): String? {
        // Call the shared ApiService
        val result = ApiService.fetchIdentity(userId)
        var jsonResponse: String? = null

        // Handle the result on the Main thread for UI operations
        withContext(Dispatchers.Main) {
            when (result) {
                is ApiService.FetchIdentityResult.Success -> {
                    jsonResponse = result.jsonResponse
                    Log.d(TAG, "Successfully fetched identity for User ID $userId: $jsonResponse")
                    // Save the API JSON (identity) to SharedPreferences
                    saveStringToPrefs(KEY_ID_RESPONSE, jsonResponse)
                }
                is ApiService.FetchIdentityResult.Error -> {
                    val errorMessage = result.errorMessage
                    Log.e(TAG, "Failed to fetch identity for User ID $userId: $errorMessage")
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
        return jsonResponse
    }

    private fun saveStringToPrefs(key: String, value: String) {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString(key, value)
            apply()
        }
        Log.d(TAG, "Saved string to SharedPreferences with key '$key'")
    }

    private fun getStringFromPrefs(key: String): String? {
        val sharedPreferences = requireContext().getSharedPreferences(HomeFragment.Companion.PREFS_NAME, Context.MODE_PRIVATE)
        val value = sharedPreferences.getString(key, null)
        if (value != null) {
            Log.d(TAG, "Retrieved string from SharedPreferences for key '$key'")
        } else {
            Log.d(TAG, "No string found in SharedPreferences for key '$key'")
        }
        return value
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
