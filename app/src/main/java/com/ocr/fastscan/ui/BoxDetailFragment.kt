package com.ocr.fastscan.ui

import android.widget.Toast
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.data.model.BoxRequest
import com.ocr.fastscan.data.model.OrderInBox
import com.ocr.fastscan.data.model.RemoveOrderRequest
import com.ocr.fastscan.databinding.FragmentBoxDetailBinding
import com.ocr.fastscan.utils.AppDependencies
import com.ocr.fastscan.utils.ScanUtils
import kotlinx.coroutines.launch

class BoxDetailFragment : Fragment() {

    private var _binding: FragmentBoxDetailBinding? = null
    private val binding get() = _binding!!

    private val apiService = AppDependencies.apiService
    private val firebaseManager = AppDependencies.firebaseManager
    private val soundManager = AppDependencies.soundManager
    private val printHelper = AppDependencies.printHelper

    private var boxGuid: String = ""
    private var boxTracking: String = ""

    private val ordersAdapter = OrdersInBoxAdapter { order ->
        // Удаление заказа
        removeOrderFromBox(order.guid ?: "")
    }

    companion object {
        fun newInstance(boxGuid: String, boxTracking: String): BoxDetailFragment {
            val fragment = BoxDetailFragment()
            val args = Bundle()
            args.putString("box_guid", boxGuid)
            args.putString("box_tracking", boxTracking)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBoxDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        boxGuid = arguments?.getString("box_guid") ?: ""
        boxTracking = arguments?.getString("box_tracking") ?: ""

        binding.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrders.adapter = ordersAdapter

        loadBoxDetails()

        binding.btnRefresh.setOnClickListener {
            loadBoxDetails()
        }

        binding.btnPrint.setOnClickListener {
            printLabel()
        }

        binding.btnAddOrder.setOnClickListener {
            // Запуск скана трека
            ScanUtils.launchCamera(requireContext(), "barcode", scanLauncher)
        }

        binding.btnSaveSizes.setOnClickListener {
            updateSizes()
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun loadBoxDetails() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = apiService.getOrderDetails(boxTracking)
                if (response.isSuccessful && response.body() != null) {
                    val orders = response.body()!!  // список заказов
                    ordersAdapter.submitList(orders)
                    binding.tvGrossWeight.text = "Вес: ${calculateGrossWeight(orders)} кг"
                    binding.tvOrderCount.text = "Заказов: ${orders.size}"
                    binding.tvVWeight.text = "Объёмный вес: ${calculateVWeight()} кг"
                    // Размеры из prefs или из API — если из API, подставь
                } else {
                    Toast.makeText(requireContext(), "Ошибка загрузки деталей коробки", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun calculateGrossWeight(orders: List<OrderInBox>): Double {
        return orders.sumOf { it.weight ?: 0.0 }
    }

    private fun calculateVWeight(): Double {
        val length = binding.etLength.text.toString().toIntOrNull() ?: 70
        val width = binding.etWidth.text.toString().toIntOrNull() ?: 45
        val height = binding.etHeight.text.toString().toIntOrNull() ?: 50
        val divider = PreferenceManager.getDefaultSharedPreferences(requireContext()).getInt("divider", 6000)
        return (length * width * height) / divider.toDouble()
    }

    private fun updateSizes() {
        lifecycleScope.launch {
            try {
                val request = BoxRequest(
                    boxTracking,
                    binding.etWidth.text.toString().toInt(),
                    binding.etHeight.text.toString().toInt(),
                    binding.etLength.text.toString().toInt()
                )
                val response = apiService.createOrUpdateBox(request)
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Размеры обновлены", Toast.LENGTH_SHORT).show()
                    loadBoxDetails()  // обновляем экран
                } else {
                    Toast.makeText(requireContext(), "Ошибка обновления размеров", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeOrderFromBox(orderGuid: String) {
        lifecycleScope.launch {
            try {
                val request = RemoveOrderRequest(orderGuid)
                val response = apiService.removeOrderFromBox(request)
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Заказ удалён", Toast.LENGTH_SHORT).show()
                    loadBoxDetails()
                } else {
                    Toast.makeText(requireContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Сеть ошибка", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun printLabel() {
        val weight = binding.tvGrossWeight.text.toString().replace("Вес: ", "").replace(" кг", "")
        val last4 = boxTracking.takeLast(4)

        printHelper.printBoxLabel(boxTracking, weight, last4)
        soundManager.playSuccess()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}