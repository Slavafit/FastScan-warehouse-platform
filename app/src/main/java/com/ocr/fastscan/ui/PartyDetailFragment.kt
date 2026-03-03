package com.ocr.fastscan.ui

import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ocr.fastscan.databinding.FragmentPartyDetailBinding
import com.ocr.fastscan.utils.AppDependencies
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.*
import com.ocr.fastscan.databinding.DialogSelectBoxBinding
import kotlinx.coroutines.launch
import com.ocr.fastscan.utils.PrintHelper
import java.text.SimpleDateFormat
import java.util.Date

class PartyDetailFragment : Fragment() {

    private var _binding: FragmentPartyDetailBinding? = null
    private val binding get() = _binding!!

    private val apiService = AppDependencies.apiService
    private val soundManager = AppDependencies.soundManager

    private var partialGuid: String = ""
    private var partialCode: String = ""
    private var partialName: String = ""   // заполняй в onViewCreated или newInstance

//    private val boxesAdapter = BoxesInPartialAdapter { box ->
//        // Долгое нажатие или кнопка удаления — удаляем коробку
//        AlertDialog.Builder(requireContext())
//            .setTitle("Удалить коробку?")
//            .setMessage("Удалить ${box.tracking_code} из партии? (пока не реализовано)")
//            .setPositiveButton("Удалить") { _, _ ->
//                Toast.makeText(requireContext(), "Удаление пока не реализовано", Toast.LENGTH_SHORT).show()
//                //removeBoxFromParty(box.guid)
//            }
//            .setNegativeButton("Отмена", null)
//            .show()
//    }
private val boxesAdapter = BoxesInPartialAdapter { box ->
    Log.d("PartyDetail", "Открываем детали коробки, передаём partialName: '$partialName'")  // ← лог для проверки
    // Удаление пока отключено
    Toast.makeText(requireContext(), "Удаление коробки временно недоступно", Toast.LENGTH_SHORT).show()
    val detailFragment = BoxDetailFragment.newInstance(
        boxGuid = box.guid ?: "",
        boxTracking = box.tracking_code ?: "",
        boxWeight = box.gross_weight?.toString() ?: "0.0",  // ← Double → String
        partialName = partialName,

    )
    parentFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, detailFragment)
        .addToBackStack(null)
        .commit()
    }
    companion object {
        fun newInstance(guid: String, code: String, name: String) = PartyDetailFragment().apply {
            arguments = Bundle().apply {
                putString("partial_guid", guid)
                putString("partial_code", code)
                putString("partial_name", name)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        partialGuid = arguments?.getString("partial_guid") ?: ""
        partialCode = arguments?.getString("partial_code") ?: ""
        partialName = arguments?.getString("partial_name") ?: "Без названия"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartyDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        partialName = binding.tvTitle.text.toString().replace("Партия ", "").trim()

        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = "Партия $partialCode"
        binding.btnAddBox.setOnClickListener {
            showSelectBoxDialog()
        }

        binding.btnFinishParty.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Завершить партию?")
                .setMessage("Завершить партию $partialCode?")
                .setPositiveButton("Завершить") { _, _ ->
                    finishParty()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        binding.rvBoxes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBoxes.adapter = boxesAdapter

        binding.btnBack.setOnClickListener {
            // Возврат назад по back stack (в список партий)
            //parentFragmentManager.popBackStack()
            // Или, если хочешь анимацию и чистый возврат:
            parentFragmentManager.popBackStackImmediate()
        }

        loadBoxDetails()
    }

    private fun loadBoxDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val response = apiService.getBoxesInPartial(partialGuid)
                //Log.d("PartyDetail", "Ответ сервера: код ${response.code()}, тело: ${response.body()}")
                if (response.isSuccessful) {
                    val boxes = response.body() ?: emptyList()
                    //Log.d("PartyDetail", "Получен: ${boxes}")

                    boxesAdapter.submitList(boxes)
                    binding.tvInfo.text = "Склад: BMG | Создано: ... | Коробок: ${boxes.size}"
                } else {
                    Toast.makeText(requireContext(), "Ошибка загрузки", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showSelectBoxDialog() {
        val dialogBinding = DialogSelectBoxBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            //.setTitle("Выберите коробку")
            .create()

        dialogBinding.rvOpenBoxes.layoutManager = LinearLayoutManager(requireContext())
        val adapter = OpenBoxesAdapter { box ->
            addBoxToParty(box)
            dialog.dismiss()
        }
        dialogBinding.rvOpenBoxes.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = apiService.getOpenBoxes()
                if (response.isSuccessful) {
                    adapter.submitList(response.body() ?: emptyList())
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка загрузки коробок", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun addBoxToParty(box: BoxInPartial) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = AddBoxRequest(
                    guid = box.guid,
                    tracking_code = box.tracking_code,
                    width = box.width,
                    height = box.height,
                    length = box.length,
                    v_weight = box.v_weight,
                    gross_weight = box.gross_weight,
                    orderCount = box.orderCount
                )
                Log.d("addBoxToParty", "request: ${request}")

                val response = apiService.addBoxToPartial(request)
                Log.d("addBoxToParty", "response: ${response}")

                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Коробка добавлена", Toast.LENGTH_SHORT).show()
                    soundManager.playSuccess()
                    loadBoxDetails()
                } else {
                    Toast.makeText(requireContext(), "Ошибка добавления коробки", Toast.LENGTH_SHORT).show()
                    soundManager.playError()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
                soundManager.playError()
            }
        }
    }
//    private fun removeBoxFromParty(boxGuid: String) {
//        viewLifecycleOwner.lifecycleScope.launch {
//            try {
//                // Пока заглушка — замени на реальный запрос, когда дашь путь
//                // Пример для DELETE /api/order/partial-box?guid=boxGuid&partial=partialGuid
//                val response = apiService.removeBoxFromPartial(boxGuid, partialGuid)
//
//                if (response.isSuccessful) {
//                    Toast.makeText(requireContext(), "Коробка удалена из партии", Toast.LENGTH_SHORT).show()
//                    soundManager.playSuccess()
//                    loadBoxDetails()  // обновляем список коробок
//                } else {
//                    Toast.makeText(requireContext(), "Ошибка удаления коробки", Toast.LENGTH_SHORT).show()
//                    soundManager.playError()
//                }
//            } catch (e: Exception) {
//                Log.e("PartyDetail", "Ошибка удаления коробки", e)
//                Toast.makeText(requireContext(), "Сеть/ошибка удаления: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
//                soundManager.playError()
//            }
//        }
//    }
private fun finishParty() {
    AlertDialog.Builder(requireContext())
        .setTitle("Завершить партию?")
        .setMessage("Завершить партию $partialCode?")
        .setPositiveButton("Завершить") { _, _ ->
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val request = SendPartialRequest(partial = partialGuid)
                    val response = apiService.sendPartial(request)
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "Партия завершена", Toast.LENGTH_SHORT).show()
                        soundManager.playSuccess()

                        // Печать документа
                        val printHelper = PrintHelper(requireContext())
                        partialName = binding.tvTitle.text.toString().replace("Партия ", "").trim()
                        printHelper.printParty(
                            partialCode = partialCode,
                            partialName = partialName,  //переменная с именем партии (KG 87 и т.д.
                            warehouseName = "Spain",    // или откуда берёшь
                            dateCreated = SimpleDateFormat("dd.MM.yyyy").format(Date()), // или откуда берёшь
                            boxes = boxesAdapter.currentList
                        )

                        // Возврат в список
                        parentFragmentManager.popBackStack()
                    } else {
                        Toast.makeText(requireContext(), "Ошибка завершения", Toast.LENGTH_SHORT).show()
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
}
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}