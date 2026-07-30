package com.ocr.fastscan.utils

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.LifecycleOwner
import com.ocr.fastscan.databinding.DialogVolumeBinding
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.BoxRequest
import com.ocr.fastscan.data.model.OrderDimensions
import com.ocr.fastscan.databinding.DialogAddBoxBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val apiService by lazy { AppDependencies.apiService }
private val soundManager by lazy { AppDependencies.soundManager }
object DialogUtils {

    fun showVolumeDialog(
        context: Context,
        onResult: (OrderDimensions) -> Unit
    ) {
        val dialogBinding = DialogVolumeBinding.inflate(LayoutInflater.from(context))

        val builder = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setTitle((R.string.calc_v_weight))

        val dialog = builder.create()

        // Слушатель для обновления preview в реальном времени
        val textWatcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                calculateAndShowPreview(dialogBinding)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        // Привязываем слушатели к полям
        dialogBinding.etLength.addTextChangedListener(textWatcher)
        dialogBinding.etWidth.addTextChangedListener(textWatcher)
        dialogBinding.etHeight.addTextChangedListener(textWatcher)

        // ЛОГИКА ТВОЕЙ КНОПКИ СОХРАНЕНИЯ (btnApplyVolume)
        dialogBinding.btnApplyVolume.setOnClickListener {
            val length = dialogBinding.etLength.text.toString().toIntOrNull() ?: 0
            val width  = dialogBinding.etWidth.text.toString().toIntOrNull()  ?: 0
            val height = dialogBinding.etHeight.text.toString().toIntOrNull() ?: 0

            // Проверка на заполненность
            if (length <= 0 || width <= 0 || height <= 0) {
                Toast.makeText(
                    context,
                    (R.string.dimensions_more_0),
                    Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener // Прерываем выполнение, диалог не закрываем
            }
            // Финальный расчёт (переводим одно из чисел в Double до деления, чтобы сохранить дроби)
            val divider = context.getVolWeightDivider()
            val vWeight = (length.toDouble() * width * height) / divider
            // Форматируем до 2 знаков после запятой
            val vWeightFormatted = String.format(Locale.US,"%.2f", vWeight).toDouble()

            val result = OrderDimensions(
                grossWeight = vWeightFormatted,
                length = length,
                width = width,
                height = height
            )
            //Log.d("SaveOrderJSON", "Отправляем: $result")

            onResult(result)
            dialog.dismiss()
        }

        builder.setNegativeButton(R.string.cancel) { _, _ ->
            dialog.dismiss()
        }
        // Показываем диалог
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialogBinding.etLength.post {
            dialogBinding.etLength.requestFocus()
            val inputMethodManager =
                context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(dialogBinding.etLength, InputMethodManager.SHOW_IMPLICIT)
        }
    }



    fun showAddNewBoxDialog(
        lifecycleOwner: LifecycleOwner,
        context: Context,
        onSuccess: (String) -> Unit,  // колбэк успеха — передаёт trackingCode
        onError: (String) -> Unit = {}  // колбэк ошибки — опционально
    ) {
        val dialogBinding = DialogAddBoxBinding.inflate(LayoutInflater.from(context))
        //Log.d("DialogUtils", "Dialog binding создан")

        val generatedTrackingCode = generateTrackingCode()
        dialogBinding.tvTrackingCode.text = context.getString(
            R.string.new_box,
            generatedTrackingCode
        )

        //Log.d("DialogUtils", "Сгенерирован tracking_code: $generatedTrackingCode")

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setTitle(R.string.add_new_box)
            .setPositiveButton(R.string.create, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialogBinding.etLength.setText("70")
        dialogBinding.etWidth.setText("45")
        dialogBinding.etHeight.setText("50")

        var isSubmitting = false
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            positiveButton?.setOnClickListener {
                if (isSubmitting) return@setOnClickListener

                val length = dialogBinding.etLength.text.toString().toIntOrNull() ?: 70
                val width = dialogBinding.etWidth.text.toString().toIntOrNull() ?: 45
                val height = dialogBinding.etHeight.text.toString().toIntOrNull() ?: 50

                if (length <= 0 || width <= 0 || height <= 0) {
                    Toast.makeText(context, (R.string.dimensions_more_0), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleOwner.lifecycleScope.launch {
                    try {
                        isSubmitting = true
                        positiveButton.isEnabled = false
                        negativeButton?.isEnabled = false
                        dialog.setCanceledOnTouchOutside(false)
                        dialog.setCancelable(false)

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
                        val response = apiService.createOrUpdateBox(request)

                        if (response.isSuccessful && response.body()?.success == true) {
                            Toast.makeText(context,
                                context.getString(
                                R.string.box_created,
                                generatedTrackingCode),
                                Toast.LENGTH_SHORT)
                                .show()
                            soundManager.playSuccess()
                            onSuccess(generatedTrackingCode)  // ← передаём код коробки в колбэк
                            dialog.dismiss()
                        } else {
                            val errorMsg = response.body()?.message ?: context.getString(R.string.error_create)

                            Toast.makeText(context,
                                context.getString(
                                R.string.error_create,
                                errorMsg),
                                Toast.LENGTH_LONG)
                                .show()
                            soundManager.playError()
                            onError(errorMsg)
                        }
                    } catch (e: Exception) {
                        val errorMsg = e.localizedMessage
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.net_error,
                                errorMsg),
                            Toast.LENGTH_LONG)
                            .show()
                        soundManager.playError()
                        onError(e.localizedMessage ?: context.getString(R.string.unknown_error))
                    } finally {
                        if (dialog.isShowing) {
                            isSubmitting = false
                            positiveButton.isEnabled = true
                            negativeButton?.isEnabled = true
                            dialog.setCanceledOnTouchOutside(true)
                            dialog.setCancelable(true)
                        }
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
// Отдельная функция для расчёта и показа preview
private fun calculateAndShowPreview(binding: DialogVolumeBinding) {
    val length = binding.etLength.text.toString().toIntOrNull() ?: 0
    val width  = binding.etWidth.text.toString().toIntOrNull()  ?: 0
    val height = binding.etHeight.text.toString().toIntOrNull() ?: 0

    if (length > 0 && width > 0 && height > 0) {
        val divider = binding.root.context.getVolWeightDivider()
        val vWeight = (length * width * height / divider)
        //binding.tvResultPreview.text = String.format(java.util.Locale.US, "Вес: %.2f кг", vWeight)

        binding.tvResultPreview.text = binding.root.context.getString(R.string.weight_format, vWeight)
    } else {
        binding.tvResultPreview.text = binding.root.context.getString(R.string.weight_kg)
    }
}
