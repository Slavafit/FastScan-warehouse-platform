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
import com.ocr.fastscan.data.model.*
import com.ocr.fastscan.databinding.FragmentBoxDetailBinding
import com.ocr.fastscan.utils.AppDependencies
import kotlinx.coroutines.launch
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import com.ocr.fastscan.utils.PrintHelper
import com.ocr.fastscan.utils.ScanUtils
import com.ocr.fastscan.utils.getVolWeightDivider

class BoxDetailFragment : Fragment() {

    private var _binding: FragmentBoxDetailBinding? = null
    private val binding get() = _binding!!

    private val apiService by lazy { AppDependencies.apiService }
    private val firebaseManager by lazy { AppDependencies.firebaseManager }
    private val soundManager by lazy { AppDependencies.soundManager }

    private var boxGuid: String = ""
    private var boxСodeid: String = ""
    private var boxTracking: String = ""
    private var boxWeight: String = ""
    private var partialName: String = ""
    private var currentBox: BoxRequest? = null  // твоя модель коробки (замени на реальную, если отличается)

    private val ordersAdapter = OrdersAdapter { order: OrderDetails ->
        // Удаление заказа
        removeOrderFromBox(order.guid ?: "")
    }

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val value = result.data?.getStringExtra("scan_result") ?: ""
            val mode = result.data?.getStringExtra("scan_mode") ?: ""

            //Log.d("BoxDetail", "Скан трека: mode=$mode, value=$value")

