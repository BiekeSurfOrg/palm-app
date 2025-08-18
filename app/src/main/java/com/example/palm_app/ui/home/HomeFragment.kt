package com.example.palm_app.ui.home

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.palm_app.databinding.FragmentHomeBinding
import com.example.palm_app.network.ApiService // Added import for ApiService
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers // Ensured Dispatchers is imported for withContext(Dispatchers.Main)

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val PREFS_NAME = "PalmAppPrefs"
        const val KEY_ID_RESPONSE = "identity"
        const val KEY_USERID = "user_id"
        const val KEY_BLE_DATA = "ble_data"
        const val KEY_PALM_HASH = "palm_hash"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val previousApiResponse = getStringFromPrefs(KEY_ID_RESPONSE)
        if (previousApiResponse != null) {
            Log.d("HomeFragment", "Retrieved previous identity response for key '$KEY_ID_RESPONSE': $previousApiResponse")
        } else {
            Log.d("HomeFragment", "No previous identity response found for key '$KEY_ID_RESPONSE'")
        }

        val previousUserId = getStringFromPrefs(KEY_USERID)
        if (previousUserId != null) {
            Log.d("HomeFragment", "Retrieved previous User ID for key '$KEY_USERID': $previousUserId")
            binding.userIdEditText.setText(previousUserId)
        } else {
            Log.d("HomeFragment", "No previous User ID found for key '$KEY_USERID'")
            binding.userIdEditText.setText("1")
        }

        binding.startButton.setOnClickListener {
            val userIdString = binding.userIdEditText.text.toString()
            val userId = userIdString.toIntOrNull()

            if (userId == null || userId < 1) {
                Toast.makeText(context, "Please enter a valid User ID (1 or higher)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (userIdString.isNotEmpty()) {
                saveStringToPrefs(KEY_USERID, userIdString)
                Log.d("HomeFragment", "Saved User ID '$userIdString' to SharedPreferences with key '$KEY_USERID'")
            }

            lifecycleScope.launch { 
                fetchDataAndNavigate(userId)
            }
        }

        binding.restartButton.setOnClickListener {
            performRestartActions() // Logic moved to its own method
        }
    }

    fun performRestartActions() {
        if (_binding == null || !isAdded || view == null) {
            Log.e("HomeFragment", "performRestartActions called when fragment is not in a valid state.")
            return
        }
        val defaultUserId = "1"
        binding.userIdEditText.setText(defaultUserId)
        saveStringToPrefs(KEY_USERID, defaultUserId)
        Toast.makeText(requireContext(), "Restart: User ID reset to 1. All data cleared.", Toast.LENGTH_LONG).show()
        clearStringFromPrefs(KEY_ID_RESPONSE)
        clearStringFromPrefs(KEY_BLE_DATA)
        clearStringFromPrefs(KEY_PALM_HASH)
        Log.d("HomeFragment", "Cleared API response for key '$KEY_ID_RESPONSE' on restart.")
        Log.d("HomeFragment", "Cleared BLE data for key '$KEY_BLE_DATA' on restart.")
        Log.d("HomeFragment", "Cleared Palm Hash for key '$KEY_PALM_HASH' on restart.")
        Log.d("HomeFragment", "Saved User ID '$defaultUserId' to SharedPreferences with key '$KEY_USERID' on restart.")
    }

    private suspend fun fetchDataAndNavigate(userId: Int) {
        val result = ApiService.fetchIdentity(userId)
        withContext(Dispatchers.Main) {
            when (result) {
                is ApiService.FetchIdentityResult.Success -> {
                    val jsonResponse = result.jsonResponse
                    Log.d("HomeFragment", "Successfully fetched identity for User ID $userId: $jsonResponse")
                    saveStringToPrefs(KEY_ID_RESPONSE, jsonResponse)
                    val action = HomeFragmentDirections.actionNavHomeToNavRegister(jsonResponse)
                    findNavController().navigate(action)
                }
                is ApiService.FetchIdentityResult.Error -> {
                    val errorMessage = result.errorMessage
                    Log.e("HomeFragment", "Failed to fetch identity for User ID $userId: $errorMessage")
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
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

    private fun getStringFromPrefs(key: String): String? {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = sharedPreferences.getString(key, null)
        if (value != null) {
            Log.d("HomeFragment", "Retrieved string from SharedPreferences for key '$key'")
        } else {
            Log.d("HomeFragment", "No string found in SharedPreferences for key '$key'")
        }
        return value
    }

    private fun clearStringFromPrefs(key: String) {
        val sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            remove(key)
            apply()
        }
        Log.d("HomeFragment", "Cleared string from SharedPreferences for key '$key'")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
