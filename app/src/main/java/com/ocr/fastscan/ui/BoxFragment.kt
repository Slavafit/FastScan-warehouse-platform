package com.ocr.fastscan.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.BoxItem
import com.ocr.fastscan.data.model.BoxRequest
import com.ocr.fastscan.data.model.BoxResponse
import com.ocr.fastscan.databinding.DialogAddBoxBinding
import com.ocr.fastscan.databinding.FragmentBoxBinding
import com.ocr.fastscan.utils.AppDependencies
import com.ocr.fastscan.utils.AppDependencies.soundManager
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
        val detailFragment = BoxDetailFragment.newInstance(box.guid ?: "", box.trackingCode ?: "")
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

    private fun loadBoxes(isOpenOnly: Boolean) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvNoBoxes.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = apiService.getBoxes(status = if (isOpenOnly) 0 else null)
                if (response.isSuccessful && response.body() != null) {
                    val boxes = response.body()!!
                    boxesAdapter.submitList(boxes)
                    if (boxes.isEmpty()) {
                        binding.tvNoBoxes.visibility = View.VISIBLE
                    }
                } else {
                    Toast.makeText(requireContext(), "Ошибка загрузки коробок", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showAddNewBoxDialog() {
        val dialogBinding = DialogAddBoxBinding.inflate(layoutInflater)

        // Генерируем tracking_code сразу
        val generatedTrackingCode = generateTrackingCode()
        dialogBinding.tvTrackingCode.text = "Будет создан номер: $generatedTrackingCode"  // добавь TextView в dialog_add_box.xml

        AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setTitle("Новая коробка")
            .setPositiveButton("Создать") { _, _ ->
                val length = dialogBinding.etLength.text.toString().toIntOrNull() ?: 70
                val width = dialogBinding.etWidth.text.toString().toIntOrNull() ?: 45
                val height = dialogBinding.etHeight.text.toString().toIntOrNull() ?: 50

                lifecycleScope.launch {
                    try {
                        val request = BoxRequest(
                            trackingCode = generatedTrackingCode,  // используем сгенерированный
                            width = width,
                            height = height,
                            length = length
                        )
                        val response = apiService.createOrUpdateBox(request)
                        if (response.isSuccessful && response.body()?.success == true) {
                            Toast.makeText(requireContext(), "Коробка создана: $generatedTrackingCode", Toast.LENGTH_SHORT).show()
                            soundManager.playSuccess()
                            loadBoxes(isOpenOnly = true)
                            // Открываем детальный экран новой коробки
                            val detailFragment = BoxDetailFragment.newInstance("", generatedTrackingCode)
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragment_container, detailFragment)
                                .addToBackStack(null)
                                .commit()
                        } else {
                            Toast.makeText(requireContext(), "Ошибка создания", Toast.LENGTH_SHORT).show()
                            soundManager.playError()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
                        soundManager.playError()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()

        // Устанавливаем дефолтные размеры
        dialogBinding.etLength.setText("70")
        dialogBinding.etWidth.setText("45")
        dialogBinding.etHeight.setText("50")
    }

    private fun generateTrackingCode(): String {
        val date = SimpleDateFormat("MMdd", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HHmm", Locale.getDefault()).format(Date())
        val random = (0..9999).random().toString().padStart(4, '0')
        return "NBX$date$time$random"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}