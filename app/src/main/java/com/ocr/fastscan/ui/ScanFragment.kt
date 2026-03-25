package com.ocr.fastscan.ui

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
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
import com.ocr.fastscan.R
import com.ocr.fastscan.SettingsActivity
import com.ocr.fastscan.data.model.*
import com.ocr.fastscan.data.SenderManager
import com.ocr.fastscan.databinding.FragmentScanBinding
import com.ocr.fastscan.utils.*
import kotlinx.coroutines.launch

import com.ocr.fastscan.databinding.DialogSearchClientBinding
import com.ocr.fastscan.databinding.DialogWeightLimitBinding

class ScanFragment : Fragment() {

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
    private val apiService by lazy { AppDependencies.apiService }
    private val tokenManager by lazy { AppDependencies.tokenManager }
    private val soundManager by lazy { AppDependencies.soundManager }
    private val searchOrder by lazy { SearchOrder(this) }
    private val printHelper by lazy { PrintHelper(requireContext()) }
    private val senderManager by lazy {
        SenderManager(
            apiService = AppDependencies.apiService,
            firebaseManager = AppDependencies.firebaseManager,
            context = requireContext()
        )
    }
    private var currentLength: Int = 0
    private var currentWidth: Int = 0
    private var currentHeight: Int = 0
    private var currentOrderDimensions: OrderDimensions? = null

