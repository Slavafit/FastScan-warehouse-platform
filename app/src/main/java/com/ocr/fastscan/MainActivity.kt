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
import com.google.gson.Gson

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val apiService by lazy { NetworkModule.createService(this) }
    private val firebaseManager = FirebaseManager()
    private val soundManager by lazy { SoundManager(this) }
    private val printHelper by lazy { PrintHelper(this) }
    private val tokenManager by lazy { TokenManager(this) }
    private var currentReceiverGuid: String? = null

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
                    Log.d("SCAN_DEBUG", "Mode: $details") // Добавь это для проверки

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

                Log.d("FetchKG", "Код запроса: $kgCode | HTTP ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val receivers = response.body()!!  // List<ReceiverResponse>

                    if (receivers.isNotEmpty()) {
                        val first = receivers[0]
                        val fullName = first.fullName?.trim() ?: "Имя не указано"
                        currentReceiverGuid = first.recieverGuid  // ← сохраняем GUID
                        Log.d("DEBUG_SCAN", "FetchClientByKgCode: full_name = $fullName")

                        // Успех: показываем имя клиента
                        binding.tvClientName.text = fullName
                        binding.tvClientName.setTextColor(
                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark)
                        )
                        Toast.makeText(this@MainActivity, "Клиент: $fullName", Toast.LENGTH_SHORT).show()
                        soundManager.playSuccess()
                    } else {
                        // Массив пустой
                        Log.d("FetchKG", "Массив пустой")
                        binding.tvClientName.text = "Клиент не найден"
                        binding.tvClientName.setTextColor(
                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark)
                        )
                        Toast.makeText(this@MainActivity, "Клиент не найден", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Неуспешный HTTP-ответ (400, 404, 500 и т.д.)
                    val errorBody = response.errorBody()?.string() ?: "Нет тела ошибки"
                    Log.e("FetchKG", "Ошибка сервера: ${response.code()} → $errorBody")

                    binding.tvClientName.text = "Ошибка сервера ${response.code()}"
                    binding.tvClientName.setTextColor(
                        ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark)
                    )
                    Toast.makeText(this@MainActivity, "Ошибка: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Сетевая ошибка, таймаут, Gson не смог распарсить и т.д.
                Log.e("FetchKG", "Исключение: ${e.message}", e)

                binding.tvClientName.text = "Сеть ошибка"
                binding.tvClientName.setTextColor(
                    ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark)
                )
                Toast.makeText(this@MainActivity, "Сеть: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==========================================
    // ФИНАЛЬНАЯ ОТПРАВКА (Firebase + API)
    // ==========================================
    private fun submitFinalOrder() {
        val track = binding.etTracking.text.toString().trim()
        val kg = binding.etClientCode.text.toString().trim().uppercase()
        val weightStr = binding.etWeight.text.toString().trim()
        val nameToSave = binding.tvClientName.text.toString().trim()

        // Валидация веса и конвертация в Double?
        val grossWeight = try {
            weightStr.toDoubleOrNull() ?: throw IllegalArgumentException("Неверный вес")
        } catch (e: Exception) {
            Toast.makeText(this, "Введите корректный вес (например 0.32)", Toast.LENGTH_SHORT).show()
            return
        }

        if (grossWeight <= 0) {
            Toast.makeText(this, "Вес должен быть больше 0", Toast.LENGTH_SHORT).show()
            return
        }
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
                sendToApi(kg, nameToSave, track, grossWeight)

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
                        //Log.d("Firebase", "Новый трек сохранен")
                        sendToApi(kg, nameToSave, track, grossWeight)
                        Log.d("DEBUG_kg", " $kg")

                    },
                    onFailure = { e ->
                        Log.e("Firebase", "Ошибка: ${e.message}")
                        sendToApi(kg, nameToSave, track, grossWeight)
                    }
                )
            }
        }
    }

    private fun sendToApi(kg: String, name: String, track: String, grossWeight: Double, recieverGuid: String) {
        lifecycleScope.launch {
            try {
                if (recieverGuid == null) {
                    Toast.makeText(this@MainActivity, "Сначала отсканируйте/введите код клиента", Toast.LENGTH_LONG).show()
                    return@launch
                }
                // Используем твои имена параметров для SaveOrderRequest
                val req = SaveOrderRequest(
                    clientCode = kg,
                    clientName = name,
                    recieverGuid = recieverGuid,
                    trackingNumber = track,
                    grossWeight = grossWeight,
                    withBox = if (binding.cbWithBox.isChecked) "1" else "0"
                )

                val res = apiService.saveOrder(req)
                Log.d("SaveOrderJSON", "Отправляемый JSON: ${Gson().toJson(req)}")
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