package com.ocr.fastscan.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.ocr.fastscan.SettingsActivity
import com.ocr.fastscan.data.model.*
import com.ocr.fastscan.data.SenderManager
import com.ocr.fastscan.databinding.DialogVolumeBinding
import com.ocr.fastscan.databinding.FragmentScanBinding
import com.ocr.fastscan.utils.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val value = result.data?.getStringExtra("scan_result") ?: ""
            val mode = result.data?.getStringExtra("scan_mode") ?: ""
            handleScanResult(mode, value)
        }
    }
    private var currentReceiverGuid: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable { dimScreen() }
    private val INACTIVITY_TIMEOUT = 60_000L  // 60 секунд
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var originalBrightness: Float = 1f

    private val apiService by lazy { AppDependencies.apiService }
    private val tokenManager by lazy { AppDependencies.tokenManager }
    private val soundManager by lazy { AppDependencies.soundManager }
    private val firebaseManager by lazy { AppDependencies.firebaseManager }
    private val printHelper by lazy { PrintHelper(requireContext()) }
    private val senderManager by lazy {
        SenderManager(
            apiService = AppDependencies.apiService,
            context = requireContext()
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Экран не гаснет
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Сохраняем исходную яркость
        originalBrightness = requireActivity().window.attributes.screenBrightness

        // Настраиваем сенсор движения
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Слушаем касания по всему экрану
        binding.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                resetInactivityTimer()
            }
            false
        }