    private var cachedReceivers: List<ReceiverItem>? = null  // ← кэш

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
        // настройки (кнопки, слушатели и т.д.)
        setupButtons()
        setupClientCodeInput()
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
            DialogUtils.showVolumeDialog(requireContext()) { result ->
                // Получаем объёмный вес и размеры

                currentOrderDimensions = result
                binding.etWeight.setText("%.2f".format(result.grossWeight))
                //binding.etWeight.setText(String.format(java.util.Locale.US, "%.2f", result.grossWeight))

                // Сохраняем размеры (например в переменные фрагмента)
                currentLength = result.length
                currentWidth  = result.width
                currentHeight = result.height

                //Log.d("Scan", "Сохранены размеры: ${result.length} × ${result.width} × ${result.height}")
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
                binding.tvClientName.text.toString(),
                binding.etWeight.text.toString()
            )
        }

        binding.btnSubmit.setOnClickListener { submitFinalOrder() }

        // Вызываем диалог ввода трека
        binding.btnGlobalSearch.setOnClickListener {
            searchOrder.showSearchDialog()
        }

        binding.btnLogout.setOnClickListener {
            tokenManager.clear()
            Toast.makeText(requireContext(), "Токены очищены", Toast.LENGTH_SHORT).show()
            ScanUtils.clearFieldsAll(binding)
        }
        binding.btnSearchClient.setOnClickListener {
            showSearchClientDialog()
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

                    binding.tvClientName.text = details.clientName ?: getString(R.string.name_not_specified)
                    binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))

                    binding.etClientCode.setText(details.clientCode ?: "")
                    binding.etWeight.setText(details.grossWeight?.toString() ?: "")
                    //val weightValue = details.grossWeight ?: 0.0
                    //binding.etWeight.setText(String.format(java.util.Locale.US, "%.2f", weightValue))

                    // РАЗМЕРЫ: заполняем из пришедших данных (если null, ставим пусто или 0)
                    binding.etLength.setText(details.length?.toString() ?: "")
                    binding.etWidth.setText(details.width?.toString() ?: "")
                    binding.etHeight.setText(details.height?.toString() ?: "")

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
                    //Log.d("GUID_DEBUG", "Получен user_guid: ${currentReceiverGuid}")
                    // ←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←

                    soundManager.playSuccess()
                } else {
                    binding.tvClientName.text = getString(R.string.track_not_found)
                    binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
                    ScanUtils.clearFields(binding)
                    //binding.etClientCode.text.clear()
                    //binding.etWeight.text.clear()
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
                        val fullName = first.fullName?.trim() ?: getString(R.string.name_not_specified)
                        currentReceiverGuid = first.recieverGuid

                        binding.tvClientName.text = fullName
                        binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))

                        // === Индикаторы коробок ===
                        binding.cbShoesBox.isChecked = first.shoesBox == 1
                        binding.cbBagBox.isChecked = first.bagBox == 1

                        // Если значение null — делаем чекбокс неактивным (серый)
                        binding.cbShoesBox.isEnabled = first.shoesBox != null
                        binding.cbBagBox.isEnabled = first.bagBox != null

                        soundManager.playSuccess()
                    } else {
                        binding.tvClientName.text = getString(R.string.client_not_found)
                        binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                        soundManager.playError()
                    }
                } else {
                    binding.tvClientName.text = getString(R.string.server_error)
                    binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                }
            } catch (e: Exception) {
                //Log.e("FetchKG", "Исключение: ${e.message}", e)
                binding.tvClientName.text = getString(R.string.net_error)
                binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            }
        }
    }

    // ==================== ФИНАЛЬНАЯ ОТПРАВКА ====================
    private fun submitFinalOrder() {
        val track = binding.etTracking.text.toString().trim()
        val kg = binding.etClientCode.text.toString().trim().uppercase()
        val weightStr = binding.etWeight.text.toString().trim()

        // 1. Базовая проверка полей
        if (track.isEmpty() || !kg.startsWith("KG") || weightStr.isEmpty() || currentReceiverGuid == null) {
            Toast.makeText(requireContext(), "Заполните все поля и код клиента", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Подготовка данных веса
        val normalizedWeight = weightStr.replace(',', '.')
        val grossWeight = normalizedWeight.toDoubleOrNull() ?: 0.0

        if (grossWeight <= 0.0) {
            Toast.makeText(requireContext(), "Введите корректный вес", Toast.LENGTH_SHORT).show()
            return
        }

        // 3. Создаем объект запроса
        val req = SaveOrderRequest(
            clientCode = kg,
            clientName = binding.tvClientName.text.toString(),
            trackingNumber = track,
            grossWeight = grossWeight,
            recieverGuid = currentReceiverGuid!!,
            userGuid = currentReceiverGuid!!,
            withBox = if (binding.cbWithBox.isChecked) 1 else 0,
            length = currentLength,
            width  = currentWidth,
            height = currentHeight
        )

        // 4. Проверка лимита веса (8 кг)
        if (grossWeight > 8.0) {
            soundManager.playError() // Звук предупреждения

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.weight_limit_title)
                .setMessage(getString(R.string.weight_limit_message, grossWeight))   // %1$.2f заменится на вес
                .setCancelable(false)
                .setPositiveButton(R.string.weight_limit_positive) { _, _ ->
                    executeSubmit(req)
                }
                .setNegativeButton(R.string.weight_limit_negative) { _, _ ->
                    binding.etWeight.text?.clear()
                    binding.etWeight.requestFocus()
                }
                .show()
        } else {
            // Если вес в пределах нормы — отправляем сразу
            executeSubmit(req)
        }
    }

    // Вспомогательная функция для выполнения самой сетевой отправки
    private fun executeSubmit(req: SaveOrderRequest) {
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Отправка..."

        senderManager.submitOrder(req,
            onSuccess = {
                soundManager.playSuccess()
                Toast.makeText(requireContext(), "ЗАКАЗ УСПЕШНО СОХРАНЕН!", Toast.LENGTH_SHORT).show()
                ScanUtils.clearFieldsAll(binding)
                currentReceiverGuid = null

                // Восстанавливаем кнопку
                binding.btnSubmit.isEnabled = true
                binding.btnSubmit.text = "ОТПРАВИТЬ ЗАКАЗ"
            },
            onError = { error ->
                soundManager.playError()
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()

                // Восстанавливаем кнопку
                binding.btnSubmit.isEnabled = true
                binding.btnSubmit.text = "ОТПРАВИТЬ ЗАКАЗ"
            }
        )
    }
    private fun showSearchClientDialog() {
        val dialogBinding = DialogSearchClientBinding.inflate(layoutInflater)  // ← binding-класс

        AlertDialog.Builder(requireContext())
            .setTitle("Поиск по имени и фамилии")
            .setView(dialogBinding.root)
            .setPositiveButton("Поиск") { _, _ ->
                val first = dialogBinding.etFirstName.text.toString().trim()
                val last = dialogBinding.etLastName.text.toString().trim()
                if (first.isNotEmpty() || last.isNotEmpty()) {
                    searchClientByName(first, last)
                } else {
                    Toast.makeText(requireContext(), "Введите имя или фамилию", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun searchClientByName(first: String, last: String) {
        val searchText = (first + last).trim()
        if (searchText.length < 3) {
            Toast.makeText(requireContext(), "Введите минимум 3 символа", Toast.LENGTH_SHORT).show()
            return
        }

        val progress = ProgressDialog(requireContext()).apply {
            setMessage("Загрузка клиентов...")
            setCancelable(true)
        }
        progress.show()

        lifecycleScope.launch {
            binding.btnSearchClient.isEnabled = false

            try {
                val receivers = getCachedOrLoadReceivers()  // ← новая функция

                // Поиск без учёта регистра, частичное совпадение
                val filtered = receivers.filter {
                    (first.isEmpty() || it.firstName?.contains(first, ignoreCase = true) == true) &&
                            (last.isEmpty() || it.lastName?.contains(last, ignoreCase = true) == true)
                }

                when {
                    filtered.isEmpty() -> {
                        Toast.makeText(requireContext(), "Клиент не найден", Toast.LENGTH_SHORT).show()
                        soundManager.playError()
                    }
                    filtered.size == 1 -> {
                        val match = filtered[0]
                        val code = match.clientCode ?: ""
                        binding.etClientCode.setText(code)
                        binding.etClientCode.requestFocus()
                        binding.etClientCode.setSelection(code.length)
                        binding.etClientCode.onEditorAction(EditorInfo.IME_ACTION_DONE)

                        val name = "${match.firstName ?: ""} ${match.lastName ?: ""}".trim()
                        Toast.makeText(requireContext(), "Найден: $name ($code)", Toast.LENGTH_SHORT).show()
                        soundManager.playSuccess()
                    }
                    else -> {
                        showMultipleResultsDialog(filtered)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Сеть ошибка search", Toast.LENGTH_SHORT).show()
                Log.e("SearchClient", e.message, e)
            } finally {
                progress.dismiss()
                binding.btnSearchClient.isEnabled = true
            }
        }
    }

    /**
     * Загружает список клиентов один раз и кэширует.
     * При повторных вызовах возвращает кэш.
     */
    private suspend fun getCachedOrLoadReceivers(): List<ReceiverItem> {
        // Если кэш уже есть — сразу возвращаем
        cachedReceivers?.let {
            Log.d("SearchClient", "Используем кэш: ${it.size} клиентов")
            return it
        }

        // Загружаем с сервера
        //Log.d("SearchClient", "Загружаем клиентов с сервера...")
        val response = apiService.getAllReceivers()

        if (response.isSuccessful) {
            val list = response.body() ?: emptyList()
            cachedReceivers = list
            //Log.d("SearchClient", "Кэшировано ${list.size} клиентов")
            return list
        } else {
            throw Exception("Ошибка сервера: ${response.code()}")
        }
    }

    private fun showMultipleResultsDialog(results: List<ReceiverItem>) {
        val items = results.mapIndexed { index, item ->
            val name = "${item.firstName ?: ""} ${item.lastName ?: ""}".trim()
            val code = item.clientCode ?: "Нет кода"
            "$name ($code)"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Найдено несколько клиентов")
            .setItems(items) { _, which ->
                val selected = results[which]
                val client = selected.clientCode ?: ""
                // Вставляем код клиента
                binding.etClientCode.setText(client)
                binding.etClientCode.requestFocus()
                binding.etClientCode.setSelection(client.length)
                // Вызываем действие Done
                binding.etClientCode.onEditorAction(EditorInfo.IME_ACTION_DONE)
                val name = "${selected.firstName ?: ""} ${selected.lastName ?: ""}".trim()
                Toast.makeText(requireContext(), "Выбран: $name", Toast.LENGTH_SHORT).show()
                soundManager.playSuccess()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}