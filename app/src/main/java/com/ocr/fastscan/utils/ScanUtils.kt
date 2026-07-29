package com.ocr.fastscan.utils

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.ocr.fastscan.CameraActivity
import com.ocr.fastscan.databinding.FragmentScanBinding
import android.view.View
import androidx.core.content.ContextCompat
import com.ocr.fastscan.R

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

    fun clearFields(binding: FragmentScanBinding, context: Context) {
        //binding.etTracking.text.clear()
        binding.etClientCode.text.clear()
        binding.etWeight.text.clear()
        binding.etLength.text.clear()
        binding.etWidth.text.clear()
        binding.etHeight.text.clear()
        binding.tvClientName.text = context.getString(R.string.scan_wait_client)
        binding.cbWithBox.isChecked = false
        binding.cbShoesBox.isChecked = false
        binding.cbBagBox.isChecked = false
    }
    fun clearFieldsAll(binding: FragmentScanBinding, context: Context,) {
        binding.etTracking.text.clear()
        binding.etClientCode.text.clear()
        binding.etWeight.text.clear()
        binding.etLength.text.clear()
        binding.etWidth.text.clear()
        binding.etHeight.text.clear()
        binding.tvClientName.text = context.getString(R.string.scan_wait)
        binding.tvClientName.setTextColor(ContextCompat.getColor(binding.root.context, android.R.color.black))
        binding.cbWithBox.isChecked = false
        binding.cbShoesBox.isChecked = false
        binding.cbBagBox.isChecked = false
        binding.btnPrint.visibility = View.GONE
    }
 // Если есть другие общие функции — добавляй сюда
}
