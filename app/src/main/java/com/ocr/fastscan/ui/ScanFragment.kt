package com.ocr.fastscan.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
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
import androidx.lifecycle.lifecycleScope
import com.ocr.fastscan.R
import com.ocr.fastscan.SettingsActivity
import com.ocr.fastscan.data.model.*
import com.ocr.fastscan.data.ReceiverCacheRepository
import com.ocr.fastscan.data.SenderManager
import com.ocr.fastscan.databinding.FragmentScanBinding
import com.ocr.fastscan.utils.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.ocr.fastscan.databinding.DialogSearchClientBinding

class ScanFragment : BaseFragment() {

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
    private val searchOrder by lazy { SearchOrder(this) }
    private val printHelper by lazy { PrintHelper(requireActivity()) }
    private val senderManager by lazy {
        SenderManager(
            apiService = apiService,
            firebaseManager = firebaseManager,
            context = requireContext()
        )
    }
    private var currentLength: Int = 0
    private var currentWidth: Int = 0
    private var currentHeight: Int = 0
    private var currentOrderDimensions: OrderDimensions? = null
    private var isFetchingWeight = false

    private val receiverCacheRepository by lazy {
        ReceiverCacheRepository(requireContext(), apiService)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.tvAppVersion.text = getAppVersionText()

        binding.etTracking.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                val tcode = binding.etTracking.text.toString().trim()
                if (tcode.isNotEmpty()) {
                    fetchOrderInfo(tcode)
                    hideKeyboard(binding.etTracking)
                    true
                } else false
            } else false
        }
        setupButtons()
        setupClientCodeInput()
    }

    private fun getAppVersionText(): String {
        return try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            getString(
                R.string.app_version_summary,
                packageInfo.versionName ?: "-",
                packageInfo.versionCodeCompat()
            )
        } catch (e: Exception) {
            getString(R.string.app_version_summary, "-", 0L)
        }
    }

    private fun PackageInfo.versionCodeCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
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
            fetchWeightFromServer()
        }

        binding.btnVolumeCalc.setOnClickListener {
            DialogUtils.showVolumeDialog(requireContext()) { result ->
                currentOrderDimensions = result
                binding.etWeight.setText("%.2f".format(result.grossWeight))
                
                // Отображаем размеры в UI
                currentLength = result.length
                currentWidth  = result.width
                currentHeight = result.height
                
                binding.etLength.setText(currentLength.toString())
                binding.etWidth.setText(currentWidth.toString())
                binding.etHeight.setText(currentHeight.toString())
            }
        }

        // Добавляем возможность удаления размеров долгим нажатием
        binding.btnVolumeCalc.setOnLongClickListener {
            clearDimensions()
            Toast.makeText(requireContext(), R.string.dimensions_cleared, Toast.LENGTH_SHORT).show()
            true
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

        binding.btnGlobalSearch.setOnClickListener {
            searchOrder.showSearchDialog()
        }

        binding.btnSearchClient.setOnClickListener {
            showSearchClientDialog()
        }
    }

    private fun clearDimensions() {
        currentLength = 0
        currentWidth = 0
        currentHeight = 0
        currentOrderDimensions = null
        binding.etLength.text?.clear()
        binding.etWidth.text?.clear()
        binding.etHeight.text?.clear()
    }

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
                if (code.startsWith("KG") && code.length >= 4) {
                    fetchClientByKgCode(code)
                    hideKeyboard(binding.etClientCode)
                    true
                } else {
                    Toast.makeText(requireContext(), getString(R.string.input_4_numbers), Toast.LENGTH_SHORT).show()
                    false
                }
            } else false
        }
    }

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

    private fun fetchOrderInfo(tcode: String) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val response = apiService.getOrderDetails(tcode)
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    val details = response.body()!![0]
                    binding.tvClientName.text = details.clientName ?: getString(R.string.name_not_specified)
                    binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                    binding.etClientCode.setText(details.clientCode ?: "")
                    binding.etWeight.setText(details.grossWeight?.toString() ?: "")
                    
                    // Обновляем UI и внутренние переменные
                    currentLength = details.length ?: 0
                    currentWidth = details.width ?: 0
                    currentHeight = details.height ?: 0
                    
                    binding.etLength.setText(details.length?.toString() ?: "")
                    binding.etWidth.setText(details.width?.toString() ?: "")
                    binding.etHeight.setText(details.height?.toString() ?: "")
                    
                    binding.cbWithBox.isChecked = details.withBox == "1"
                    currentReceiverGuid = details.userGuid
                    soundManager.playSuccess()
                } else {
                    binding.tvClientName.text = getString(R.string.track_not_found)
                    binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
                    ScanUtils.clearFields(binding, requireContext())
                    clearDimensions()
                }
            } catch (e: Exception) {
                Log.e("FetchInfo", "Error: ${e.message}")
                Toast.makeText(requireContext(), R.string.net_error_short, Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun fetchClientByKgCode(kgCode: String) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val response = apiService.getReceiverByCode(kgCode)
                if (response.isSuccessful && response.body() != null) {
                    val receivers = response.body()!!
                    if (receivers.isNotEmpty()) {
                        val first = receivers[0]
                        binding.tvClientName.text = first.fullName?.trim() ?: getString(R.string.name_not_specified)
                        binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                        currentReceiverGuid = first.recieverGuid
                        binding.cbShoesBox.isChecked = first.shoesBox == 1
                        binding.cbBagBox.isChecked = first.bagBox == 1
                        soundManager.playSuccess()
                        fetchWeightFromServer()
                    } else {
                        binding.tvClientName.text = getString(R.string.client_not_found)
                        binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                        soundManager.playError()
                    }
                }
            } catch (e: Exception) {
                binding.tvClientName.text = getString(R.string.net_error)
                binding.tvClientName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun submitFinalOrder() {
        val track = binding.etTracking.text.toString().trim()
        val kg = binding.etClientCode.text.toString().trim().uppercase()
        val weightStr = binding.etWeight.text.toString().trim()

        if (track.isEmpty() || !kg.startsWith("KG") || weightStr.isEmpty() || currentReceiverGuid == null) {
            Toast.makeText(requireContext(), getString(R.string.input_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        val grossWeight = weightStr.replace(',', '.').toDoubleOrNull() ?: 0.0
        if (grossWeight <= 0.0) {
            Toast.makeText(requireContext(), getString(R.string.input_weight), Toast.LENGTH_SHORT).show()
            return
        }

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

        if (grossWeight > 8.0) {
            soundManager.playWarning()
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.weight_limit_title)
                .setMessage(getString(R.string.weight_limit_message, grossWeight))
                .setCancelable(false)
                .setPositiveButton(R.string.weight_limit_positive) { _, _ -> executeSubmit(req) }
                .setNegativeButton(R.string.weight_limit_negative) { _, _ ->
                    binding.etWeight.text?.clear()
                    binding.etWeight.requestFocus()
                }
                .show()
        } else {
            executeSubmit(req)
        }
    }

    private fun executeSubmit(req: SaveOrderRequest) {
        binding.btnSubmit.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        senderManager.submitOrder(req,
            onSuccess = {
                binding.progressBar.visibility = View.GONE
                soundManager.playSent()
                Toast.makeText(requireContext(), getString(R.string.order_saved), Toast.LENGTH_SHORT).show()
                ScanUtils.clearFieldsAll(binding, requireContext())
                clearDimensions() // Сбрасываем размеры после отправки
                currentReceiverGuid = null
                binding.btnSubmit.isEnabled = true
            },
            onError = { error ->
                binding.progressBar.visibility = View.GONE
                soundManager.playError()
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                binding.btnSubmit.isEnabled = true
            }
        )
    }

    private fun showSearchClientDialog() {
        val dialogBinding = DialogSearchClientBinding.inflate(layoutInflater)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.search_client_title)
            .setCancelable(false)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.search, null) // Обработаем клик вручную для скрытия клавиатуры
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val searchBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            searchBtn.setOnClickListener {
                // Прячем клавиатуру перед выполнением поиска
                hideKeyboard(dialog.currentFocus ?: dialogBinding.root)

                val first = dialogBinding.etFirstName.text.toString().trim()
                val last = dialogBinding.etLastName.text.toString().trim()
                if (first.isNotEmpty() || last.isNotEmpty()) {
                    searchClientByName(first, last)
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.input_name_surname), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Позволяем запустить поиск кнопкой Enter/Search на клавиатуре
        val editorActionListener = android.widget.TextView.OnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v)
                val first = dialogBinding.etFirstName.text.toString().trim()
                val last = dialogBinding.etLastName.text.toString().trim()
                if (first.isNotEmpty() || last.isNotEmpty()) {
                    searchClientByName(first, last)
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.input_name_surname), Toast.LENGTH_SHORT).show()
                }
                true
            } else false
        }
        
        dialogBinding.etFirstName.imeOptions = EditorInfo.IME_ACTION_SEARCH
        dialogBinding.etLastName.imeOptions = EditorInfo.IME_ACTION_SEARCH
        dialogBinding.etFirstName.setOnEditorActionListener(editorActionListener)
        dialogBinding.etLastName.setOnEditorActionListener(editorActionListener)

        dialog.show()
    }

    private fun searchClientByName(first: String, last: String) {
        if ((first + last).trim().length < 3) {
            Toast.makeText(requireContext(), getString(R.string.input_3_chars), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnSearchClient.isEnabled = false
            try {
                val receivers = getCachedOrLoadReceivers()
                val filtered = receivers.filter {
                    (first.isEmpty() || it.firstName?.contains(first, ignoreCase = true) == true) &&
                    (last.isEmpty() || it.lastName?.contains(last, ignoreCase = true) == true)
                }

                when {
                    filtered.isEmpty() -> {
                        Toast.makeText(requireContext(), getString(R.string.no_client_found), Toast.LENGTH_SHORT).show()
                        soundManager.playWarning()
                    }
                    filtered.size == 1 -> {
                        val match = filtered[0]
                        binding.etClientCode.setText(match.clientCode ?: "")
                        binding.etClientCode.onEditorAction(EditorInfo.IME_ACTION_DONE)
                        soundManager.playSuccess()
                    }
                    else -> showMultipleResultsDialog(filtered)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.net_error_short, Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnSearchClient.isEnabled = true
            }
        }
    }

    private suspend fun getCachedOrLoadReceivers(): List<ReceiverItem> {
        return receiverCacheRepository.getReceivers()
    }

    private fun showMultipleResultsDialog(results: List<ReceiverItem>) {
        val items = results.map { "${it.firstName} ${it.lastName} (${it.clientCode})" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clients_found)
            .setItems(items) { _, which ->
                val selected = results[which]
                binding.etClientCode.setText(selected.clientCode ?: "")
                binding.etClientCode.onEditorAction(EditorInfo.IME_ACTION_DONE)
                soundManager.playSuccess()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun fetchWeightFromServer() {
        if (isFetchingWeight) return
        isFetchingWeight = true
        binding.btnScanWeight.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val weightStr = withContext(Dispatchers.IO) { WeightServerHelper.getWeight(requireContext()) }
                if (weightStr != null) {
                    binding.etWeight.setText(weightStr)
                    soundManager.playSuccess()
                } else {
                    Toast.makeText(requireContext(), R.string.weight_fetch_error, Toast.LENGTH_SHORT).show()
                    soundManager.playError()
                }
            } finally {
                isFetchingWeight = false
                binding.btnScanWeight.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