            if (mode == "barcode" && value.isNotEmpty()) {
                addOrderByTrack(value)
            } else {
                Toast.makeText(requireContext(), "Неверный результат сканирования", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        fun newInstance(
            boxGuid: String,
            boxСodeid: String,
            boxTracking: String,
            boxWeight: String,
            partialName: String = "test"
        ):
                BoxDetailFragment {
            val fragment = BoxDetailFragment()
            val args = Bundle()
            args.putString("box_guid", boxGuid)
            args.putString("codeid", boxСodeid)
            args.putString("box_tracking", boxTracking)
            args.putString("box_weight", boxWeight)
            args.putString("partial_name", partialName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        boxGuid = arguments?.getString("box_guid") ?: ""
        boxСodeid = arguments?.getString("codeid") ?: ""
        boxTracking = arguments?.getString("box_tracking") ?: ""
        boxWeight = arguments?.getString("box_weight") ?: ""
        partialName = arguments?.getString("partial_name") ?: "KG"  // ← ключевой момент!


        //Log.d("BoxDetail", "Получено имя партии из аргументов: '$partialName'")
        //Log.d("BoxDetail", "Получено вес из аргументов: '$boxWeight'")

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
        boxWeight = arguments?.getString("gross_weight") ?: ""

        binding.btnSaveSizes.setOnClickListener {
            val newWeightStr = binding.etGrossWeight.text.toString().trim()
            val newWeight = newWeightStr.toDoubleOrNull()

            if (newWeight == null || newWeight <= 0) {
                Toast.makeText(requireContext(), "Введите корректный вес", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Обновляем UI
            binding.tvGrossWeight.text = "Вес: %,2f кг".format(newWeight)

        }

        binding.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrders.adapter = ordersAdapter

        setupClickListeners()
        setupSizeListeners()    // Авто-расчёт v_weight при вводе размеров

        loadBoxInfo()  // ← функция для загрузки данных коробки (размеры, веса)
        loadOrdersInBox()  // ← загрузка списка заказов
        loadPartialName() //функция запроса имени партии
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
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val response = apiService.getOrderDetailsBox(boxTracking)  // boxTracking = tracking_code коробки
                if (response.isSuccessful && response.body() != null) {
                    val orders = response.body()!!  // список заказов
                    ordersAdapter.submitList(orders)

                    // Считаем суммарный вес
                    val totalGrossWeight = orders.sumOf { it.grossWeight?.toDouble() ?: 0.0 }
                    // обновляем UI
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

     private fun calculateVWeight(): Double {
        val length = binding.etLength.text.toString().toIntOrNull() ?: 70
        val width = binding.etWidth.text.toString().toIntOrNull() ?: 45
        val height = binding.etHeight.text.toString().toIntOrNull() ?: 50
        val divider = PreferenceManager.getDefaultSharedPreferences(requireContext()).getInt("divider", 6000)
        return (length * width * height) / divider.toDouble()
    }

    private fun updateSizes() {
        val lengthStr = binding.etLength.text.toString().trim()
        val widthStr = binding.etWidth.text.toString().trim()
        val heightStr = binding.etHeight.text.toString().trim()
        val weightStr = binding.etGrossWeight.text.toString().trim()

        val length = lengthStr.toIntOrNull() ?: 70
        val width = widthStr.toIntOrNull() ?: 45
        val height = heightStr.toIntOrNull() ?: 50
        val weight = weightStr.toDoubleOrNull() ?: 0.0
        // Валидация
        if (length <= 0 || width <= 0 || height <= 0 ) {
            Toast.makeText(requireContext(), "Все значения должны быть положительными",
                Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = BoxRequest(
                    guid = boxGuid,  // ← обязательно для обновления!
                    codeid = boxСodeid, // ← обязательно для обновления!
                    trackingCode = boxTracking,
                    width = width,
                    height = height,
                    length = length,
                    weight = weight,
                    vWeight = calculateVWeight(),
                    )
                Log.d("BoxDetail", "Отправляем обновление: $request")
                val response = apiService.createOrUpdateBox(request)
                Log.d("BoxDetail", "response: $response")

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(requireContext(), "Размеры и вес сохранены", Toast.LENGTH_SHORT).show()
                    // Обновляем локальную модель (если она есть)
                    currentBox = currentBox?.copy(
                        width = width,
                        height = height,
                        length = length,
                        weight = weight,
                        vWeight = calculateVWeight()
                    )


                    binding.tvGrossWeight.text = "Вес: %.2f кг".format(weight)

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
                val vWeight = (length * width * height) / divider

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
    // 1. Полный текст из UI (логируем для отладки)
    //Log.d("printHelper", "Полный текст веса из UI: '$fullWeightText', Имя партии перед печатью: '$partialName'")
    val length = binding.etLength.text.toString().toIntOrNull()
    val width = binding.etWidth.text.toString().toIntOrNull()
    val height = binding.etHeight.text.toString().toIntOrNull()
    val fullWeightText = binding.tvGrossWeight.text.toString()

    // 2. Очень надёжная очистка веса (убираем всё лишнее)
    val weightClean = fullWeightText
        .replace("Общий вес:", "", ignoreCase = true)
        .replace("Вес:", "", ignoreCase = true)
        .replace(" кг", "", ignoreCase = true)
        .replace("kg", "", ignoreCase = true)
        .replace(",", ".")          // запятая → точка (русский формат)
        .replace("\\s+", "")        // все пробелы убираем
        .trim()

    // 3. Проверяем, что вес — это число (защита от краша в PrintHelper)
    val weightValue = weightClean.toDoubleOrNull()
    if (weightValue == null || weightValue <= 0) {
        Log.e("printHelper", "Некорректный вес: '$weightClean'")
        Toast.makeText(requireContext(), "Некорректный вес для печати", Toast.LENGTH_SHORT).show()
        soundManager.playError()
        return
    }

    // 4. Трек-номер (убираем лишние пробелы)
    val trimmedTracking = boxTracking.trim()

    // 5. last4 — всегда 4 символа (дополняем нулями слева, если короткий)
    val last4 = if (trimmedTracking.isNotEmpty()) {
        trimmedTracking.takeLast(4).padStart(4, '0')
    } else {
        "0000"  // fallback на случай пустого трека
    }

    //Log.d("printHelper", "Параметры для печати: track='$trimmedTracking', weight='$weightClean', " +
            //"last4='$last4', partial='$partialName', length='$length', width='$width', height='$height'")

    // 6. Печать
    try {

        val printHelper = PrintHelper(requireContext())
        printHelper.printBoxLabel(
            trimmedTracking,
            weightClean,
            last4,
            partialName,
            length = length.toString(),
            width = width.toString(),
            height = height.toString()
            )
        soundManager.playSuccess()
        Toast.makeText(requireContext(), "Этикетка отправлена на печать", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("printHelper", "Ошибка печати этикетки", e)
        Toast.makeText(requireContext(), "Ошибка печати: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        soundManager.playError()
    }
}
    private fun addOrderByTrack(tcode: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                //Log.d("AddOrder", "Шаг 1: Поиск заказа по треку $tcode")
                val searchResponse = apiService.searchOrder(tcode)

                if (!searchResponse.isSuccessful || searchResponse.body().isNullOrEmpty()) {
                    val msg = if (!searchResponse.isSuccessful) "Ошибка поиска: ${searchResponse.code()}" else "Заказ не найден"
                    //Log.e("AddOrder", msg)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    soundManager.playError()
                    return@launch
                }

                val order = searchResponse.body()!![0]
                val orderGuid = order.guid ?: run {
                    //Log.e("AddOrder", "У заказа нет GUID")
                    Toast.makeText(requireContext(), "У заказа нет GUID", Toast.LENGTH_SHORT).show()
                    soundManager.playError()
                    return@launch
                }

                //Log.d("AddOrder", "Шаг 2: Добавляем заказ $orderGuid в коробку $boxGuid")

                val addRequest = BoxOrderRequest(
                    order = orderGuid,
                    box = boxGuid,
                    desc = "España BMG"  // короткое описание
                )

                val addResponse = apiService.addOrderToBox(addRequest)

                //Log.d("AddOrder", "Ответ сервера: код ${addResponse.code()}, тело: ${addResponse.body()}")

                if (addResponse.isSuccessful) {
                    val rawBody = addResponse.body()

                    // Успех: массив с GUID
                    if (rawBody is List<*> && rawBody.isNotEmpty()) {
                        val firstItem = rawBody[0] as? Map<*, *>
                        val newGuid = firstItem?.get("guid") as? String

                        //Log.d("AddOrder", "Успех: добавлен GUID $newGuid")
                        Toast.makeText(requireContext(), "Заказ добавлен", Toast.LENGTH_SHORT).show()
                        soundManager.playSuccess()
                        loadBoxDetails()

                        // Firebase
                        val entry = BoxEntry(
                            orderTracking = tcode,
                            clientCode = order.clientCode,
                            client = order.client,
                            boxTracking = boxTracking,
                            date = Timestamp.now(),
                            orderWeight = order.grossWeight ?: 0.0
                        )
                        firebaseManager.saveBoxEntry(
                            entry = entry,
                            onSuccess = {
                                //Log.d("AddOrder", "Запись в Firebase прошла успешно")
                                // Здесь можно обновить UI, если нужно (например, перезагрузить список коробок)
                            },
                            onFailure = { e ->
                                Log.e("AddOrder", "Ошибка записи в Firebase", e)
                                Toast.makeText(requireContext(), "Не удалось сохранить в Firebase: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                soundManager.playError()
                            }
                        )
                    }
                    // Ошибка: объект с сообщением
                    else if (rawBody is Map<*, *>) {
                        val success = rawBody["success"] as? Boolean ?: false
                        val message = rawBody["message"] as? String
                            ?: rawBody["error"] as? String
                            ?: "Неизвестная ошибка"

                        if (success) {
                            //Log.d("AddOrder", "Успех в объектном формате")
                            Toast.makeText(requireContext(), "Заказ добавлен", Toast.LENGTH_SHORT).show()
                            soundManager.playSuccess()
                            loadBoxDetails()
                        } else {
                            //Log.e("AddOrder", "Ошибка от сервера: $message")
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                            soundManager.playError()
                        }
                    }
                    // Неизвестный формат
                    else {
                        Log.e("AddOrder", "Неожиданный формат ответа: $rawBody")
                        Toast.makeText(requireContext(), "Неизвестный ответ сервера", Toast.LENGTH_LONG).show()
                        soundManager.playError()
                    }
                } else {
                    val errorBody = addResponse.errorBody()?.string() ?: "Нет тела ошибки"
                    Log.e("AddOrder", "Ошибка сервера: код ${addResponse.code()}, тело: $errorBody")
                    Toast.makeText(requireContext(), "Ошибка сервера: ${addResponse.code()}", Toast.LENGTH_LONG).show()
                    soundManager.playError()
                }
            } catch (e: Exception) {
                Log.e("AddOrder", "Сетевая/другая ошибка при добавлении заказа", e)
                Toast.makeText(requireContext(), "Сеть/ошибка: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                soundManager.playError()
            }
        }
    }
    private fun loadBoxInfo() {
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
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
                    binding.etGrossWeight.setText(box.grossWeight?.toString() ?: "0.0")

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
    //всегда загружать имя партии по коробке
    private fun loadPartialName() {
        viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val response = apiService.getOpenPartials()
                    if (response.isSuccessful) {
                        val parties = response.body() ?: emptyList()
                        //Log.d("BoxDetail", "Получено ${parties.size} партий: $parties")
                        if (parties.isNotEmpty()) {
                            partialName = parties.first().partial_name.trim()
                            //Log.d("BoxDetail", "Взято первое имя партии: '$partialName'")
                        } else {
                        //Log.e("BoxDetail", "Ошибка API: ${response.code()} ")
                        Toast.makeText(requireContext(), "Ошибка загрузки заказов", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BoxDetail", "Ошибка сети", e)
                    Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}