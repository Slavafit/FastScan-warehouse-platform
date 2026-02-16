package com.ocr.fastscan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.ocr.fastscan.api.NetworkModule
import com.ocr.fastscan.data.model.*
import com.ocr.fastscan.databinding.ActivityMainBinding
import com.ocr.fastscan.databinding.DialogVolumeBinding
import com.ocr.fastscan.utils.*
import kotlinx.coroutines.launch
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val apiService by lazy { NetworkModule.createService(this) }
    private val firebaseManager = FirebaseManager()
    private val soundManager by lazy { SoundManager(this) }
    private val printHelper by lazy { PrintHelper(this) }
    private val tokenManager by lazy { TokenManager(this) }

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val value = result.data?.getStringExtra("scan_result") ?: ""
            val mode = result.data?.getStringExtra("scan_mode") ?: ""
            handleScanResult(mode, value)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupButtons()
        performAutoLogin()

    }

    private fun setupButtons() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Кнопки сканирования
        binding.btnScanTrack.setOnClickListener { launchCamera("barcode") }
        binding.btnScanKg.setOnClickListener { launchCamera("kg") }
        binding.btnScanWeight.setOnClickListener { launchCamera("weight") }

        // Калькулятор объема
        binding.btnVolumeCalc.setOnClickListener { showVolumeDialog() }

        // Генерация трека
        binding.btnGenerateOnza.setOnClickListener {
            val generated = "ONZA${System.currentTimeMillis().toString().takeLast(8)}"
            binding.etTracking.setText(generated)
            binding.btnPrint.visibility = View.VISIBLE
        }

        // Печать
        binding.btnPrint.setOnClickListener {
            val track = binding.etTracking.text.toString()
            val client = binding.etClientCode.text.toString()
            val weight = binding.etWeight.text.toString()
            if (track.isNotEmpty()) {
                printHelper.printLabel(track, client, weight)
            }
        }

        // Отправка
        binding.btnSubmit.setOnClickListener { submitFinalOrder() }

        //Logout
        binding.btnLogout.setOnClickListener {
            tokenManager.clear()
            Toast.makeText(this, "Токены очищены", Toast.LENGTH_SHORT).show()
            clearFields()
        }

        // NEW: Listener для Enter в etClientCode
        binding.etClientCode.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val clientCode = binding.etClientCode.text.toString().trim().uppercase()
                if (clientCode.startsWith("KG") && clientCode.length >= 4) {  // Простая валидация
                    fetchClientByKgCode(clientCode)  // Вызов твоей функции
                    true  // Обработано, клавиатура закроется
                } else {
                    Toast.makeText(this, "Неверный формат KG-кода (например, KG123456)", Toast.LENGTH_SHORT).show()
                    false
                }
            } else {
                false
            }
        }
    }

    // ==========================================
    // ЛОГИКА СКАНИРОВАНИЯ И МАРШРУТИЗАЦИИ
    // ==========================================

    private fun handleScanResult(mode: String, value: String) {
        //Log.d("SCAN_DEBUG", "Mode: $mode, Value: $value") // Добавь это для проверки
        if (value.isEmpty()) return
        soundManager.playScanBeep()

        when (mode) {
            "barcode" -> {
                binding.etTracking.setText(value)
                fetchOrderInfo(value) // Запрос 1: ищем трек
            }
            "kg" -> {
                binding.etClientCode.setText(value)
                fetchClientByKgCode(value) // Запрос 2: ищем имя по коду
            }
            "weight" -> binding.etWeight.setText(value)
        }
    }

    // ==========================================
    // ЗАПРОС 1: ПО ТРЕКУ (/order-tracking-code)
    // ==========================================
    private fun fetchOrderInfo(tcode: String) {
        lifecycleScope.launch {
            try {
                val response = apiService.getOrderDetails(tcode)
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    val details = response.body()!![0]

                    // 1. Заполняем имя из поля "client"
                    binding.tvClientName.text = details.clientName ?: "Имя не указано"
                    binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))

                    // 2. Заполняем остальные поля
                    binding.etClientCode.setText(details.clientCode ?: "")
                    binding.etWeight.setText(details.grossWeight ?: "")

                    val boxValue = details.withBox.toString()
                    binding.cbWithBox.isChecked = (boxValue == "1" || boxValue == "true")

                    soundManager.playSuccess()
                } else {
                    // Если трек не найден
                    binding.tvClientName.text = "Трек не найден. Сканируйте KG"
                    binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark))
                    binding.etClientCode.text.clear()
                }
            } catch (e: Exception) {
                Log.e("FetchInfo", "Error: ${e.message}")
            }
        }
    }

    // ==========================================
    // ЗАПРОС 2: ПО КОДУ КЛИЕНТА (/common/reciever)
    // ==========================================
    private fun fetchClientByKgCode(kgCode: String) {
        lifecycleScope.launch {
            try {
                val response = apiService.getReceiverByCode(kgCode)
                if (response.isSuccessful && response.body() != null) {
                    // Здесь нам важно только поле "full_name"
                    val nameFromApi = response.body()?.fullName
                    Log.d("fetchClientByKgCode", "kgCode: $nameFromApi")

                    if (!nameFromApi.isNullOrEmpty()) {
                        binding.tvClientName.text = nameFromApi
                        binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                        soundManager.playSuccess()
                    } else {
                        binding.tvClientName.text = "Клиент $kgCode"
                    }
                }
            } catch (e: Exception) {
                Log.e("FetchKG", "Error: ${e.message}")
            }
        }
    }

    // ==========================================
    // ФИНАЛЬНАЯ ОТПРАВКА (Firebase + API)
    // ==========================================
    private fun submitFinalOrder() {
        val track = binding.etTracking.text.toString()
        val kg = binding.etClientCode.text.toString()
        val weightStr = binding.etWeight.text.toString()
        val nameToSave = binding.tvClientName.text.toString()

        if (track.isEmpty() || kg.isEmpty() || weightStr.isEmpty()) {
            Toast.makeText(this, "Заполните все данные!", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Проверка..."

        // ШАГ 1: Проверяем, есть ли такой трек в Firebase
        firebaseManager.checkTrackExists(track) { exists ->
            if (exists) {
                // Если трек уже есть, не дублируем в Firebase, а сразу шлем в API
                Log.d("Firebase", "Трек $track уже существует, пропускаем запись в облако")
                sendToApi(kg, nameToSave, track, weightStr)
            } else {
                // ШАГ 2: Если трека нет, сохраняем в Firebase
                val weightDouble = weightStr.toDoubleOrNull() ?: 0.0
                val fsData = FirestoreShipment(
                    trackingNumber = track,
                    clientCode = kg,
                    clientName = nameToSave,
                    weight = weightDouble
                )

                firebaseManager.saveShipment(fsData,
                    onSuccess = {
                        Log.d("Firebase", "Новый трек сохранен")
                        sendToApi(kg, nameToSave, track, weightStr)
                    },
                    onFailure = { e ->
                        Log.e("Firebase", "Ошибка: ${e.message}")
                        sendToApi(kg, nameToSave, track, weightStr)
                    }
                )
            }
        }
    }

    private fun sendToApi(kg: String, name: String, track: String, weight: String) {
        lifecycleScope.launch {
            try {
                // Используем твои имена параметров для SaveOrderRequest
                val req = SaveOrderRequest(
                    clientCode = kg,
                    clientName = name,
                    trackingNumber = track,
                    grossWeight = weight,
                    withBox = if (binding.cbWithBox.isChecked) "1" else "0"
                )

                val res = apiService.saveOrder(req)
                if (res.isSuccessful) {
                    soundManager.playSuccess()
                    Toast.makeText(this@MainActivity, "ЗАКАЗ УСПЕШНО СОХРАНЕН!", Toast.LENGTH_SHORT).show()
                    clearFields()
                } else {
                    soundManager.playError()
                    Toast.makeText(this@MainActivity, "Ошибка API: ${res.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                soundManager.playError()
                Toast.makeText(this@MainActivity, "Ошибка сети при отправке", Toast.LENGTH_SHORT).show()
            } finally {
                // Возвращаем кнопку в исходное состояние
                binding.btnSubmit.isEnabled = true
                binding.btnSubmit.text = "ОТПРАВИТЬ ЗАКАЗ"
            }
        }
    }

    // ==========================================
    // ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
    // ==========================================

    private fun clearFields() {
        binding.etTracking.text.clear()
        binding.etClientCode.text.clear()
        binding.etWeight.text.clear()
        binding.tvClientName.text = "Ожидание сканирования..."
        binding.tvClientName.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        binding.cbWithBox.isChecked = false
        binding.btnPrint.visibility = View.GONE
    }

    private fun launchCamera(scanMode: String) {
        val intent = Intent(this, CameraActivity::class.java)
        intent.putExtra("scan_mode", scanMode)
        Log.d("DEBUG_SCAN", "MainActivity отправляет scan_mode: $scanMode")
        scanLauncher.launch(intent)
    }

    private fun performAutoLogin() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val loginVal = prefs.getString("login", "") ?: ""
        val passVal = prefs.getString("password", "") ?: ""

        if (loginVal.isNotEmpty() && passVal.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    val response = apiService.login(mapOf("login" to loginVal, "password" to passVal))
                    if (response.isSuccessful) {
                        val authData = response.body()
                        if (authData?.accessToken != null) {
                            tokenManager.saveTokens(authData.accessToken, authData.refreshToken ?: "")
                            Toast.makeText(this@MainActivity, "Успешный вход!", Toast.LENGTH_SHORT).show()
                            //Log.d("Auth", "Успешный вход! Токен сохранен.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Auth", "Ошибка сети при логине: ${e.message}")
                }
            }
        }
    }

    private fun showVolumeDialog() {
        val dialogBinding = DialogVolumeBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val l = dialogBinding.etLength.text.toString().toDoubleOrNull() ?: 0.0
                val w = dialogBinding.etWidth.text.toString().toDoubleOrNull() ?: 0.0
                val h = dialogBinding.etHeight.text.toString().toDoubleOrNull() ?: 0.0
                val divider = prefs.getString("vol_weight", "6000")?.toDoubleOrNull() ?: 6000.0

                val res = (l * w * h) / divider
                dialogBinding.tvResultPreview.text = "Вес: ${String.format("%.2f", res)} кг"
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        }
        dialogBinding.etLength.addTextChangedListener(watcher)
        dialogBinding.etWidth.addTextChangedListener(watcher)
        dialogBinding.etHeight.addTextChangedListener(watcher)

        dialogBinding.btnApplyVolume.setOnClickListener {
            val resText = dialogBinding.tvResultPreview.text.toString().replace("Вес: ", "").replace(" кг", "").replace(",", ".")
            binding.etWeight.setText(resText)
            dialog.dismiss()
        }
        dialog.show()
    }
}