// Enter в поле трека → запрос
        binding.etTracking.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                val tcode = binding.etTracking.text.toString().trim()
                if (tcode.isNotEmpty()) {
                    fetchOrderInfo(tcode)
                    // Скрытие клавиатуры
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(binding.etTracking.windowToken, 0)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        // Твои настройки (кнопки, слушатели и т.д.)
        setupButtons()
        setupClientCodeInput()
        lifecycleScope.launch {
            AppDependencies.performAutoLogin(requireContext())
        }
    }

    private fun setupButtons() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        binding.btnScanTrack.setOnClickListener {
            ScanUtils.launchCamera(requireContext(), "barcode", scanLauncher)
        }
        binding.btnScanKg.setOnClickListener {
            ScanUtils.launchCamera(requireContext(), "kg", scanLauncher)
        }
        binding.btnScanWeight.setOnClickListener {
            ScanUtils.launchCamera(requireContext(), "weight", scanLauncher)
        }

        binding.btnVolumeCalc.setOnClickListener {
            DialogUtils.showVolumeDialog(requireContext()) { weight ->
                   binding.etWeight.setText(weight)
            }
        }
        binding.btnGenerateOnza.setOnClickListener {
            val generated = "ONZA${System.currentTimeMillis().toString().takeLast(8)}"
            binding.etTracking.setText(generated)
            binding.btnPrint.visibility = View.VISIBLE
        }

        binding.btnPrint.setOnClickListener {
            printHelper.printLabel(
                binding.etTracking.text.toString(),
                binding.etClientCode.text.toString(),
                binding.etWeight.text.toString()
            )
        }

        binding.btnSubmit.setOnClickListener { submitFinalOrder() }

        binding.btnLogout.setOnClickListener {
            tokenManager.clear()
            Toast.makeText(requireContext(), "Токены очищены", Toast.LENGTH_SHORT).show()
            ScanUtils.clearFields(binding)
        }
    }


    // ==================== Авто "KG" + только цифры ====================
    private fun setupClientCodeInput() {
        binding.etClientCode.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        binding.etClientCode.imeOptions = EditorInfo.IME_ACTION_DONE

        binding.etClientCode.addTextChangedListener(object : android.text.TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                if (isUpdating) return
                isUpdating = true

                val digits = s.toString().replace(Regex("[^0-9]"), "")
                val fullCode = if (digits.isNotEmpty()) "KG$digits" else ""

                if (fullCode != s.toString()) {
                    binding.etClientCode.setText(fullCode)
                    binding.etClientCode.setSelection(fullCode.length)
                }
                isUpdating = false
            }
        })

        binding.etClientCode.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                val code = binding.etClientCode.text.toString().trim()
                if (code.startsWith("KG") && code.length >= 6) {
                    fetchClientByKgCode(code)
                    // Скрытие клавиатуры
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(binding.etClientCode.windowToken, 0)
                    true
                } else {
                    Toast.makeText(requireContext(), "Введите минимум 4 цифры после KG", Toast.LENGTH_SHORT).show()
                    false
                }
            } else false
        }
    }

    // ==================== Сканирование ====================
    private fun handleScanResult(mode: String, value: String) {
        if (value.isEmpty()) return
        soundManager.playScanBeep()

        when (mode) {
            "barcode" -> {
                binding.etTracking.setText(value)
                fetchOrderInfo(value)
            }
            "kg" -> {
                val cleanCode = value.trim().uppercase()
                binding.etClientCode.setText(cleanCode)
                fetchClientByKgCode(cleanCode)
            }
            "weight" -> binding.etWeight.setText(value)
        }
    }

    // ==================== ЗАПРОС 1: Трек ====================
    private fun fetchOrderInfo(tcode: String) {
        lifecycleScope.launch {
            try {
                val response = apiService.getOrderDetails(tcode)
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    val details = response.body()!![0]

                    binding.tvClientName.text = details.clientName ?: "Имя не указано"
                    binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))

                    binding.etClientCode.setText(details.clientCode ?: "")
                    binding.etWeight.setText(details.grossWeight?.toString() ?: "")

                    // Чекбокс — только индикатор
                    binding.cbWithBox.isChecked = when (details.withBox) {
                        "1", -> true
                        else -> false
                    }
                    //binding.cbWithBox.isChecked = details.withBox == "1" || details.withBox == true
                    binding.cbWithBox.isEnabled = false
                    // ←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←
                    // Сохраняем user_guid из ответа трека
                    currentReceiverGuid = details.userGuid
                    Log.d("GUID_DEBUG", "Получен user_guid: ${currentReceiverGuid}")
                    // ←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←

                    soundManager.playSuccess()
                } else {
                    binding.tvClientName.text = "Трек не найден. Сканируйте KG"
                    binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
                    binding.etClientCode.text.clear()
                    binding.etWeight.text.clear()
                }
            } catch (e: Exception) {
                Log.e("FetchInfo", "Error: ${e.message}")
            }
        }
    }

    // ==================== ЗАПРОС 2: Код клиента ====================
    private fun fetchClientByKgCode(kgCode: String) {
        lifecycleScope.launch {
            try {
                val response = apiService.getReceiverByCode(kgCode)

                if (response.isSuccessful && response.body() != null) {
                    val receivers = response.body()!!
                    if (receivers.isNotEmpty()) {
                        val first = receivers[0]
                        val fullName = first.fullName?.trim() ?: "Имя не указано"
                        currentReceiverGuid = first.recieverGuid

                        binding.tvClientName.text = fullName
                        binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                        soundManager.playSuccess()
                    } else {
                        binding.tvClientName.text = "Клиент не найден"
                        binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                        soundManager.playError()
                    }
                } else {
                    binding.tvClientName.text = "Ошибка сервера"
                    binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                }
            } catch (e: Exception) {
                Log.e("FetchKG", "Исключение: ${e.message}", e)
                binding.tvClientName.text = "Сеть ошибка"
                binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            }
        }
    }

    // ==================== ФИНАЛЬНАЯ ОТПРАВКА ====================
    private fun submitFinalOrder() {
        val track = binding.etTracking.text.toString().trim()
        val kg = binding.etClientCode.text.toString().trim().uppercase()
        val weightStr = binding.etWeight.text.toString().trim()

        if (track.isEmpty() || !kg.startsWith("KG") || weightStr.isEmpty() || currentReceiverGuid == null) {
            Toast.makeText(requireContext(), "Заполните все поля и код клиента", Toast.LENGTH_SHORT).show()
            return
        }

        val grossWeight = weightStr.toDoubleOrNull() ?: 0.0

        val req = SaveOrderRequest(
            clientCode = kg,
            clientName = binding.tvClientName.text.toString(),
            trackingNumber = track,
            grossWeight = grossWeight,
            recieverGuid = currentReceiverGuid!!,
            userGuid = currentReceiverGuid!!,
            withBox = if (binding.cbWithBox.isChecked) 1 else 0
        )

        //Log.d("SaveOrderJSON", "Отправляем: ${Gson().toJson(req)}")

        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Отправка..."

        senderManager.submitOrder(req,
            onSuccess = {
                soundManager.playSuccess()
                Toast.makeText(requireContext(), "ЗАКАЗ УСПЕШНО СОХРАНЕН!", Toast.LENGTH_SHORT).show()
                ScanUtils.clearFields(binding)
                currentReceiverGuid = null
            },
            onError = { error ->
                soundManager.playError()
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        )

        // Восстановление кнопки (можно вынести в finally внутри submitOrder)
        binding.btnSubmit.isEnabled = true
        binding.btnSubmit.text = "ОТПРАВИТЬ ЗАКАЗ"
    }

    private fun showVolumeDialog() {
        val dialogBinding = DialogVolumeBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogBinding.root).create()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

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

    // Сбрасываем таймер неактивности
    private fun resetInactivityTimer() {
        handler.removeCallbacks(inactivityRunnable)
        handler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT)
        restoreBrightness()
    }

    // Тускнеем экран
    private fun dimScreen() {
        val params = requireActivity().window.attributes
        params.screenBrightness = 0.3f
        requireActivity().window.attributes = params
    }

    // Возвращаем яркость
    private fun restoreBrightness() {
        val params = requireActivity().window.attributes
        params.screenBrightness = originalBrightness
        requireActivity().window.attributes = params
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            if (Math.abs(x) > 2 || Math.abs(y) > 2 || Math.abs(z) > 2) {
                resetInactivityTimer()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        resetInactivityTimer()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(inactivityRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        handler.removeCallbacks(inactivityRunnable)
    }
}