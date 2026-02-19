package com.ocr.fastscan.utils

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import androidx.preference.PreferenceManager
import com.ocr.fastscan.databinding.DialogVolumeBinding

object DialogUtils {

    fun showVolumeDialog(
        context: Context,
        onResult: (String) -> Unit
    ) {
        val dialogBinding = DialogVolumeBinding.inflate(
            LayoutInflater.from(context)  // ← правильный способ получить inflater
        )

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .create()

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val divider = prefs.getString("vol_weight", "6000")?.toDoubleOrNull() ?: 6000.0

        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val l = dialogBinding.etLength.text.toString().toDoubleOrNull() ?: 0.0
                val w = dialogBinding.etWidth.text.toString().toDoubleOrNull() ?: 0.0
                val h = dialogBinding.etHeight.text.toString().toDoubleOrNull() ?: 0.0
                val res = (l * w * h) / divider
                dialogBinding.tvResultPreview.text = "Вес: ${String.format("%.2f", res)} кг"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        dialogBinding.etLength.addTextChangedListener(watcher)
        dialogBinding.etWidth.addTextChangedListener(watcher)
        dialogBinding.etHeight.addTextChangedListener(watcher)

        dialogBinding.btnApplyVolume.setOnClickListener {
            val resText = dialogBinding.tvResultPreview.text.toString()
                .replace("Вес: ", "")
                .replace(" кг", "")
                .replace(",", ".")
            onResult(resText)
            dialog.dismiss()
        }

        dialog.show()
    }
}