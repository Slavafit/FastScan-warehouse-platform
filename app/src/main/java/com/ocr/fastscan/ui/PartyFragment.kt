package com.ocr.fastscan.ui

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.*
import com.ocr.fastscan.databinding.DialogCreatePartyBinding
import com.ocr.fastscan.databinding.FragmentPartyBinding
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

class PartyFragment : BaseFragment() {

    private var _binding: FragmentPartyBinding? = null
    private val binding get() = _binding!!

    private var showAll = false

    private val partiesAdapter = PartiesAdapter(
        onClick = { partial ->
            val detailFragment = PartyDetailFragment.newInstance(
                partial.guid,
                partial.codeid,
                partial.partial_code,
                partial.partial_name
            )
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit()
        },
        onLongClick = { partial ->
            if (partial.status != 0) {
                showRestorePartyDialog(partial)
            }
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPartyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvParties.layoutManager = LinearLayoutManager(requireContext())
        binding.rvParties.adapter = partiesAdapter

        binding.fabCreateParty.setOnClickListener {
            showCreatePartyDialog()
        }

        binding.btnToggleFilter.setOnClickListener {
            showAll = !showAll
            updateFilterButton()
            loadParties()
        }

        updateFilterButton()
        loadParties()
    }

    private fun updateFilterButton() {
        binding.btnToggleFilter.text = if (showAll) {
            getString(R.string.in_work)
        } else {
            getString(R.string.show_all)
        }
    }

    private fun loadParties() {
        launchOnViewLifecycle {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvNoParties.visibility = View.GONE

            try {
                val typeParam = if (showAll) null else 0
                val response = apiService.getPartials(typeParam)
                
                if (_binding == null) return@launchOnViewLifecycle
                
                if (response.isSuccessful) {
                    val parties = response.body() ?: emptyList()
                    
                    // Сортировка: самые новые сверху
                    val sortedParties = parties.sortedByDescending { it.date_created }
                    
                    binding.tvNoParties.visibility = if (sortedParties.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvNoParties.text = if (showAll) "Партий не найдено" else getString(R.string.no_open_parties)
                    partiesAdapter.submitList(sortedParties)
                } else {
                    binding.tvNoParties.text = getString(R.string.error_load)
                    binding.tvNoParties.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("PartyFragment", "Load error", e)
                binding.tvNoParties.text = getString(R.string.net_error)
                binding.tvNoParties.visibility = View.VISIBLE
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun showRestorePartyDialog(party: Partial) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.restore_party_title)
            .setMessage(getString(R.string.restore_party_message, PartyNumberFormatter.display(party.partial_name)))
            .setPositiveButton(R.string.ok) { _, _ ->
                restoreParty(party)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun restoreParty(party: Partial) {
        launchOnViewLifecycle {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val request = PartialUpdateRequest(
                    guid = party.guid,
                    partial_code = party.partial_code,
                    partial_name = party.partial_name,
                    status = 0
                )
                val response = apiService.updatePartial(request)
                if (response.isSuccessful) {
                    showToast(R.string.party_restored)
                    soundManager.playSuccess()
                    loadParties()
                } else {
                    showToast(getString(R.string.server_error, response.code()))
                    soundManager.playError()
                }
            } catch (e: Exception) {
                showToast(R.string.net_error_short)
                soundManager.playError()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showCreatePartyDialog() {
        val dialogBinding = DialogCreatePartyBinding.inflate(layoutInflater)
        val generatedCode = "NPX" + SimpleDateFormat("MMddHH", Locale.getDefault()).format(Date()) + (100000..999999).random()

        dialogBinding.etPartialCode.setText(generatedCode)
        dialogBinding.etPartialName.requestFocus()

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.create, null) 
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val inputName = dialogBinding.etPartialName.text.toString().trim()
                if (inputName.isEmpty()) {
                    showToast(getString(R.string.input_part_name))
                    return@setOnClickListener
                }

                val finalName = PartyNumberFormatter.normalize(inputName)

                button.isEnabled = false
                
                launchOnViewLifecycle {
                    try {
                        val request = PartialCreateRequest(generatedCode, finalName)
                        val response = apiService.createPartial(request)

                        if (response.isSuccessful) {
                            showToast(R.string.part_created)
                            soundManager.playSuccess()
                            dialog.dismiss()
                            delay(300)
                            loadParties()
                        } else {
                            button.isEnabled = true
                            showToast(getString(R.string.error_code, response.code()))
                            soundManager.playError()
                        }
                    } catch (e: Exception) {
                        button.isEnabled = true
                        showToast(R.string.net_error_short)
                        soundManager.playError()
                    }
                }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
