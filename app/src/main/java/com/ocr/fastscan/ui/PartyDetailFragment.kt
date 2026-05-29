package com.ocr.fastscan.ui

import android.app.AlertDialog
import android.content.Intent
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
import com.ocr.fastscan.data.model.*
import com.ocr.fastscan.databinding.DialogSelectBoxBinding
import com.ocr.fastscan.databinding.FragmentPartyDetailBinding
import com.ocr.fastscan.utils.AppDependencies
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PartyDetailFragment : Fragment() {

    private var _binding: FragmentPartyDetailBinding? = null
    private val binding get() = _binding!!

    private val apiService by lazy { AppDependencies.apiService }
    private val soundManager by lazy { AppDependencies.soundManager }
    
    private var partialGuid: String = ""
    private var partialCode: String = ""
    private var partialName: String = ""
    private var isFinishing = false

    private val boxesAdapter = BoxesInPartialAdapter { box ->
        val detailFragment = BoxDetailFragment.newInstance(
            boxGuid = box.guid ?: "",
            boxCodeid = box.codeid ?: "",
            boxTracking = box.tracking_code ?: "",
            boxWeight = box.gross_weight.toString(),
            partialName = partialName
        )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    companion object {
        fun newInstance(guid: String, codeid: String, code: String, name: String): PartyDetailFragment {
            return PartyDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("partial_guid", guid)
                    putString("partial_code", code)
                    putString("partial_name", name)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            partialGuid = it.getString("partial_guid", "")
            partialCode = it.getString("partial_code", "")
            partialName = it.getString("partial_name", "Без названия")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartyDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Отображаем красивое имя (например KG 120), если оно есть, иначе код
        binding.tvTitle.text = if (partialName.startsWith("KG")) partialName else "Партия $partialCode"
        
        binding.rvBoxes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBoxes.adapter = boxesAdapter

        binding.btnAddBox.setOnClickListener { showSelectBoxDialog() }
        binding.btnFinishParty.setOnClickListener { showFinishPartyDialog() }
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        loadBoxDetails()
    }

    private fun loadBoxDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val response = apiService.getBoxesInPartial(partialGuid)
                if (response.isSuccessful) {
                    val boxes = response.body() ?: emptyList()
                    boxesAdapter.submitList(boxes)
                    binding.tvInfo.text = "Склад: BMG | Коробок: ${boxes.size}"
                }
            } catch (e: Exception) {
                Log.e("PartyDetail", "Load boxes error", e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showSelectBoxDialog() {
        val dialogBinding = DialogSelectBoxBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        val adapter = OpenBoxesAdapter { box ->
            addBoxToParty(box)
            dialog.dismiss()
        }
        dialogBinding.rvOpenBoxes.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvOpenBoxes.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = apiService.getOpenBoxes()
                if (response.isSuccessful) {
                    adapter.submitList(response.body() ?: emptyList())
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.error_load_boxes_short, Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun addBoxToParty(box: BoxInPartial) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
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
                val response = apiService.addBoxToPartial(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    soundManager.playSuccess()
                    loadBoxDetails()
                } else {
                    Toast.makeText(requireContext(), R.string.error_adding_box, Toast.LENGTH_SHORT).show()
                    soundManager.playError()
                }
            } catch (e: Exception) {
                soundManager.playError()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun finishParty() {
        if (isFinishing) return
        isFinishing = true

        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val request = SendPartialRequest(partial = partialGuid)
                val response = apiService.sendPartial(request)

                if (response.isSuccessful) {
                    soundManager.playSuccess()
                    Toast.makeText(requireContext(), R.string.part_finished, Toast.LENGTH_SHORT).show()

                    // Вместо печати формируем текст и делимся
                    sharePartyReport()
                    
                    delay(800) 
                    parentFragmentManager.popBackStack()
                } else {
                    isFinishing = false
                    Toast.makeText(requireContext(), getString(R.string.server_error, response.code()), Toast.LENGTH_SHORT).show()
                    soundManager.playError()
                }
            } catch (e: Exception) {
                isFinishing = false
                soundManager.playError()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun sharePartyReport() {
        val boxes = boxesAdapter.currentList
        if (boxes.isEmpty()) return

        val sb = StringBuilder()
        sb.append("📋 ОТЧЕТ ПО ПАРТИИ: $partialName\n")
        sb.append("🆔 Код: $partialCode\n")
        sb.append("📅 Дата: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}\n")
        sb.append("📦 Коробок: ${boxes.size}\n")
        sb.append("────────────────\n")

        var totalWeight = 0.0
        boxes.forEachIndexed { index, box ->
            sb.append("${index + 1}. 📦 `${box.tracking_code}`\n")
            sb.append("   📏 Размеры: ${box.length} × ${box.width} × ${box.height} см\n")
            sb.append("   ⚖️ Вес: ${String.format("%.2f", box.gross_weight)} кг\n")
            totalWeight += box.gross_weight
        }
        
        sb.append("────────────────\n")
        sb.append("⚖️ ОБЩИЙ ВЕС: ${String.format("%.2f", totalWeight)} кг\n")
        sb.append("\n✅ Партия успешно закрыта.")

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(shareIntent, "Отправить отчет по партии"))
    }

    private fun showFinishPartyDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.finish_party_title)
            .setMessage("Вы уверены, что хотите завершить партию $partialName?")
            .setPositiveButton(R.string.finish_part) { _, _ -> finishParty() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
