package com.ocr.fastscan.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.R
import com.ocr.fastscan.databinding.FragmentBoxBinding
import com.ocr.fastscan.utils.AppDependencies
import com.ocr.fastscan.utils.DialogUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class BoxFragment : Fragment() {
    private val apiService by lazy { AppDependencies.apiService }
    private var _binding: FragmentBoxBinding? = null
    private val binding get() = _binding!!
    private var isShowingAllBoxes = false  // флаг для "Показать все"
    private var isLoading = false  // Флаг для предотвращения петли
    private val boxesAdapter = BoxesAdapter { box ->
        val partialName = arguments?.getString("partial_name")
        //Log.d("boxesAdapter", "Перед открытием деталей коробки. partialName = '$partialName'")
        // Клик по коробке — открываем детальный экран
        val detailFragment = BoxDetailFragment.newInstance(
            boxGuid =box.guid ?: "",
            boxCodeid = box.codeid ?: "",
            boxTracking = box.trackingCode ?: "",
            boxWeight = box.grossWeight?.toString() ?: "",
            partialName = partialName ?: "KG..."
            )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

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


        binding.tvTitle.text = getString(R.string.open_boxes)   // Использование tvTitle

        binding.rvBoxes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBoxes.adapter = boxesAdapter

        // Кнопка обновления
//        binding.btnRefresh.setOnClickListener {
//            loadBoxes(isOpenOnly = isShowingAllBoxes)
//        }
        // Кнопка добавления коробки
        binding.btnAddNewBox.setOnClickListener {
            DialogUtils.showAddNewBoxDialog(
                lifecycleOwner = viewLifecycleOwner,
                context = requireContext(),
                onSuccess = { trackingCode ->
                    // Запускаем корутину для обновления
                    viewLifecycleOwner.lifecycleScope.launch {
                        // 1. Даем серверу 300мс "прийти в себя" после записи
                        delay(300)

                        // 2. Сбрасываем флаг isLoading, чтобы loadBoxes не проигнорировал вызов
                        isLoading = false

                        // 3. Загружаем обновленный список
                        loadBoxes(isOpenOnly = true)

                        // 4. Уведомляем пользователя
                        Toast.makeText(requireContext(), "Коробка $trackingCode добавлена", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { errorMsg ->
                    Toast.makeText(requireContext(), "Ошибка: $errorMsg", Toast.LENGTH_LONG).show()
                }
            )
        }

        // Кнопка переключения "Все / Открытые"
        binding.btnShowAll.setOnClickListener {
            isShowingAllBoxes = !isShowingAllBoxes
            binding.btnShowAll.text = if (isShowingAllBoxes) getString(R.string.open_boxes) else getString(R.string.all_boxes)
            binding.tvTitle.text = if (isShowingAllBoxes) getString(R.string.all_boxes) else getString(R.string.open_boxes)

            // Анимация обновления списка
            binding.rvBoxes.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    loadBoxes(isOpenOnly = !isShowingAllBoxes)
                    binding.rvBoxes.animate().alpha(1f).setDuration(150).start()
                }.start()
        }
        loadBoxes(isOpenOnly = true)
    }

    private fun loadBoxes(isOpenOnly: Boolean = true) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvNoBoxes.visibility = View.GONE

        if (isLoading) {
            //Log.w("BoxFragment", "Уже загружается — пропускаем вызов loadBoxes")
            return
        }
        isLoading = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                //Log.d("BoxDialog", "Загружаем список коробок")
                val response = apiService.getBoxes(status = if (isOpenOnly) 0 else null)
                //Log.d("BoxFragment", "Ответ код: ${response.code()}, успешный: ${response.isSuccessful}")
                if (response.isSuccessful && response.body() != null) {
                    val boxes = response.body()!!.map { box ->
                        box.copy(
                            dateCreated = formatBoxDate(box.dateCreated)  // ← форматируем
                        )
                    }
                    boxesAdapter.submitList(boxes)
                    // ОБНОВЛЯЕМ ЗАГОЛОВОК С КОЛИЧЕСТВОМ ===
                    val count = boxes.size
                    // Берем базовую строку (Открытые или Все) и добавляем (Количество)
                    val baseText = if (isOpenOnly) {
                        getString(R.string.open_boxes)
                    } else {
                        getString(R.string.all_boxes) // Замени на getString(R.string.all_boxes), если есть в strings.xml
                    }
                    binding.tvTitle.text = "$baseText ($count)"
                    // ===================================================
                    if (boxes.isEmpty()) {
                        binding.tvNoBoxes.visibility = View.VISIBLE
                    }
                    //Log.d("BoxFragment", "Загрузка завершена, коробок: ${boxes.size}")

                } else {
                    //Log.e("BoxFragment", "Ошибка API: ${response.code()} - ${response.message()}")
                    Toast.makeText(requireContext(), "Ошибка загрузки коробок", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                // ВАЖНО: Если корутину отменили (например, перешли на другой экран),
                // просто пробрасываем исключение дальше, не показывая Toast
                throw e
            } catch (e: Exception) {
                //Log.e("BoxFragment", "Сеть/исключение: ${e.message}", e)
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                isLoading = false  // Снимаем флаг
            }
        }
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