package com.ocr.fastscan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.ocr.fastscan.api.NetworkModule
import com.ocr.fastscan.data.model.* // Импортируем всё из вашего OnzaModels.kt
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

        setupButtons()
        performAutoLogin()
    }

    private fun setupButtons() {
        // Кнопка настроек теперь точно будет найдена
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnScanTrack.setOnClickListener { launchCamera("barcode") }
        binding.btnScanKg.setOnClickListener { launchCamera("kg") }
        binding.btnScanWeight.setOnClickListener { launchCamera("weight") }
        binding.btnVolumeCalc.setOnClickListener { showVolumeDialog() }

        binding.btnGenerateOnza.setOnClickListener {
            val generated = "ONZA${System.currentTimeMillis().toString().takeLast(8)}"
            binding.etTracking.setText(generated)
            binding.btnPrint.visibility = View.VISIBLE
        }

        binding.btnSubmit.setOnClickListener { submitFinalOrder() }

        binding.btnTestLogin.setOnClickListener {
            performAutoLogin()
            Toast.makeText(this, "Тестирую login...", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            tokenManager.clear()
            Toast.makeText(this, "Токены очищены — перелогиньтесь", Toast.LENGTH_SHORT).show()
            //clearFields()
        }
    }

    private fun handleScanResult(mode: String, value: String) {
        soundManager.playScanBeep()
        when (mode) {
            "barcode" -> {
                binding.etTracking.setText(value)
                fetchOrderInfo(value)
            }
            "kg" -> {
                binding.etClientCode.setText(value)
                fetchReceiverByCode(value) // Запуск Сценария Б (получение full_name)
            }
            "weight" -> binding.etWeight.setText(value)
        }
    }

    private fun fetchOrderInfo(tcode: String) {
        lifecycleScope.launch {
            try {
                // 1. Делаем запрос
                val response = apiService.getOrderDetails(tcode)
                val details = response.body()!!

                // 2. Проверяем успех
                if (response.isSuccessful && response.body() != null) {

                    // А. Имя клиента (из поля "client")
                    val name = details.clientName ?: "Имя не указано"
                    binding.tvClientName.text = name
                    binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))

                    // Б. Код клиента (KG)
                    // Если пришел null, оставляем поле пустым или ставим ""
                    binding.etClientCode.setText(details.clientCode ?: "")

                    // В. Коробка (with_box)
                    // Сервер может вернуть 1 (Int) или "1" (String), обрабатываем оба варианта
                    val boxValue = details.withBox.toString()
                    binding.cbWithBox.isChecked = (boxValue == "1" || boxValue == "true")

                    // Г. Вес НЕ ТРОГАЕМ (очищаем или оставляем как есть)
                    // binding.etWeight.text.clear() // Раскомментируй, если надо очищать старый вес

                    soundManager.playSuccess()
                    Toast.makeText(this@MainActivity, "Данные загружены", Toast.LENGTH_SHORT).show()

                } else {
                    // Если трек не найден (404 или пусто)
                    binding.tvClientName.text = "Трек не найден (введите KG)"
                    binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark))
                    // Очищаем KG, чтобы не осталось от предыдущего скана
                    binding.etClientCode.text.clear()
                    binding.cbWithBox.isChecked = false
                }
            } catch (e: Exception) {
                Log.e("FetchInfo", "Error: ${e.message}")
                binding.tvClientName.text = "Ошибка сети"
                binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
            }
        }
    }


    private fun handleScanResult(mode: String, value: String) {
        soundManager.playScanBeep()
        when (mode) {
            "barcode" -> {
                binding.etTracking.setText(value)
                fetchOrderInfo(value) // Запрос по треку (уже есть)
            }
            "kg" -> {
                binding.etClientCode.setText(value)
                fetchClientByKgCode(value) // НОВЫЙ ЗАПРОС: по KG-коду
            }
            "weight" -> binding.etWeight.setText(value)
        }
    }

    private fun fetchClientByKgCode(kgCode: String) {
        lifecycleScope.launch {
            try {
                val response = apiService.getReceiverByCode(kgCode)
                if (response.isSuccessful && response.body() != null) {
                    val fullName = response.body()?.fullName
                    if (!fullName.isNullOrEmpty()) {
                        binding.tvClientName.text = fullName
                        binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                    } else {
                        binding.tvClientName.text = "Имя не найдено для $kgCode"
                        binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                    }
                }
            } catch (e: Exception) {
                Log.e("FetchReceiver", "Error: ${e.message}")
                Toast.makeText(this@MainActivity, "Ошибка при получении имени клиента", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitFinalOrder() {
        val track = binding.etTracking.text.toString()
        val kg = binding.etClientCode.text.toString()
        val weight = binding.etWeight.text.toString()

        if (track.isEmpty() || kg.isEmpty() || weight.isEmpty()) {
            soundManager.playError()
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            // 1. Сохранение в Firebase
            val fsData = FirestoreShipment(track, kg, binding.tvClientName.text.toString(), weight.toDoubleOrNull() ?: 0.0)
            firebaseManager.saveShipment(fsData, {
                // 2. Отправка в API Onza
                sendToApi(kg, binding.tvClientName.text.toString(), track, weight)
            }, {
                Toast.makeText(this@MainActivity, "Ошибка Firebase", Toast.LENGTH_SHORT).show()
            })
        }
    }

    private fun sendToApi(kg: String, name: String, track: String, weight: String) {
        lifecycleScope.launch {
            try {
                val req = SaveOrderRequest(kg, name, track, weight, if (binding.cbWithBox.isChecked) "1" else "0")
                val res = apiService.saveOrder(req)
                if (res.isSuccessful) {
                    soundManager.playSuccess()
                    Toast.makeText(this@MainActivity, "УСПЕШНО", Toast.LENGTH_SHORT).show()
                    clearFields()
                }
            } catch (e: Exception) {
                soundManager.playError()
            }
        }
    }

    private fun clearFields() {
        binding.etTracking.text.clear()
        binding.etClientCode.text.clear()
        binding.etWeight.text.clear()
        binding.tvClientName.text = "Ожидание..."
        binding.btnPrint.visibility = View.GONE
    }

    private fun launchCamera(mode: String) {
        val intent = Intent(this, CameraActivity::class.java)
        intent.putExtra("mode", mode)
        scanLauncher.launch(intent)
    }

    private fun performAutoLogin() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val login = prefs.getString("login", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""
        if (login.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Логин/пароль пустые в настройках", Toast.LENGTH_LONG).show()
            return
        }
        if (login.isNotEmpty() && pass.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    val res = apiService.login(mapOf("login" to login, "password" to pass))
                    Log.d("AutoLogin", "Response code: ${res.code()}")
                    if (res.isSuccessful && res.body()?.bearer != null) {
                        tokenManager.saveTokens(res.body()!!.bearer!!, res.body()!!.refreshBearer ?: "")
                        Toast.makeText(this@MainActivity, "Авто-логин OK!", Toast.LENGTH_SHORT).show()
                    } else {
                        val errorBody = res.errorBody()?.string() ?: "No body"
                        Log.e("AutoLoginError", "Code ${res.code()}, Body: $errorBody")
                        Toast.makeText(this@MainActivity, "Авто-логин ошибка: ${res.code()} - $errorBody", Toast.LENGTH_LONG).show()
                    }
                    //res.body()?.bearer?.let { tokenManager.saveTokens(it, "") }
                } catch (e: Exception) {
                Log.e("AutoLoginError", "Exception: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Сеть: ${e.message}", Toast.LENGTH_SHORT).show()
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
                val v = prefs.getString("vol_weight", "6000") ?: ""
                val res = (l * w * h) / 6000.0
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