package com.ocr.fastscan.ui

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.ocr.fastscan.data.model.BoxEntry
import com.ocr.fastscan.data.model.BoxOrderRequest
import com.ocr.fastscan.data.model.BoxRequest
import com.ocr.fastscan.data.model.OrderInBox
import com.ocr.fastscan.data.model.RemoveOrderRequest
import com.ocr.fastscan.databinding.FragmentBoxDetailBinding
import com.ocr.fastscan.utils.AppDependencies
import kotlinx.coroutines.launch
import android.text.TextWatcher
import android.util.Log
import com.ocr.fastscan.data.model.OrderDetails
import com.ocr.fastscan.utils.ScanUtils
import com.ocr.fastscan.utils.getVolWeightDivider

class BoxDetailFragment : Fragment() {

    private var _binding: FragmentBoxDetailBinding? = null
    private val binding get() = _binding!!

    private val apiService = AppDependencies.apiService
    private val firebaseManager = AppDependencies.firebaseManager
    private val soundManager = AppDependencies.soundManager
    private val printHelper = AppDependencies.printHelper

    private var boxGuid: String = ""
    private var boxTracking: String = ""

    private val ordersAdapter = OrdersAdapter { order: OrderDetails ->
        // Удаление заказа
        removeOrderFromBox(order.guid ?: "")
    }

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val value = result.data?.getStringExtra("scan_result") ?: ""
            val mode = result.data?.getStringExtra("scan_mode") ?: ""

            Log.d("BoxDetail", "Скан трека: mode=$mode, value=$value")

