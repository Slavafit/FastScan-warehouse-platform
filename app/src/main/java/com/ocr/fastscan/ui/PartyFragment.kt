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
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.databinding.DialogCreatePartyBinding
import kotlinx.coroutines.launch
import com.ocr.fastscan.data.model.*
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

class PartyFragment : Fragment() {

    private var _binding: FragmentPartyBinding? = null
    private val binding get() = _binding!!

    private val apiService by lazy { AppDependencies.apiService }
    private val soundManager by lazy { AppDependencies.soundManager }

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
            } catch (e: CancellationException) {
                // Игнорируем отмену корутины
                throw e
            } catch (e: Exception) {
                Log.e("CreateParty", "Ошибка сети", e)
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
            //.setTitle("Новая партия")
            .setPositiveButton("Создать") { _, _ ->
                val inputName = dialogBinding.etPartialName.text.toString().trim()
                if (inputName.isEmpty()) {
                    Toast.makeText(requireContext(), "Введите название", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                // Формируем финальное имя: если цифры 123, станет KG123
                val finalName = if (inputName.startsWith("KG ", ignoreCase = true)) {
                    inputName.uppercase()
                } else {
                    "KG $inputName"
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val request = PartialCreateRequest(generatedCode, finalName)
                        Log.d("CreateParty", "Отправляем: $request")

                        val response = apiService.createPartial(request)
                        Log.d("CreateParty", "Код сервера: ${response.code()}")
                        Log.d("CreateParty", "Тело ответа: ${response.body()}")

                        if (response.isSuccessful) {    // Проверка по коду 200 OK, игнорируя опечатку "seccess")))
                            Toast.makeText(requireContext(), "Партия создана", Toast.LENGTH_SHORT).show()
                            soundManager.playSuccess()
                            delay(300)  // Ждем, пока база на сервере обновится
                            loadParties()
                        } else {
                            Toast.makeText(requireContext(), "Ошибка создания код ${response.code()}", Toast.LENGTH_SHORT).show()
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