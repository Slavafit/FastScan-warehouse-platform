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
import java.util.Locale

class PartyDetailFragment : Fragment() {

    private var _binding: FragmentPartyDetailBinding? = null
    private val binding get() = _binding!!

    private val apiService by lazy { AppDependencies.apiService }
    private val soundManager by lazy { AppDependencies.soundManager }
    private var partialGuid: String = ""
    private var partialCodeid: String = ""
    private var partialCode: String = ""
    private var partialName: String = ""   // заполняй в onViewCreated или newInstance
    private var isFinishing = false

private val boxesAdapter = BoxesInPartialAdapter { box ->
    //Log.d("PartyDetail", "Открываем детали коробки, передаём partialName: '$partialName'")  // ← лог для проверки
    // Удаление пока отключено
    Toast.makeText(requireContext(), R.string.delete_box, Toast.LENGTH_SHORT).show()
    val detailFragment = BoxDetailFragment.newInstance(
        box.guid ?: "",
        box.codeid ?: "",
        box.tracking_code ?: "",
        box.gross_weight?.toString() ?: "0.0",  // ← Double → String
        partialName,
    )
    parentFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, detailFragment)
        .addToBackStack(null)
        .commit()
    }
    companion object {
        fun newInstance(guid: String, codeid: String, code: String, name: String) = PartyDetailFragment().apply {
            val fragment = PartyDetailFragment()
            val args = Bundle()
            args.putString("partial_guid", guid)
            args.putString("codeid", codeid)
            args.putString("partial_code", code)
            args.putString("partial_name", name)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        partialGuid = arguments?.getString("partial_guid") ?: ""
        partialCodeid = arguments?.getString("codeid") ?: ""
        partialCode = arguments?.getString("partial_code") ?: ""
        partialName = arguments?.getString("partial_name") ?: "Без названия"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartyDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // Лучше брать название напрямую из правильного TextView
        partialName = binding.tvTitle.text.toString().trim()

        // Если tvTitle пустой — fallback
        if (partialName.isBlank()) {
            partialName = partialName ?: getString(R.string.no_party_name)
        }

        //partialName = binding.tvTitle.text.toString().replace("Партия ", "").trim()

        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = getString(R.string.part, partialCode)
        binding.btnAddBox.setOnClickListener {
            showSelectBoxDialog()
        }

        binding.btnFinishParty.setOnClickListener {
            showFinishPartyDialog()
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
                    binding.tvInfo.text = "Склад: BMG | Коробок: ${boxes.size}"
                } else {
                    Toast.makeText(requireContext(), R.string.error_load, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.net_error_short, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(requireContext(),
                    getString(
                        R.string.error_load_boxes,
                        e),
                    Toast.LENGTH_SHORT)
                    .show()
            }
        }
        dialog.show()
    }

    private fun addBoxToParty(box: BoxInPartial) {
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = AddBoxRequest(
                    guid = box.guid,
                    codeid = box.codeid,
                    partial = partialGuid,
                    tracking_code = box.tracking_code,
                    width = box.width,
                    height = box.height,
                    length = box.length,
                    v_weight = box.v_weight,
                    gross_weight = box.gross_weight
                )
                //Log.d("AddBox", "Отправка запроса:: ${request}")

                val response = apiService.addBoxToPartial(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        val message = body.message ?: R.string.box_added
                        Toast.makeText(requireContext(), "$message", Toast.LENGTH_LONG).show()
                        soundManager.playSuccess()

                        // Обновляем список коробок в партии
                        loadBoxDetails()
                    } else {
                        Toast.makeText(requireContext(), "Сервер вернул success=false: ${body?.message}", Toast.LENGTH_LONG).show()
                        soundManager.playError()
                    }
                } else {
                    //val errorBody = response.errorBody()?.string() ?: "Нет тела ошибки"
                    //Log.e("AddBox", "Ошибка добавления коробки: код ${response.code()}, тело: $errorBody")
                    Toast.makeText(requireContext(), "Ошибка добавления коробки", Toast.LENGTH_SHORT).show()
                    soundManager.playError()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    getString(
                        R.string.net_error,
                        e),
                    Toast.LENGTH_SHORT)
                    .show()
                //Log.e("AddBox", "Сетевая ошибка при добавлении коробки", e)

                soundManager.playError()
            }
            finally {
                binding.progressBar.visibility = View.GONE
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
        if (isFinishing) return // Защита от дребезга (двойного клика)
        isFinishing = true

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        isFinishing = true
                        binding.progressBar.visibility = View.VISIBLE // Показываем загрузку

                        val request = SendPartialRequest(partial = partialGuid)
                        val response = apiService.sendPartial(request)

                        if (response.isSuccessful) {
                            Toast.makeText(requireContext(), R.string.part_finished, Toast.LENGTH_SHORT).show()
                            soundManager.playSuccess()

                            // 1. Сначала готовим данные для печати
                            val currentBoxes = boxesAdapter.currentList.toList() // Копируем список
                            val titleName = binding.tvTitle.text.toString().replace("Партия ", "").trim()

                            // 2. Вызываем печать (PrintHelper должен быть асинхронным внутри)
                            val printHelper = PrintHelper(requireContext())
                            printHelper.printParty(
                                partialCode,
                                partialName,
                                "Spain BMG",
                                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()),
                                boxes = currentBoxes
                            )
                            Log.e("PrintHelper", "printHelper.printParty: partialCode: $partialCode, partialName: $partialName, titleName: $titleName")

                            // 3. Возвращаемся назад.
                            // Используем Dispatchers.Main для уверенности и небольшую задержку,
                            // чтобы Toast успел показаться
                            parentFragmentManager.popBackStack()

                        } else {
                            isFinishing = false
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(),
                                getString(R.string.server_error,
                                    response.code()
                                    ),
                                Toast.LENGTH_SHORT)
                                .show()
                            soundManager.playError()
                        }
                    } catch (e: Exception) {
                        isFinishing = false
                        binding.progressBar.visibility = View.GONE
                        Log.e("FinishParty", "Ошибка сети", e)
                        Toast.makeText(requireContext(),
                            getString(
                                R.string.net_error,
                                e),
                            Toast.LENGTH_SHORT)
                            .show()
                        soundManager.playError()
                    }
                }
    }

    private fun showFinishPartyDialog() {
        if (isFinishing) return

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.finish_party_title)
            .setMessage(getString(R.string.finish_part_property, partialCode))
            .setCancelable(true)
            .setPositiveButton(R.string.finish_part) { _, _ ->
                finishParty()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}