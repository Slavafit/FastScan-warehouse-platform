package com.ocr.fastscan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val apiService by lazy { NetworkModule.createService(this) }
    private val firebaseManager = FirebaseManager()
    private val soundManager by lazy { SoundManager(this) }
    private val printHelper by lazy { PrintHelper(this) }
    private val tokenManager by lazy { TokenManager(this) }

    private fun isLoggedIn(): Boolean {
        return tokenManager.getToken() != null  // Или добавьте проверку на expire (если сервер возвращает expiry)
    }

    // Новый способ получения данных из камеры
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

        // При старте пробуем авторизоваться
        performAutoLogin()
    }

    private fun performAutoLogin() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val login = prefs.getString("login", "") ?: ""
        val password = prefs.getString("password", "") ?: ""

        if (login.isNotEmpty() && password.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    val response = apiService.login(mapOf("email" to login, "password" to password))
                    if (response.isSuccessful && response.body()?.bearer != null) {
                        tokenManager.saveTokens(response.body()!!.bearer!!, response.body()!!.refreshBearer ?: "")
                        Toast.makeText(this@MainActivity, "Вход выполнен", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Ошибка входа. Проверьте настройки", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Нет связи с сервером", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Укажите логин/пароль в настройках!", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun handleScanResult(mode: String, value: String) {
        if (value.isEmpty()) return
        soundManager.playScanBeep()

        when (mode) {
            "barcode" -> {
                binding.etTracking.setText(value)
                fetchOrderInfo(value) // Сразу ищем клиента
            }
            "kg" -> binding.etClientCode.setText(value)
            "weight" -> binding.etWeight.setText(value)
        }
    }

    private fun fetchOrderInfo(tcode: String) {
        lifecycleScope.launch {
            try {
                // Если токена нет, запрос не сработает (Authenticator попробует обновить, если настроен)
                val response = apiService.getOrderDetails(tcode)
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    val details = response.body()!![0]
                    binding.tvClientName.text = details.clientName ?: "Имя не найдено"
                    binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))

                    if (!details.clientCode.isNullOrEmpty()) {
                        binding.etClientCode.setText(details.clientCode)
                    }
                    binding.cbWithBox.isChecked = details.withBox == "1"
                } else {
                    binding.tvClientName.text = "Трек не зарегистрирован"
                    binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка API: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupButtons() {
        binding.btnScanTrack.setOnClickListener { launchCamera("barcode") }
        binding.btnScanKg.setOnClickListener { launchCamera("kg") }
        binding.btnScanWeight.setOnClickListener { launchCamera("weight") }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnVolumeCalc.setOnClickListener { showVolumeDialog() }

        binding.btnGenerateOnza.setOnClickListener {
            // Генерация: ONZA + ГГММДД + 4 цифры времени
            val time = System.currentTimeMillis().toString()
            val shortId = time.substring(time.length - 8)
            val generated = "ONZA$shortId"

            binding.etTracking.setText(generated)
            binding.btnPrint.visibility = View.VISIBLE
            binding.tvClientName.text = "Новый трек (Generated)"
        }

        binding.btnPrint.setOnClickListener {
            val track = binding.etTracking.text.toString()
            val client = binding.etClientCode.text.toString()
            val weight = binding.etWeight.text.toString()
            if (track.isNotEmpty()) {
                printHelper.printLabel(track, client, weight)
            }
        }

        binding.btnSubmit.setOnClickListener { submitFinalOrder() }
    }

    private fun launchCamera(mode: String) {
        val intent = Intent(this, CameraActivity::class.java)
        intent.putExtra("mode", mode)
        scanLauncher.launch(intent)
    }

    private fun submitFinalOrder() {
        val track = binding.etTracking.text.toString()
        val kg = binding.etClientCode.text.toString()
        val weight = binding.etWeight.text.toString()
        val clientName = binding.tvClientName.text.toString()

        if (track.isEmpty() || kg.isEmpty() || weight.isEmpty()) {
            soundManager.playError()
            Toast.makeText(this, "Заполните все поля!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.btnSubmit.isEnabled = false // Блокируем кнопку
            binding.btnSubmit.text = "Отправка..."

            // 1. Сначала в Firebase (надежнее, если API упадет)
            val fsData = FirestoreShipment(track, kg, clientName, weight.toDoubleOrNull() ?: 0.0, if (binding.cbWithBox.isChecked) "1" else "0")

            firebaseManager.saveShipment(fsData,
                onSuccess = {
                    // 2. Если в Firebase ушло, пробуем на API
                    sendToApi(kg, clientName, track, weight)
                },
                onFailure = {
                    // Даже если Firebase упал, пробуем API
                    sendToApi(kg, clientName, track, weight)
                }
            )
        }
    }

    private fun sendToApi(kg: String, name: String, track: String, weight: String) {
        lifecycleScope.launch {
            try {
                val req = SaveOrderRequest(kg, name, track, weight, if (binding.cbWithBox.isChecked) "1" else "0")
                val res = apiService.saveOrder(req)
                if (res.isSuccessful && res.body()?.success == true) {
                    soundManager.playSuccess()
                    Toast.makeText(this@MainActivity, "ЗАКАЗ СОХРАНЕН!", Toast.LENGTH_LONG).show()
                    clearFields()
                } else {
                    soundManager.playError()
                    Toast.makeText(this@MainActivity, "Ошибка сервера ONZA", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                soundManager.playError()
                Toast.makeText(this@MainActivity, "Сбой сети: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSubmit.isEnabled = true
                binding.btnSubmit.text = "ОТПРАВИТЬ ЗАКАЗ"
            }
        }
    }

    private fun clearFields() {
        binding.etTracking.text.clear()
        binding.etClientCode.text.clear()
        binding.etWeight.text.clear()
        binding.tvClientName.text = "Ожидание..."
        binding.tvClientName.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        binding.cbWithBox.isChecked = false
        binding.btnPrint.visibility = View.GONE
    }

    private fun showVolumeDialog() {
        val dialogBinding = DialogVolumeBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val l = dialogBinding.etLength.text.toString().toDoubleOrNull() ?: 0.0
                val w = dialogBinding.etWidth.text.toString().toDoubleOrNull() ?: 0.0
                val h = dialogBinding.etHeight.text.toString().toDoubleOrNull() ?: 0.0
                val res = (l * w * h) / 6000.0
                dialogBinding.tvResultPreview.text = "Вес: ${String.format("%.3f", res)} кг"
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