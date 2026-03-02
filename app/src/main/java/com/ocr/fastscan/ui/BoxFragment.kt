package com.ocr.fastscan.ui

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.BoxRequest
import com.ocr.fastscan.databinding.DialogAddBoxBinding
import com.ocr.fastscan.databinding.FragmentBoxBinding
import com.ocr.fastscan.utils.AppDependencies
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BoxFragment : Fragment() {

    private var _binding: FragmentBoxBinding? = null
    private val binding get() = _binding!!

    private val apiService = AppDependencies.apiService

    private val soundManager = AppDependencies.soundManager

    private var isShowingAllBoxes = false  // флаг для "Показать все"

    private val boxesAdapter = BoxesAdapter { box ->
        // Клик по коробке — открываем детальный экран
        val detailFragment = BoxDetailFragment.newInstance(
            box.guid ?: "",
            box.trackingCode ?: ""
        )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    private var isCreating = false  // флаг, чтобы избежать повторных запусков

    private var loadBoxesCallCount = 0


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBoxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.tvTitle.text = "Открытые коробки"  // Использование tvTitle

        binding.rvBoxes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBoxes.adapter = boxesAdapter

        loadBoxes(isOpenOnly = true)

        binding.btnRefresh.setOnClickListener {
            loadBoxes(isOpenOnly = isShowingAllBoxes)
        }

        binding.btnAddNewBox.setOnClickListener {
            showAddNewBoxDialog()
        }

        binding.btnShowAll.setOnClickListener {
            isShowingAllBoxes = !isShowingAllBoxes
            binding.btnShowAll.text = if (isShowingAllBoxes) "Показать открытые" else "Показать все"
            binding.tvTitle.text = if (isShowingAllBoxes) "Все коробки" else "Открытые коробки"
            loadBoxes(isOpenOnly = !isShowingAllBoxes)
        }
    }

    private fun loadBoxes(isOpenOnly: Boolean = true) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvNoBoxes.visibility = View.GONE

            loadBoxesCallCount++
            Log.d("BoxFragment", "loadBoxes вызван #$loadBoxesCallCount, isOpenOnly=$isOpenOnly")

            if (loadBoxesCallCount > 10) {
                Log.e("BoxFragment", "ОБНАРУЖЕНА ПЕТЛЯ! Более 10 вызовов loadBoxes подряд")
                return
            }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("BoxDialog", "Загрузка коробок: isOpenOnly=$isOpenOnly")
                Log.d("BoxDialog", "Загружаем список коробок")
                if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    loadBoxes(isOpenOnly = true)
                } else {
                    Log.w("BoxDialog", "View уничтожен — пропускаем loadBoxes")
                }
                val response = apiService.getBoxes(status = if (isOpenOnly) 0 else null)
                //Log.d("BoxFragment", "Ответ код: ${response.code()}, успешный: ${response.isSuccessful}")
                if (response.isSuccessful && response.body() != null) {
                    val boxes = response.body()!!.map { box ->
                        box.copy(
                            dateCreated = formatBoxDate(box.dateCreated)  // ← форматируем
                        )
                    }
                    //Log.d("BoxFragment", "Получено коробок: ${boxes.size}")
                    boxesAdapter.submitList(boxes)
                    if (boxes.isEmpty()) {
                        binding.tvNoBoxes.visibility = View.VISIBLE
                    }
                } else {
                    //Log.e("BoxFragment", "Ошибка API: ${response.code()} - ${response.message()}")
                    Toast.makeText(requireContext(), "Ошибка загрузки коробок", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("BoxFragment", "Сеть/исключение: ${e.message}", e)
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

private fun showAddNewBoxDialog() {
    Log.d("BoxDialog", "Открыт диалог создания коробки")

    val dialogBinding = DialogAddBoxBinding.inflate(layoutInflater)
    Log.d("BoxDialog", "Dialog binding создан")

    // Генерируем tracking_code
    val generatedTrackingCode = generateTrackingCode()
    dialogBinding.tvTrackingCode.text = "Будет создан номер: $generatedTrackingCode"
    Log.d("BoxDialog", "Сгенерирован tracking_code: $generatedTrackingCode")

    // Создаём диалог
    val dialog = AlertDialog.Builder(requireContext())
        .setView(dialogBinding.root)
        .setTitle("Новая коробка")
        .setPositiveButton("Создать", null)
        .setNegativeButton("Отмена", null)
        .create()

    Log.d("BoxDialog", "AlertDialog создан")

    // Дефолтные размеры
    dialogBinding.etLength.setText("70")
    dialogBinding.etWidth.setText("45")
    dialogBinding.etHeight.setText("50")
    Log.d("BoxDialog", "Установлены дефолтные размеры")

    // Переопределяем кнопку "Создать"
    dialog.setOnShowListener {
        Log.d("BoxDialog", "Диалог показан, переопределяем кнопку Create")


        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { v ->
            Log.d("BoxDialog", "Нажата кнопка 'Создать'")

            if (isCreating) return@setOnClickListener  // если уже в процессе — игнор

            v.isEnabled = false  // отключить кнопку
            isCreating = true

            val length = dialogBinding.etLength.text.toString().toIntOrNull() ?: 70
            val width = dialogBinding.etWidth.text.toString().toIntOrNull() ?: 45
            val height = dialogBinding.etHeight.text.toString().toIntOrNull() ?: 50
            Log.d("BoxDialog", "Размеры: L=$length, W=$width, H=$height")

            if (length <= 0 || width <= 0 || height <= 0) {
                Log.w("BoxDialog", "Некорректные размеры")
                Toast.makeText(requireContext(), "Размеры должны быть больше 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                Log.d("BoxDialog", "Запускаем корутину создания коробки")
                try {
                    val request = BoxRequest(
                        trackingCode = generatedTrackingCode,
                        width = width,
                        height = height,
                        length = length
                    )
                    Log.d("BoxDialog", "Запрос сформирован: $request")

                    val response = apiService.createOrUpdateBox(request)
                    Log.d("BoxDialog", "Ответ получен: код ${response.code()}, body=${response.body()}")

                    if (response.isSuccessful && response.body()?.success == true) {
                        Log.d("BoxDialog", "Успех создания коробки: $generatedTrackingCode")
                        Toast.makeText(requireContext(), "Коробка создана: $generatedTrackingCode", Toast.LENGTH_SHORT).show()
                        soundManager.playSuccess()

                        Log.d("BoxDialog", "Закрываем диалог")
                        dialog.dismiss()

                        Log.d("BoxDialog", "Загружаем список коробок")
                        loadBoxes(isOpenOnly = true)

                        Log.d("BoxDialog", "Переходим на детальный экран коробки")
                        val detailFragment = BoxDetailFragment.newInstance("", generatedTrackingCode)
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, detailFragment)
                            .addToBackStack(null)
                            .commitAllowingStateLoss()

                        } else {
                        val errorMsg = response.body()?.message ?: "Ошибка создания"
                        Log.e("BoxDialog", "Ошибка от сервера: $errorMsg")
                        Toast.makeText(requireContext(), "Ошибка создания: $errorMsg", Toast.LENGTH_LONG).show()
                        soundManager.playError()

                        v.isEnabled = true
                        }

                } catch (e: Exception) {
                    Log.e("BoxDialog", "Исключение при создании коробки", e)
                    Toast.makeText(requireContext(), "Сеть/ошибка: ${e.localizedMessage ?: "Неизвестно"}", Toast.LENGTH_LONG).show()
                    soundManager.playError()
                    v.isEnabled = true
                }
                finally {
                    isCreating = false
                }
            }
        }
    }

    Log.d("BoxDialog", "Показываем диалог")
    dialog.show()
}
    private fun generateTrackingCode(): String {
        val date = SimpleDateFormat("MMdd", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HHmm", Locale.getDefault()).format(Date())
        val random = (0..9999).random().toString().padStart(4, '0')
        return "NBX$date$time$random"
    }
    //формат даты Результат: "23.02.2026 17:14"
    private fun formatBoxDate(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return "Нет даты"

        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(isoDate) ?: return "Ошибка даты"

            val outputFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            return outputFormat.format(date)
        } catch (e: Exception) {
            return isoDate
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}