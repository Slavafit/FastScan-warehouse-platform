package com.ocr.fastscan.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.UnpackedOrder
import com.ocr.fastscan.databinding.FragmentUnpackedOrdersBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class UnpackedOrdersFragment : BaseFragment() {

    private var _binding: FragmentUnpackedOrdersBinding? = null
    private val binding get() = _binding!!

    private val unpackedAdapter = UnpackedOrdersAdapter()

    private var allUnpackedOrders = listOf<UnpackedOrder>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUnpackedOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadUnpackedOrders(days = 1) // По умолчанию — за сегодня
    }

    private fun setupUI() {
        binding.rvUnpacked.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUnpacked.adapter = unpackedAdapter

        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            filterList(text?.toString() ?: "")
        }

        binding.chipToday.setOnClickListener { loadUnpackedOrders(1) }
        binding.chip3Days.setOnClickListener { loadUnpackedOrders(3) }
        binding.chipWeek.setOnClickListener { loadUnpackedOrders(7) }
        binding.chipAll.setOnClickListener { loadUnpackedOrders(0) }

        binding.btnShareReport.setOnClickListener { shareReport() }
    }

    private fun loadUnpackedOrders(days: Int) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val cutoffDate = if (days > 0) {
                    Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -days)
                    }.time
                } else null

                // 1. Загрузка данных из Firebase
                val shipments = withContext(Dispatchers.IO) {
                    if (cutoffDate != null) firebaseManager.getShipmentsAfter(cutoffDate)
                    else firebaseManager.getAllShipments()
                }

                val boxEntries = withContext(Dispatchers.IO) {
                    if (cutoffDate != null) firebaseManager.getBoxEntriesAfter(cutoffDate)
                    else firebaseManager.getAllBoxEntries()
                }

                // 2. Обработка данных в Default потоке (чтобы не фризить UI)
                val processedOrders = withContext(Dispatchers.Default) {
                    val packedTrackings = boxEntries.map { it.orderTracking }.toSet()
                    
                    shipments
                        .filter { it.trackingNumber !in packedTrackings }
                        .map { shipment ->
                            UnpackedOrder(
                                trackingNumber = shipment.trackingNumber,
                                clientName = shipment.clientName ?: "—",
                                clientCode = shipment.clientCode ?: "—",
                                grossWeight = shipment.weight ?: 0.0,
                                registeredAt = shipment.createdAt
                            )
                        }
                        .sortedByDescending { it.registeredAt.toDate() }
                }

                allUnpackedOrders = processedOrders

                // 3. Обновление UI
                if (_binding != null) {
                    unpackedAdapter.submitList(allUnpackedOrders)
                    binding.tvCount.text = getString(R.string.unpacked_count_default, allUnpackedOrders.size)
                    binding.tvEmptyState.visibility = if (allUnpackedOrders.isEmpty()) View.VISIBLE else View.GONE
                }

            } catch (e: Exception) {
                Log.e("UnpackedFragment", "Load error", e)
                Toast.makeText(requireContext(), R.string.error_load_data, Toast.LENGTH_LONG).show()
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun filterList(query: String) {
        lifecycleScope.launch(Dispatchers.Default) {
            val filteredList = if (query.isBlank()) {
                allUnpackedOrders
            } else {
                allUnpackedOrders.filter { order ->
                    order.trackingNumber.contains(query, ignoreCase = true) ||
                            order.clientName.contains(query, ignoreCase = true) ||
                            order.clientCode.contains(query, ignoreCase = true)
                }
            }

            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    unpackedAdapter.submitList(filteredList)
                    binding.tvEmptyState.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun shareReport() {
        if (allUnpackedOrders.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_data_to_send, Toast.LENGTH_SHORT).show()
            return
        }

        val reportText = buildReportText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    private fun buildReportText(): String {
        val sb = StringBuilder()
        sb.append(getString(R.string.unpacked_orders_title)).append("\n")
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        sb.append(getString(R.string.date, sdf)).append("\n")
        sb.append(getString(R.string.unpacked_count_default, allUnpackedOrders.size)).append("\n\n")

        allUnpackedOrders.forEach { order ->
            sb.append("📦 `${order.trackingNumber}`\n")
            sb.append("👤 ${order.clientName} (${order.clientCode})\n")
            sb.append("⚖️ ").append(getString(R.string.weight_format, order.grossWeight)).append("\n")
            sb.append("────────────────\n")
        }
        sb.append("\n").append(getString(R.string.report_end))
        return sb.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
