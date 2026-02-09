package com.ocr.fastscan

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ocr.fastscan.api.NetworkModule
import com.ocr.fastscan.data.model.FirestoreShipment
import com.ocr.fastscan.data.model.SaveOrderRequest
import com.ocr.fastscan.databinding.ActivityMainBinding
import com.ocr.fastscan.databinding.DialogVolumeBinding
import com.ocr.fastscan.utils.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val apiService by lazy { NetworkModule.createService(this) }
    private val firebaseManager = FirebaseManager()
    private val soundManager by lazy { SoundManager(this) }
    private val printHelper by lazy { PrintHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Экран всегда включен для удобства работы на складе
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        // Проверяем, вернулись ли данные из камеры
        if (JsBridge.lastValue.isNotEmpty()) {
            val type = JsBridge.lastType
            val value = JsBridge.lastValue

            when (type) {
                "barcode" -> {
                    binding.etTracking.setText(value)
                    fetchOrderInfo(value) // Автоматический GET запрос
                }
                "kg" -> binding.etClientCode.setText(value)
                "weight" -> binding.etWeight.setText(value)
            }
            // Очищаем мост
            JsBridge.lastType = ""
            JsBridge.lastValue = ""
        }
    }

    private fun setupClickListeners() {
        binding.btnScanTrack.setOnClickListener { openCamera("barcode") }
        binding.btnScanKg.setOnClickListener { openCamera("kg") }
        binding.btnScanWeight.setOnClickListener { openCamera("weight") }

        binding.btnSubmit.setOnClickListener { submitFinalOrder() }

        binding.btnVolumeCalc.setOnClickListener { showVolumeDialog() }

        binding.btnGenerateOnza.setOnClickListener {
            val generated = "ONZA${System.currentTimeMillis()}"
            binding.etTracking.setText(generated)
            binding.btnPrint.visibility = android.view.View.VISIBLE
            soundManager.playScanBeep()
        }

        binding.btnPrint.setOnClickListener {
            val track = binding.etTracking.text.toString()
            if (track.isNotEmpty()) {
                printHelper.printLabel(track, binding.etClientCode.text.toString(), binding.etWeight.text.toString())
            }
        }
    }

    // Автоматический поиск клиента при сканировании трека
    private fun fetchOrderInfo(tcode: String) {
        lifecycleScope.launch {
            try {
                val response = apiService.getOrderDetails(tcode)
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    val details = response.body()!![0]
                    binding.tvClientName.text = details.clientName ?: "Имя не найдено"
                    binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                    binding.etClientCode.setText(details.clientCode)
                    binding.cbWithBox.isChecked = details.withBox == "1"
                    soundManager.playScanBeep()
                } else {
                    binding.tvClientName.text = "Трек не найден в системе"
                    binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка API: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Модальное окно калькулятора объема
    private fun showVolumeDialog() {
        val dialogBinding = DialogVolumeBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        val textWatcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val l = dialogBinding.etLength.text.toString().toDoubleOrNull() ?: 0.0
                val w = dialogBinding.etWidth.text.toString().toDoubleOrNull() ?: 0.0
                val h = dialogBinding.etHeight.text.toString().toDoubleOrNull() ?: 0.0
                val res = (l * w * h) / 6000.0
                dialogBinding.tvResultPreview.text = "Итого: ${String.format("%.3f", res)} кг"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        dialogBinding.etLength.addTextChangedListener(textWatcher)
        dialogBinding.etWidth.addTextChangedListener(textWatcher)
        dialogBinding.etHeight.addTextChangedListener(textWatcher)

        dialogBinding.btnApplyVolume.setOnClickListener {
            val resText = dialogBinding.tvResultPreview.text.toString().filter { it.isDigit() || it == '.' }
            binding.etWeight.setText(resText)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun submitFinalOrder() {
        val track = binding.etTracking.text.toString()
        val kg = binding.etClientCode.text.toString()
        val weight = binding.etWeight.text.toString()

        if (track.isEmpty() || kg.isEmpty() || weight.isEmpty()) {
            soundManager.playError()
            Toast.makeText(this, "Заполните все данные!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val request = SaveOrderRequest(kg, binding.tvClientName.text.toString(), track, weight, if (binding.cbWithBox.isChecked) "1" else "0")

            // 1. Сохраняем на основной сервер
            val response = apiService.saveOrder(request)

            if (response.isSuccessful) {
                // 2. Дублируем в твой личный Firebase
                val firestoreData = FirestoreShipment(track, kg, binding.tvClientName.text.toString(), weight.toDouble(), if (binding.cbWithBox.isChecked) "1" else "0")
                firebaseManager.saveShipment(firestoreData,
                    onSuccess = {
                        soundManager.playSuccess()
                        Toast.makeText(this@MainActivity, "УСПЕШНО ОТПРАВЛЕНО", Toast.LENGTH_SHORT).show()
                        clearForm()
                    },
                    onFailure = { Toast.makeText(this@MainActivity, "Ошибка Firebase", Toast.LENGTH_SHORT).show() }
                )
            } else {
                soundManager.playError()
                Toast.makeText(this@MainActivity, "Ошибка сервера ONZA", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun clearForm() {
        binding.etTracking.text.clear()
        binding.etClientCode.text.clear()
        binding.etWeight.text.clear()
        binding.tvClientName.text = "Ожидание сканирования..."
        binding.tvClientName.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        binding.cbWithBox.isChecked = false
        binding.btnPrint.visibility = android.view.View.GONE
    }

    private fun openCamera(mode: String) {
        val intent = Intent(this, CameraActivity::class.java)
        intent.putExtra("mode", mode)
        startActivity(intent)
    }
}