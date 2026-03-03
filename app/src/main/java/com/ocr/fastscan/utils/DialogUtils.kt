package com.ocr.fastscan.utils

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import com.ocr.fastscan.databinding.DialogVolumeBinding
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.ocr.fastscan.data.model.BoxRequest
import com.ocr.fastscan.databinding.DialogAddBoxBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        val divider = context.getVolWeightDivider()

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


    fun showAddNewBoxDialog(
        lifecycleOwner: LifecycleOwner,
        context: Context,
        onSuccess: (String) -> Unit,  // колбэк успеха — передаёт trackingCode
        onError: (String) -> Unit = {}  // колбэк ошибки — опционально
    ) {
        val dialogBinding = DialogAddBoxBinding.inflate(LayoutInflater.from(context))
        Log.d("DialogUtils", "Dialog binding создан")

        val generatedTrackingCode = generateTrackingCode()
        dialogBinding.tvTrackingCode.text = "Будет создан номер: $generatedTrackingCode"
        Log.d("DialogUtils", "Сгенерирован tracking_code: $generatedTrackingCode")

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setTitle("Новая коробка")
            .setPositiveButton("Создать", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialogBinding.etLength.setText("70")
        dialogBinding.etWidth.setText("45")
        dialogBinding.etHeight.setText("50")

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton?.setOnClickListener {
                val length = dialogBinding.etLength.text.toString().toIntOrNull() ?: 70
                val width = dialogBinding.etWidth.text.toString().toIntOrNull() ?: 45
                val height = dialogBinding.etHeight.text.toString().toIntOrNull() ?: 50

                if (length <= 0 || width <= 0 || height <= 0) {
                    Toast.makeText(context, "Размеры должны быть больше 0", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleOwner.lifecycleScope.launch {
                    try {
                        //вызов конструктора для передачи
                        val request = BoxRequest(
                            guid = "",
                            codeid = "",
                            trackingCode = generatedTrackingCode,
                            width = width,
                            height = height,
                            length = length,
                            vWeight = (length * width * height) / context.getVolWeightDivider(),
                        )
                        val response = AppDependencies.apiService.createOrUpdateBox(request)

                        if (response.isSuccessful && response.body()?.success == true) {
                            Toast.makeText(context, "Коробка создана: $generatedTrackingCode", Toast.LENGTH_SHORT).show()
                            AppDependencies.soundManager.playSuccess()
                            onSuccess(generatedTrackingCode)  // ← передаём код коробки в колбэк
                            dialog.dismiss()
                        } else {
                            val errorMsg = response.body()?.message ?: "Ошибка создания"
                            Toast.makeText(context, "Ошибка создания: $errorMsg", Toast.LENGTH_LONG).show()
                            AppDependencies.soundManager.playError()
                            onError(errorMsg)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Сеть/ошибка: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        AppDependencies.soundManager.playError()
                        onError(e.localizedMessage ?: "Неизвестная ошибка")
                    }
                }
            }
        }

        dialog.show()
    }

    // Функция генерации кода (перенеси сюда, если она была в фрагменте)
    private fun generateTrackingCode(): String {
        val date = SimpleDateFormat("MMdd", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HHmm", Locale.getDefault()).format(Date())
        val random = (0..9999).random().toString().padStart(4, '0')
        return "NBX$date$time$random"
    }
}
