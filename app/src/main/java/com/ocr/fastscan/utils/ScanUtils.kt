package com.ocr.fastscan.utils


import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.ocr.fastscan.CameraActivity
import com.ocr.fastscan.databinding.FragmentScanBinding
import android.view.View
import androidx.core.content.ContextCompat

object ScanUtils {

    fun launchCamera(
        context: Context,
        mode: String,
        launcher: ActivityResultLauncher<Intent>
    ) {
        val intent = Intent(context, CameraActivity::class.java)
        intent.putExtra("scan_mode", mode)
        launcher.launch(intent)
    }

    fun clearFields(binding: FragmentScanBinding) {
        binding.etTracking.text.clear()
        binding.etClientCode.text.clear()
        binding.etWeight.text.clear()
        binding.etLength.text.clear()
        binding.etWidth.text.clear()
        binding.etHeight.text.clear()
        binding.tvClientName.text = "Ожидание сканирования..."
        binding.tvClientName.setTextColor(ContextCompat.getColor(binding.root.context, android.R.color.black))
        binding.cbWithBox.isChecked = false
        binding.btnPrint.visibility = View.GONE
    }
 // Если есть другие общие функции — добавляй сюда
}