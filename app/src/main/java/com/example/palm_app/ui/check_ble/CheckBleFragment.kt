package com.example.palm_app.ui.check_ble

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.palm_app.databinding.FragmentCheckBleBinding // Assuming ViewBinding

class CheckBleFragment : Fragment() {

    private var _binding: FragmentCheckBleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCheckBleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // You can setup your UI elements here, e.g.:
        // binding.textViewCheckBleTitle.text = "Checking BLE..."
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}