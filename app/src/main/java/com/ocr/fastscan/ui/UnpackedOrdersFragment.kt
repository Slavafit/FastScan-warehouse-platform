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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class UnpackedOrdersFragment : BaseFragment() {

    private var _binding: FragmentUnpackedOrdersBinding? = null
    private val binding get() = _binding!!

    private val unpackedAdapter = UnpackedOrdersAdapter()

    private var allUnpackedOrders = listOf<UnpackedOrder>()
    private var loadJob: Job? = null
    private var filterJob: Job? = null
    private var selectedDays = 1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUnpackedOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadUnpackedOrders(days = selectedDays)
    }

    private fun setupUI() {
        binding.rvUnpacked.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUnpacked.adapter = unpackedAdapter

        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            filterList(text?.toString().orEmpty())
        }

        binding.chipToday.setOnClickListener { loadUnpackedOrders(1) }
        binding.chip3Days.setOnClickListener { loadUnpackedOrders(3) }
        binding.chipWeek.setOnClickListener { loadUnpackedOrders(7) }
        binding.chipAll.setOnClickListener { loadUnpackedOrders(0) }

        binding.btnRefresh.setOnClickListener { loadUnpackedOrders(selectedDays) }
        binding.btnShareReport.setOnClickListener { shareReport() }
    }

    private fun loadUnpackedOrders(days: Int) {
        val currentBinding = _binding ?: return
        selectedDays = days
        currentBinding.progressBar.visibility = View.VISIBLE
        currentBinding.tvEmptyState.visibility = View.GONE

        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val cutoffDate = if (days > 0) {
                    Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -days)
                    }.time
                } else {
                    null
                }

                val shipments = withContext(Dispatchers.IO) {
                    if (cutoffDate != null) firebaseManager.getShipmentsAfter(cutoffDate)
                    else firebaseManager.getAllShipments()
                }

                val boxEntries = withContext(Dispatchers.IO) {
                    if (cutoffDate != null) firebaseManager.getBoxEntriesAfter(cutoffDate)
                    else firebaseManager.getAllBoxEntries()
                }

                val processedOrders = withContext(Dispatchers.Default) {
                    val packedTrackings = boxEntries.map { it.orderTracking }.toSet()

                    shipments
                        .filter { it.trackingNumber !in packedTrackings }
                        .map { shipment ->
                            UnpackedOrder(
                                trackingNumber = shipment.trackingNumber,
                                clientName = shipment.clientName ?: "-",
                                clientCode = shipment.clientCode ?: "-",
                                grossWeight = shipment.weight ?: 0.0,
                                registeredAt = shipment.createdAt
                            )
                        }
                        .sortedByDescending { it.registeredAt.toDate() }
                }

                allUnpackedOrders = processedOrders

                val activeBinding = _binding ?: return@launch
                activeBinding.tvCount.text = getString(R.string.unpacked_count_default, allUnpackedOrders.size)
                applyFilter(activeBinding.etSearch.text?.toString().orEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("UnpackedFragment", "Load error", e)
                context?.let { Toast.makeText(it, R.string.error_load_data, Toast.LENGTH_LONG).show() }
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun filterList(query: String) {
        filterJob?.cancel()
        filterJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val filteredList = filterOrders(query)

            withContext(Dispatchers.Main) {
                _binding?.let { activeBinding ->
                    unpackedAdapter.submitList(filteredList)
                    activeBinding.tvEmptyState.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun applyFilter(query: String) {
        val filteredList = filterOrders(query)
        unpackedAdapter.submitList(filteredList)
        _binding?.tvEmptyState?.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun filterOrders(query: String): List<UnpackedOrder> {
        return if (query.isBlank()) {
            allUnpackedOrders
        } else {
            allUnpackedOrders.filter { order ->
                order.trackingNumber.contains(query, ignoreCase = true) ||
                    order.clientName.contains(query, ignoreCase = true) ||
                    order.clientCode.contains(query, ignoreCase = true)
            }
        }
    }

    private fun shareReport() {
        val currentContext = context ?: return
        if (allUnpackedOrders.isEmpty()) {
            Toast.makeText(currentContext, R.string.no_data_to_send, Toast.LENGTH_SHORT).show()
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
            sb.append("Box: `${order.trackingNumber}`\n")
            sb.append("Client: ${order.clientName} (${order.clientCode})\n")
            sb.append(getString(R.string.weight_format, order.grossWeight)).append("\n")
            sb.append("----------------\n")
        }
        sb.append("\n").append(getString(R.string.report_end))
        return sb.toString()
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        filterJob?.cancel()
        _binding?.rvUnpacked?.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
