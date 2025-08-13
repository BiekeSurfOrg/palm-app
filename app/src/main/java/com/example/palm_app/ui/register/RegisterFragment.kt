package com.example.palm_app.ui.register

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.palm_app.databinding.FragmentRegisterBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix

class RegisterFragment : Fragment() {

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

        val jsonData = args.jsonData

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

        binding.resendButton.setOnClickListener {
            // TODO: Implement resend logic (potentially navigate back or re-trigger fetch from HomeFragment)
            Toast.makeText(context, "Resend clicked (TODO: Implement logic)", Toast.LENGTH_SHORT).show()
        }

        binding.nextButtonRegister.setOnClickListener {
            val action = RegisterFragmentDirections.actionRegisterToConfirmRegistration()
            findNavController().navigate(action)
        }
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
