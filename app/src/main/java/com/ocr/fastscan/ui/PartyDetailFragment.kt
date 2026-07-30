package com.ocr.fastscan.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.text.InputType
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.*
import com.ocr.fastscan.databinding.DialogSelectBoxBinding
import com.ocr.fastscan.databinding.FragmentPartyDetailBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PartyDetailFragment : BaseFragment() {

    private var _binding: FragmentPartyDetailBinding? = null
    private val binding get() = _binding!!

    private var partialGuid: String = ""
    private var partialCode: String = ""
    private var partialName: String = ""
    private var isFinishing = false
    // Keep the current box list available for the final party report.
    private var currentBoxes: List<BoxInPartial> = emptyList()

    private val displayPartyNumber: String
        get() = PartyNumberFormatter.display(partialName).ifBlank { partialCode }

    private val boxesAdapter = BoxesInPartialAdapter { box ->
        val detailFragment = BoxDetailFragment.newInstance(
            boxGuid = box.guid,
            boxCodeid = box.codeid,
            boxTracking = box.tracking_code,
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
            partialName = it.getString("partial_name", getString(R.string.no_party_name))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartyDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = getString(R.string.part, displayPartyNumber)
        binding.btnFinishParty.text = getString(R.string.finish_part_property, displayPartyNumber)
        
        binding.rvBoxes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBoxes.adapter = boxesAdapter

        binding.btnAddBox.setOnClickListener { showSelectBoxDialog() }
        binding.btnFinishParty.setOnClickListener { showFinishPartyDialog() }
        binding.btnEditPartyName.setOnClickListener { showEditPartyNameDialog() }
        binding.tvTitle.setOnLongClickListener {
            showEditPartyNameDialog()
            true
        }
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        loadBoxDetails()
    }

    private fun showEditPartyNameDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.input_part)
            setText(PartyNumberFormatter.display(partialName))
            setSelection(text.length)
        }
        val horizontalPadding = (24 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(requireContext()).apply {
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            addView(
                input,
                android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_party_name_title)
            .setView(container)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = PartyNumberFormatter.normalize(input.text.toString().trim())
                if (newName.isBlank()) {
                    showToast(R.string.input_part_name)
                    return@setOnClickListener
                }
                if (newName == partialName) {
                    dialog.dismiss()
                    return@setOnClickListener
                }
                updatePartyName(newName, dialog)
            }
        }
        dialog.show()
    }

    private fun updatePartyName(newName: String, dialog: AlertDialog) {
        launchOnViewLifecycle {
            val activeBinding = _binding ?: return@launchOnViewLifecycle
            activeBinding.progressBar.visibility = View.VISIBLE
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
            try {
                val request = PartialUpdateRequest(
                    guid = partialGuid,
                    partial_code = partialCode,
                    partial_name = newName,
                    status = 0
                )
                val response = apiService.updatePartial(request)
                if (_binding == null || !isAdded) return@launchOnViewLifecycle
                if (response.isSuccessful) {
                    partialName = newName
                    binding.tvTitle.text = getString(R.string.part, displayPartyNumber)
                    binding.btnFinishParty.text =
                        getString(R.string.finish_part_property, displayPartyNumber)
                    showToast(R.string.party_name_updated)
                    soundManager.playSuccess()
                    dialog.dismiss()
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    showToast(getString(R.string.server_error, response.code()))
                    soundManager.playError()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                showToast(R.string.net_error_short)
                soundManager.playError()
                Log.e("PartyDetail", "Update party name error", e)
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun loadBoxDetails() {
        launchOnViewLifecycle {
            val activeBinding = _binding ?: return@launchOnViewLifecycle
            activeBinding.progressBar.visibility = View.VISIBLE
            try {
                val response = apiService.getBoxesInPartial(partialGuid)
                val currentBinding = _binding ?: return@launchOnViewLifecycle
                if (response.isSuccessful) {
                    val boxes = response.body() ?: emptyList()
                    currentBoxes = boxes
                    boxesAdapter.submitList(boxes)
                    currentBinding.tvInfo.text = getString(R.string.party_detail_info, "BMG", boxes.size)

                    Log.d("PartyDetail", "Loaded ${boxes.size} boxes for report")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PartyDetail", "Load boxes error", e)
            } finally {
                _binding?.progressBar?.visibility = View.GONE
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

        launchOnViewLifecycle {
            try {
                val response = apiService.getOpenBoxes()
                if (_binding == null || !isAdded) return@launchOnViewLifecycle
                if (response.isSuccessful) {
                    adapter.submitList(response.body() ?: emptyList())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isAdded) showToast(R.string.error_load_boxes_short)
            }
        }
        dialog.show()
    }

    private fun addBoxToParty(box: BoxInPartial) {
        launchOnViewLifecycle {
            val activeBinding = _binding ?: return@launchOnViewLifecycle
            activeBinding.progressBar.visibility = View.VISIBLE
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
                if (_binding == null || !isAdded) return@launchOnViewLifecycle
                if (response.isSuccessful && response.body()?.success == true) {
                    soundManager.playSuccess()
                    loadBoxDetails()
                } else {
                    showToast(R.string.error_adding_box)
                    soundManager.playError()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                soundManager.playError()
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun finishParty() {
        if (isFinishing) return
        isFinishing = true

        launchOnViewLifecycle {
            val activeBinding = _binding ?: return@launchOnViewLifecycle
            activeBinding.progressBar.visibility = View.VISIBLE
            try {
                val request = SendPartialRequest(partial = partialGuid)
                val response = apiService.sendPartial(request)
                if (_binding == null || !isAdded) return@launchOnViewLifecycle

                if (response.isSuccessful) {
                    soundManager.playSuccess()
                    showToast(R.string.part_finished)

                    sharePartyReport()
                    
                    delay(800) 
                    if (_binding != null && isAdded) {
                        parentFragmentManager.popBackStack()
                    }
                } else {
                    isFinishing = false
                    showToast(getString(R.string.server_error, response.code()))
                    soundManager.playError()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isFinishing = false
                soundManager.playError()
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun sharePartyReport() {
        val boxes = currentBoxes
        if (boxes.isEmpty()) {
            Log.w("PartyDetail", "Report attempt with empty boxes list")
            return
        }

        val sb = StringBuilder()
        sb.append("📋 ОТЧЕТ ПО ПАРТИИ: $displayPartyNumber\n")
        sb.append("🆔 Код: $partialCode\n")
        sb.append("📅 Дата: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}\n")
        sb.append("📦 Коробок: ${boxes.size}\n")
        sb.append("────────────────\n")

        var totalWeight = 0.0
        boxes.forEachIndexed { index, box ->
            sb.append("${index + 1}. 📦 ${box.tracking_code}\n")
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
            .setMessage(getString(R.string.finish_part_property, displayPartyNumber))
            .setPositiveButton(R.string.finish_part) { _, _ -> finishParty() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding?.rvBoxes?.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
