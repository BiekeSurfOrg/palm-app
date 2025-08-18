package com.example.palm_app.ui.register

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.palm_app.databinding.FragmentRegisterBinding
import com.example.palm_app.network.ApiService
import com.example.palm_app.ui.ble_advertising.BleAdvertisingFragment
import com.example.palm_app.ui.home.HomeFragment
import com.example.palm_app.ui.home.HomeFragment.Companion.KEY_ID_RESPONSE
import com.example.palm_app.ui.home.HomeFragment.Companion.PREFS_NAME
import com.example.palm_app.ui.home.HomeFragmentDirections
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.text.toIntOrNull

class RegisterFragment : Fragment() {

    companion object {
        private const val TAG = "RegisterFragment"
        const val PREFS_NAME = "PalmAppPrefs"
        const val KEY_ID_RESPONSE = "identity"
        const val KEY_USERID = "user_id"
        const val KEY_BLE_DATA = "ble_data"
        const val KEY_PALM_HASH = "palm_hash"
    }

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val args: RegisterFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        displayQRCode(args.jsonData)

        binding.resendButton.setOnClickListener {
            val userIdString = getStringFromPrefs(KEY_USERID)

            if (userIdString.isNullOrBlank()) {
                Log.w(TAG, "No userId in prefs; cannot fetch identity/ble data")
                Toast.makeText(context, "No userId in prefs", Toast.LENGTH_SHORT).show()
            }
            val userId = userIdString?.toIntOrNull()
            if (userId == null) {
                Log.e(
                    TAG, "Invalid userId in prefs: $userIdString")
                Toast.makeText(context, "Invalid userId in prefs", Toast.LENGTH_SHORT).show()
            }
            else {
                lifecycleScope.launch {
                    refetchDataandDisplay(userId)
                }
                Toast.makeText(context, "Resending request...", Toast.LENGTH_SHORT).show()
            }
        }

        binding.nextButtonRegister.setOnClickListener {
            val action = RegisterFragmentDirections.actionRegisterToConfirmRegistration()
            findNavController().navigate(action)
        }
    }

    private  fun displayQRCode(jsonData: String?)
    {

        binding.jsonDataTextview.text = jsonData ?: "No data received."

        if (!jsonData.isNullOrEmpty()) {
            try {
                val qrCodeBitmap = generateQrCode(jsonData)
                binding.qrCodeImageview.setImageBitmap(qrCodeBitmap)
            } catch (e: WriterException) {
                e.printStackTrace()
                Toast.makeText(context, "Could not generate QR code", Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.qrCodeImageview.setImageDrawable(null) // Clear ImageView if no data
        }
}

    private suspend fun refetchDataandDisplay(userId: Int) {
        // Call the shared ApiService
        val result = ApiService.fetchIdentity(userId)

        // Handle the result on the Main thread for UI operations
        withContext(Dispatchers.Main) {
            when (result) {
                is ApiService.FetchIdentityResult.Success -> {
                    val jsonResponse = result.jsonResponse
                    Log.d(TAG, "Successfully fetched identity for User ID $userId: $jsonResponse")
                    // Save the API JSON (identity) to SharedPreferences
                    saveStringToPrefs(KEY_ID_RESPONSE, jsonResponse)

                    displayQRCode(jsonResponse)
                }
                is ApiService.FetchIdentityResult.Error -> {
                    val errorMessage = result.errorMessage
                    displayQRCode(null)
                    Log.e(TAG, "Failed to fetch identity for User ID $userId: $errorMessage")
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()

                }
            }
        }

    }

    private fun getStringFromPrefs(key: String): String? {
        val sp = requireContext().getSharedPreferences(HomeFragment.PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(key, null).also {
            Log.d(TAG, if (it != null) "Prefs hit for '$key'" else "Prefs miss for '$key'")
        }
    }
    private fun saveStringToPrefs(key: String, value: String) {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString(key, value)
            apply()
        }
        Log.d("HomeFragment", "Saved string to SharedPreferences with key '$key'")
    }
    private fun generateQrCode(text: String): Bitmap? {
        val width = 500 // QR code width
        val height = 500 // QR code height
        val multiFormatWriter = MultiFormatWriter()
        try {
            val bitMatrix: BitMatrix = multiFormatWriter.encode(text, BarcodeFormat.QR_CODE, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            return bitmap
        } catch (e: WriterException) {
            e.printStackTrace()
            return null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