            if (mode == "barcode" && value.isNotEmpty()) {
                addOrderByTrack(value)
            } else {
                Toast.makeText(requireContext(), "Неверный результат сканирования", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        fun newInstance(boxGuid: String, boxTracking: String): BoxDetailFragment {
            val fragment = BoxDetailFragment()
            val args = Bundle()
            args.putString("box_guid", boxGuid)
            args.putString("box_tracking", boxTracking)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBoxDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        boxGuid = arguments?.getString("box_guid") ?: ""
        boxTracking = arguments?.getString("box_tracking") ?: ""

        binding.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrders.adapter = ordersAdapter

        setupClickListeners()
        setupSizeListeners()    // Авто-расчёт v_weight при вводе размеров

        loadBoxInfo()  // ← новая функция для загрузки данных коробки (размеры, веса)
        loadOrdersInBox()  // ← загрузка списка заказов
    }
    private fun setupClickListeners() {
        binding.btnAddOrder.setOnClickListener {
            ScanUtils.launchCamera(requireContext(), "barcode", scanLauncher)
        }

        binding.btnSaveSizes.setOnClickListener {
            updateSizes()
        }

        binding.btnPrint.setOnClickListener {
            printLabel()
        }

        binding.btnRefresh.setOnClickListener {
            loadBoxInfo()
            loadOrdersInBox()
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun loadBoxDetails() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = apiService.getOrderDetailsBox(boxTracking)  // boxTracking = tracking_code коробки
                if (response.isSuccessful && response.body() != null) {
                    val orders = response.body()!!  // список заказов
                    ordersAdapter.submitList(orders)

                    // Считаем суммарный вес
                    val totalGrossWeight = orders.sumOf { it.grossWeight?.toDouble() ?: 0.0 }

                    binding.tvGrossWeight.text = "Общий вес: %.2f кг".format(totalGrossWeight)
                    binding.tvOrderCount.text = "Заказов: ${orders.size}"

                    // Пересчитываем v_weight (если размеры уже загружены или дефолтные)
                    binding.tvVWeight.text = "Объёмный вес: %.2f кг".format(calculateVWeight())

                } else {
                    Toast.makeText(requireContext(), "Ошибка загрузки деталей коробки", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun calculateGrossWeight(orders: List<OrderInBox>): Double {
        return orders.sumOf { it.weight ?: 0.0 }
    }

    private fun calculateVWeight(): Double {
        val length = binding.etLength.text.toString().toIntOrNull() ?: 70
        val width = binding.etWidth.text.toString().toIntOrNull() ?: 45
        val height = binding.etHeight.text.toString().toIntOrNull() ?: 50
        val divider = PreferenceManager.getDefaultSharedPreferences(requireContext()).getInt("divider", 6000)
        return (length * width * height) / divider.toDouble()
    }

    private fun updateSizes() {
        val length = binding.etLength.text.toString().toIntOrNull() ?: 70
        val width = binding.etWidth.text.toString().toIntOrNull() ?: 45
        val height = binding.etHeight.text.toString().toIntOrNull() ?: 50
        lifecycleScope.launch {
            try {
                val request = BoxRequest(boxTracking, width, height, length)
                val response = apiService.createOrUpdateBox(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(requireContext(), "Размеры сохранены", Toast.LENGTH_SHORT).show()
                    loadBoxInfo()  // перезагружаем данные коробки
                } else {
                    Toast.makeText(requireContext(), "Ошибка сохранения размеров", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
            }
        }
    }


    //авто-расчёт v_weight при изменении полей
    private fun setupSizeListeners() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val length = binding.etLength.text.toString().toIntOrNull() ?: 70
                val width = binding.etWidth.text.toString().toIntOrNull() ?: 45
                val height = binding.etHeight.text.toString().toIntOrNull() ?: 50


                val divider = requireContext().getVolWeightDivider()  // всегда Double, минимум 6000.0
                val vWeight = (length * width * height) / divider.toDouble()

                binding.tvVWeight.text = "Объёмный вес: $vWeight кг"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        binding.etLength.addTextChangedListener(watcher)
        binding.etWidth.addTextChangedListener(watcher)
        binding.etHeight.addTextChangedListener(watcher)
    }

    private fun removeOrderFromBox(orderGuid: String) {
        lifecycleScope.launch {
            try {
                val request = RemoveOrderRequest(orderGuid)
                val response = apiService.removeOrderFromBox(request)
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Заказ удалён", Toast.LENGTH_SHORT).show()
                    loadBoxDetails()
                } else {
                    Toast.makeText(requireContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
            }
        }
    }

private fun printLabel() {
    // 1. Полный текст из UI
    val fullWeightText = binding.tvGrossWeight.text.toString()
    Log.d("printHelper", "Полный текст веса из UI: '$fullWeightText'")

    // 2. Надёжная очистка веса (убираем всё лишнее, меняем запятую на точку)
    val weightClean = fullWeightText
        .replace("Общий вес: ", "")
        .replace("Вес: ", "")
        .replace(" кг", "")
        .replace("kg", "")
        .replace(",", ".")  // ← ключевой фикс для русского формата
        .trim()

    // 3. last4 — с защитой от пробелов и коротких строк
    val trimmedTracking = boxTracking.trim()  // убираем пробелы в конце
    val last4 = if (trimmedTracking.length >= 4) {
        trimmedTracking.takeLast(4)
    } else {
        trimmedTracking.padStart(4, '0')  // если меньше 4 символов — дополняем нулями
    }
    // 4. Печать
    printHelper.printBoxLabel(trimmedTracking, weightClean, last4)
    soundManager.playSuccess()
}
    private fun addOrderByTrack(tcode: String) {
        lifecycleScope.launch {
            try {
                val response = apiService.searchOrder(tcode)
                if (response.isSuccessful && response.body() != null && response.body()!!.isNotEmpty()) {
                    val order = response.body()!![0]
                    val orderGuid = order.guid ?: return@launch

                    // Добавляем заказ в коробку
                    val addRequest = BoxOrderRequest(
                        order = orderGuid,
                        box = boxGuid,  // guid коробки из аргументов фрагмента
                        desc = "Добавлен по скану"
                    )

                    val addResponse = apiService.addOrderToBox(addRequest)
                    if (addResponse.isSuccessful && addResponse.body()?.success == true) {
                        Toast.makeText(requireContext(), "Заказ добавлен", Toast.LENGTH_SHORT).show()
                        soundManager.playSuccess()
                        loadBoxDetails()  // обновляем список заказов и веса
                        // Сохраняем в Firebase Boxes
                        val entry = BoxEntry(
                            orderTracking = tcode,
                            boxTracking = boxTracking,
                            date = Timestamp.now(),
                            orderWeight = order.grossWeight ?: 0.0
                        )
                        firebaseManager.saveBoxEntry(entry)
                    } else {
                        Toast.makeText(requireContext(), "Ошибка добавления заказа", Toast.LENGTH_SHORT).show()
                        soundManager.playError()
                    }
                } else {
                    Toast.makeText(requireContext(), "Заказ не найден", Toast.LENGTH_SHORT).show()
                    soundManager.playError()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
                soundManager.playError()
            }
        }
    }
    private fun loadBoxInfo() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = apiService.getBoxes(tCode = boxTracking)  // GET /api/order/box?tCode=tracking_code
                Log.d("loadBoxInfo", "Ответ код: ${response.code()}, успешный: ${response.isSuccessful}")
                if (response.isSuccessful && response.body() != null && response.body()!!.isNotEmpty()) {
                    val box = response.body()!![0]  // берём первую (единственную) коробку по tCode

                    binding.tvBoxNumber.text = "Номер коробки: ${boxTracking}"

                    // Заполняем поля размеров
                    binding.etLength.setText(box.length?.toString() ?: "70")
                    binding.etWidth.setText(box.width?.toString() ?: "45")
                    binding.etHeight.setText(box.height?.toString() ?: "50")

                    // Показываем веса из API
                    binding.tvGrossWeight.text = "Общий вес: %.2f кг".format(box.grossWeight ?: 0.0)
                    binding.tvVWeight.text = "Объёмный вес: %.2f кг".format(box.vWeight ?: 0.0)

                    // Если размеры изменились — пересчитываем v_weight локально
                    //setupSizeListeners()
                } else {
                    Log.e("loadBoxInfo", "Ошибка API: ${response.code()} - ${response.message()}")
                    Toast.makeText(requireContext(), "Ошибка загрузки данных коробки", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("loadBoxInfo", "Сеть/исключение: ${e.message}", e)
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadOrdersInBox() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = apiService.getOrderDetailsBox(boxTracking) ///api/order/order-tracking-code
                if (response.isSuccessful && response.body() != null) {
                    val orders = response.body()!!
                    ordersAdapter.submitList(orders)

                    val totalGrossWeight = orders.sumOf { it.grossWeight?.toDouble() ?: 0.0 }
                    binding.tvGrossWeight.text = "Общий вес: %.2f кг".format(totalGrossWeight)
                    binding.tvOrderCount.text = "Заказов: ${orders.size}"

                    // Пересчитываем v_weight (если размеры уже загружены или дефолтные)
                    binding.tvVWeight.text = "Объёмный вес: %.2f кг".format(calculateVWeight())
                } else {
                    Toast.makeText(requireContext(), "Ошибка загрузки заказов", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}