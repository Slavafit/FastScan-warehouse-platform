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

    private val partiesAdapter = PartiesAdapter { partial ->
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
    }

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

        loadParties()
    }

    private fun loadParties() {
        launchOnViewLifecycle {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvNoParties.visibility = View.GONE

            try {
                val response = apiService.getOpenPartials()
                if (_binding == null) return@launchOnViewLifecycle
                
                if (response.isSuccessful) {
                    val parties = response.body() ?: emptyList()
                    binding.tvNoParties.visibility = if (parties.isEmpty()) View.VISIBLE else View.GONE
                    partiesAdapter.submitList(parties)
                } else {
                    binding.tvNoParties.text = getString(R.string.error_load)
                    binding.tvNoParties.visibility = View.VISIBLE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PartyFragment", "Load error", e)
                binding.tvNoParties.text = getString(R.string.net_error)
                binding.tvNoParties.visibility = View.VISIBLE
            } finally {
                _binding?.progressBar?.visibility = View.GONE
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
            .setPositiveButton(R.string.create, null) // Устанавливаем null, чтобы переопределить поведение ниже
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

                // Формируем имя с префиксом KG
                val finalName = PartyNumberFormatter.normalize(inputName)

                button.isEnabled = false // Блокируем кнопку на время запроса
                
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
