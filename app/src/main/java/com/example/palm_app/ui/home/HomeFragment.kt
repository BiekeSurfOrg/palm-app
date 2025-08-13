package com.example.palm_app.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView // Kept for existing code, can be removed if text_home is removed
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.palm_app.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome // Kept for existing code
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.startButton.setOnClickListener {
            val userIdString = binding.userIdEditText.text.toString()
            val userId = userIdString.toIntOrNull()
            if (userId == null || userId < 1) {
                Toast.makeText(context, "Please enter a valid User ID (1 or higher)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Make network request in a coroutine
            lifecycleScope.launch {
                fetchDataAndNavigate(userId)
            }
        }

        binding.restartButton.setOnClickListener {
            binding.userIdEditText.setText("1")
            Toast.makeText(context, "Restart: User ID reset to 1.", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun fetchDataAndNavigate(userId: Int) {
        val url = "https://palm-central-7d7e7aad638d.herokuapp.com/Palmki/api/identity/$userId"
        val request = Request.Builder().url(url).header("Content-Type", "application/json").build()

        var jsonResponse: String? = null
        var errorMessage: String? = null

        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code ${response}")
                    jsonResponse = response.body?.string() // Get the raw JSON string
                }
            } catch (e: IOException) {
                Log.e("HomeFragment", "Network request failed", e)
                errorMessage = "Network request failed: ${e.message}"
            } catch (e: Exception) {
                Log.e("HomeFragment", "An unexpected error occurred", e)
                errorMessage = "An unexpected error occurred: ${e.message}"
            }
        }

        withContext(Dispatchers.Main) {
            if (jsonResponse != null) {
                // Navigate using Safe Args, passing the raw JSON string
                val action = HomeFragmentDirections.actionNavHomeToNavRegister(jsonResponse)
                findNavController().navigate(action)
            } else {
                Toast.makeText(context, errorMessage ?: "Failed to fetch data.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
