package com.ocr.fastscan

import android.app.Activity
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
import com.ocr.fastscan.BuildConfig  // FIX: Правильный импорт (ваш package)
import com.ocr.fastscan.api.NetworkModule
import com.ocr.fastscan.data.model.*
import com.ocr.fastscan.databinding.ActivityMainBinding
import com.ocr.fastscan.databinding.DialogVolumeBinding
import com.ocr.fastscan.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val apiService by lazy { NetworkModule.createService(this) }
    private val firebaseManager = FirebaseManager()
    private val soundManager by lazy { SoundManager(this) }
    private val printHelper by lazy { PrintHelper(this) }
    private val tokenManager by lazy { TokenManager(this) }

    private fun isLoggedIn(): Boolean {
        return tokenManager.getToken() != null  // Можно добавить expire check если есть expiry в response
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

        if (BuildConfig.DEBUG) {
            // NEW: Покажем кнопки только в debug
            binding.btnTestLogin.visibility = View.VISIBLE
            binding.btnLogout.visibility = View.VISIBLE
        }

        setupButtons()

        // При старте пробуем авторизоваться
        performAutoLogin()
    }

    private fun performAutoLogin() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val login = prefs.getString("login", "") ?: ""
        val password = prefs.getString("password", "") ?: ""

        if (login.isEmpty() || password.isEmpty()) {  // FIX: Убрал дубликат if
            Toast.makeText(this, "Логин/пароль пустые в настройках", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                val response = apiService.login(mapOf("email" to login, "password" to password))
                Log.d("AutoLogin", "Response code: ${response.code()}")
                if (response.isSuccessful && response.body()?.bearer != null) {
                    tokenManager.saveTokens(response.body()!!.bearer!!, response.body()!!.refreshBearer ?: "")
                    Toast.makeText(this@MainActivity, "Авто-логин OK!", Toast.LENGTH_SHORT).show()
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No body"
                    Log.e("AutoLoginError", "Code ${response.code()}, Body: $errorBody")
                    Toast.makeText(this@MainActivity, "Авто-логин ошибка: ${response.code()} - $errorBody", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("AutoLoginError", "Exception: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Сеть: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupButtons() {
        // Твои кнопки...

        // Обработчик для btnTestLogin
        binding.btnTestLogin.setOnClickListener {
            performAutoLogin()
            Toast.makeText(this, "Тестирую login...", Toast.LENGTH_SHORT).show()
        }

        // Кнопка Logout для clear()
        binding.btnLogout.setOnClickListener {
            tokenManager.clear()
            Toast.makeText(this, "Токены очищены — перелогиньтесь", Toast.LENGTH_SHORT).show()
            clearFields()  // Опционально
        }
    }

    // Предполагаю, handleScanResult (добавь если truncated)
    private fun handleScanResult(mode: String, value: String) {
        when (mode) {
            "barcode" -> {
                binding.etTracking.setText(value)
                fetchOrderInfo(value)  // Вызов после сканирования трека
            }
            // ... другие моды (kg, weight)
        }
    }

    // Улучшенный fetchOrderInfo
    private fun fetchOrderInfo(tcode: String) {
        lifecycleScope.launch {
            try {
                val res = apiService.getOrderDetails(tcode)
                Log.d("FetchInfo", "Response code: ${res.code()}")
                if (res.isSuccessful) {
                    val details = res.body() ?: emptyList()
                    if (details.isNotEmpty()) {
                        // Трек зарегистрирован
                        val first = details[0]
                        binding.tvClientName.text = first.clientName ?: "Клиент: ${first.clientCode}"
                        binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
                        binding.etClientCode.setText(first.clientCode ?: "")
                        binding.cbWithBox.isChecked = (first.withBox == "1")
                        if (first.grossWeight != null && first.grossWeight > 0) {
                            binding.etWeight.setText(first.grossWeight.toString())
                        }
                        soundManager.playSuccess()
                        Toast.makeText(this@MainActivity, "Трек найден — данные загружены", Toast.LENGTH_SHORT).show()
                    } else {
                        // Трек НЕ зарегистрирован — позволяем ввести вручную
                        binding.tvClientName.text = "Новый трек — введите код клиента"
                        binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark))  // Синий для info
                        Toast.makeText(this@MainActivity, "Трек новый — сканируйте/введите код клиента и вес", Toast.LENGTH_SHORT).show()
                        // Здесь можно показать кнопку сканирования KG или ничего — отправка всё равно возможна позже
                    }
                } else {
                    val errorBody = res.errorBody()?.string() ?: "No body"
                    Log.e("FetchInfoError", "Code ${res.code()}, Body: $errorBody")
                    binding.tvClientName.text = "Ошибка: ${res.code()}"
                    binding.tvClientName.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                    Toast.makeText(this@MainActivity, "Fetch ошибка: ${res.code()} - $errorBody", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("FetchInfoError", "Exception: ${e.message}", e)
                binding.tvClientName.text = "Сеть ошибка"
                Toast.makeText(this@MainActivity, "Сеть: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // sendToApi с проверкой авторизации
    private fun sendToApi(clientCode: String, name: String, track: String, weight: String) {  // Предполагаю параметры
        lifecycleScope.launch {
            // NEW: Проверка и refresh авторизации
            if (!isLoggedIn()) {
                performAutoLogin()
                delay(1000)  // Ждём
                if (!isLoggedIn()) {
                    Toast.makeText(this@MainActivity, "Авторизация провалилась", Toast.LENGTH_LONG).show()
                    binding.btnSubmit.isEnabled = true
                    return@launch
                }
            }

            try {
                val req = SaveOrderRequest(clientCode = clientCode, clientName = name, trackingNumber = track, grossWeight = weight, withBox = if (binding.cbWithBox.isChecked) "1" else "0")
                Log.d("OnzaRequest", "Sending: $req")
                val res = apiService.saveOrder(req)
                if (res.isSuccessful) {
                    val body = res.body()
                    if (body?.success == true) {
                        soundManager.playSuccess()
                        Toast.makeText(this@MainActivity, "ЗАКАЗ СОХРАНЕН!", Toast.LENGTH_LONG).show()
                        // NEW: Сохрани в Firebase после успеха Onza
                        //val shipment = FirestoreShipment(/* твои данные */)
                        //firebaseManager.saveShipment(shipment, {}, { e -> Log.e("Firebase", e.message) })
                        clearFields()
                    } else {
                        val errorBody = res.errorBody()?.string() ?: "No error body"
                        Log.e("OnzaError", "Unsuccessful: Code ${res.code()}, Body: $errorBody")
                        Toast.makeText(this@MainActivity, "ONZA ошибка: ${res.code()} - $errorBody", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val errorBody = res.errorBody()?.string() ?: "No error body"
                    Log.e("OnzaError", "HTTP error: Code ${res.code()}, Body: $errorBody")
                    Toast.makeText(this@MainActivity, "ONZA HTTP: ${res.code()} - $errorBody", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("OnzaError", "Exception: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Сеть/исключение: ${e.message}", Toast.LENGTH_LONG).show()
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