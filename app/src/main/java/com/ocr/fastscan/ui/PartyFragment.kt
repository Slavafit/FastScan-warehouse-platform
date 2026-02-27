package com.ocr.fastscan.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ocr.fastscan.R
import com.ocr.fastscan.databinding.FragmentPartyBinding
import com.ocr.fastscan.utils.AppDependencies
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.databinding.DialogCreatePartyBinding
import kotlinx.coroutines.launch
import com.ocr.fastscan.data.model.*

class PartyFragment : Fragment() {

    private var _binding: FragmentPartyBinding? = null
    private val binding get() = _binding!!

    private val apiService = AppDependencies.apiService
    private val soundManager = AppDependencies.soundManager

    private val partiesAdapter = PartiesAdapter { partial ->
        val detailFragment = PartyDetailFragment.newInstance(
            partial.guid,
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
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvNoParties.visibility = View.GONE

            try {
                val response = apiService.getOpenPartials()
                if (response.isSuccessful) {
                    val parties = response.body() ?: emptyList()
                    binding.tvNoParties.visibility = if (parties.isEmpty()) View.VISIBLE else View.GONE
                    partiesAdapter.submitList(parties)
                } else {
                    binding.tvNoParties.text = "Ошибка загрузки"
                    binding.tvNoParties.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.tvNoParties.text = "Сеть ошибка"
                binding.tvNoParties.visibility = View.VISIBLE
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

        AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setTitle("Новая партия")
            .setPositiveButton("Создать") { _, _ ->
                val name = dialogBinding.etPartialName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Введите название", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val request = PartialCreateRequest(generatedCode, name)
                        val response = apiService.createPartial(request)
                        if (response.isSuccessful && response.body()?.success == true) {
                            Toast.makeText(requireContext(), "Партия создана", Toast.LENGTH_SHORT).show()
                            soundManager.playSuccess()
                            loadParties()
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}