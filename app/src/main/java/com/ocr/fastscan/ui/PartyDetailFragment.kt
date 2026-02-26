package com.ocr.fastscan.ui

import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ocr.fastscan.databinding.FragmentPartyDetailBinding
import com.ocr.fastscan.utils.AppDependencies
import android.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.ActivityResultLauncher


class PartyDetailFragment : Fragment() {

    private var _binding: FragmentPartyDetailBinding? = null
    private val binding get() = _binding!!

    private val apiService = AppDependencies.apiService
    private val soundManager = AppDependencies.soundManager

    private var partialGuid: String = ""
    private var partialCode: String = ""

    private val boxesAdapter = BoxesInPartialAdapter { box ->
        // Долгое нажатие или кнопка удаления — удаляем коробку
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить коробку?")
            .setMessage("Удалить ${box.tracking_code} из партии?")
            .setPositiveButton("Удалить") { _, _ ->
                removeBoxFromParty(box.guid)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    companion object {
        fun newInstance(guid: String, code: String) = PartyDetailFragment().apply {
            arguments = Bundle().apply {
                putString("partial_guid", guid)
                putString("partial_code", code)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        partialGuid = arguments?.getString("partial_guid") ?: ""
        partialCode = arguments?.getString("partial_code") ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartyDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
                    binding.tvInfo.text = "Склад: ... | Создано: ... | Коробок: ${boxes.size}"
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
            .setTitle("Выберите коробку")
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
                val response = apiService.addBoxToPartial(request)
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

    private fun finishParty() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = SendPartialRequest(partial = partialGuid)
                val response = apiService.sendPartial(request)
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Партия завершена", Toast.LENGTH_SHORT).show()
                    soundManager.playSuccess()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}