package com.ocr.fastscan.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.ocr.fastscan.data.model.*
import com.ocr.fastscan.databinding.FragmentBoxDetailBinding
import kotlinx.coroutines.launch
import android.text.TextWatcher
import android.util.Log
import com.ocr.fastscan.R
import com.ocr.fastscan.utils.PrintHelper
import com.ocr.fastscan.utils.ScanUtils
import com.ocr.fastscan.utils.getBoxTareWeight
import com.ocr.fastscan.utils.getVolWeightDivider
import com.ocr.fastscan.utils.saveBoxTareWeight
import java.util.Locale

class BoxDetailFragment : BaseFragment() {

    private var _binding: FragmentBoxDetailBinding? = null
    private val binding get() = _binding!!

    private var boxGuid: String = ""
    private var boxCodeid: String = ""
    private var boxTracking: String = ""
    private var partialName: String = ""
    private var ordersGrossWeight: Double = 0.0

    private val ordersAdapter = OrdersAdapter { order: OrderDetails ->
        removeOrderFromBox(order.guid ?: "")
    }

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val value = result.data?.getStringExtra("scan_result") ?: ""
            val mode = result.data?.getStringExtra("scan_mode") ?: ""

            if (mode == "barcode" && value.isNotEmpty()) {
                addOrderByTrack(value)
            } else {
                Toast.makeText(requireContext(), R.string.scan_error_result, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val DEFAULT_BOX_TARE_WEIGHT = 0.8

        fun newInstance(
            boxGuid: String,
            boxCodeid: String,
            boxTracking: String,
            boxWeight: String,
            partialName: String = "España"
        ): BoxDetailFragment {
            val fragment = BoxDetailFragment()
            val args = Bundle()
            args.putString("box_guid", boxGuid)
            args.putString("codeid", boxCodeid)
            args.putString("box_tracking", boxTracking)
            args.putString("box_weight", boxWeight)
            args.putString("partial_name", partialName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        boxGuid = arguments?.getString("box_guid") ?: ""
        boxCodeid = arguments?.getString("codeid") ?: ""
        boxTracking = arguments?.getString("box_tracking") ?: ""
        partialName = arguments?.getString("partial_name") ?: "KG"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBoxDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrders.adapter = ordersAdapter
        binding.etBoxTareWeight.setText(formatWeight(requireContext().getBoxTareWeight()))

        setupClickListeners()
        setupSizeListeners()

        loadBoxInfo()
        loadOrdersInBox()
        
        // Загружаем имя партии только если оно не передано или является дефолтным
        if (partialName == "KG" || partialName == "España" || partialName.isBlank()) {
            loadPartialName()
        }
    }

    private fun setupClickListeners() {
        binding.btnAddOrder.setOnClickListener {
            ScanUtils.launchCamera(requireContext(), "barcode", scanLauncher)
        }

        binding.btnPasteOrderTrack.setOnClickListener {
            pasteOrderTrackFromClipboard()
        }

        binding.btnSaveSizes.setOnClickListener {
            updateSizes()
        }

        binding.btnPrint.setOnClickListener {
            printLabel()
        }

        binding.btnRefresh.setOnClickListener {
            loadBoxInfo()
            loadOrdersInBox()
            loadPartialName()
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun pasteOrderTrackFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val pastedTrack = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(requireContext())
            ?.toString()
            ?.trim()
            .orEmpty()

        if (pastedTrack.isBlank()) {
            Toast.makeText(requireContext(), R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
            soundManager.playError()
            return
        }

        addOrderByTrack(pastedTrack)
    }

    private fun loadOrdersInBox() {
        viewLifecycleOwner.lifecycleScope.launch {
            _binding?.progressBar?.visibility = View.VISIBLE
            try {
                val response = apiService.getOrderDetailsBox(boxTracking)
                if (_binding == null || !isAdded) return@launch

                if (response.isSuccessful && response.body() != null) {
                    val orders = response.body()!!
                    ordersAdapter.submitList(orders)

                    ordersGrossWeight = orders.sumOf { it.grossWeight ?: 0.0 }
                    _binding?.tvGrossWeight?.text = getString(R.string.weight_total, ordersGrossWeight)
                    _binding?.tvOrderCount?.text = getString(R.string.orders_count, orders.size)
                    updateGrossWeightFromOrdersAndBox()
                } else {
                    Toast.makeText(context, R.string.error_load_orders, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (isAdded) Toast.makeText(context, getString(R.string.net_error, e.localizedMessage), Toast.LENGTH_SHORT).show()
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun calculateVWeight(): Double {
        val length = _binding?.etLength?.text?.toString()?.toIntOrNull() ?: 70
        val width = _binding?.etWidth?.text?.toString()?.toIntOrNull() ?: 45
        val height = _binding?.etHeight?.text?.toString()?.toIntOrNull() ?: 50
        val divider = context?.getVolWeightDivider() ?: 6000.0
        return (length * width * height) / divider
    }

    private fun updateSizes() {
        val length = binding.etLength.text.toString().toIntOrNull() ?: 70
        val width = binding.etWidth.text.toString().toIntOrNull() ?: 45
        val height = binding.etHeight.text.toString().toIntOrNull() ?: 50
        val weight = parseWeight(binding.etGrossWeight.text.toString()) ?: 0.0
        val boxTareWeight = parseWeight(binding.etBoxTareWeight.text.toString()) ?: requireContext().getBoxTareWeight()

        if (length <= 0 || width <= 0 || height <= 0) {
            Toast.makeText(requireContext(), R.string.all_values_positive, Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            _binding?.progressBar?.visibility = View.VISIBLE
            try {
                val request = BoxRequest(
                    guid = boxGuid,
                    codeid = boxCodeid,
                    trackingCode = boxTracking,
                    width = width,
                    height = height,
                    length = length,
                    weight = weight,
                    vWeight = calculateVWeight()
                )
                val response = apiService.createOrUpdateBox(request)
                if (_binding == null || !isAdded) return@launch

                if (response.isSuccessful && response.body()?.success == true) {
                    requireContext().saveBoxTareWeight(boxTareWeight)
                    Toast.makeText(context, R.string.weight_saved, Toast.LENGTH_SHORT).show()
                    loadBoxInfo()
                } else {
                    Toast.makeText(context, R.string.error_saving_dim, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (isAdded) Toast.makeText(context, getString(R.string.net_error, e.localizedMessage), Toast.LENGTH_SHORT).show()
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun setupSizeListeners() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                _binding?.tvVWeight?.text = getString(R.string.volume_weight, calculateVWeight())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        binding.etLength.addTextChangedListener(watcher)
        binding.etWidth.addTextChangedListener(watcher)
        binding.etHeight.addTextChangedListener(watcher)

        binding.etBoxTareWeight.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                updateGrossWeightFromOrdersAndBox()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updateGrossWeightFromOrdersAndBox() {
        val activeBinding = _binding ?: return
        val boxTareWeight = parseWeight(activeBinding.etBoxTareWeight.text.toString())
            ?: context?.getBoxTareWeight()
            ?: DEFAULT_BOX_TARE_WEIGHT
        activeBinding.etGrossWeight.setText(formatWeight(ordersGrossWeight + boxTareWeight))
    }

    private fun removeOrderFromBox(orderGuid: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = RemoveOrderRequest(orderGuid)
                val response = apiService.removeOrderFromBox(request)
                if (_binding == null || !isAdded) return@launch
                
                if (response.isSuccessful) {
                    Toast.makeText(context, R.string.order_deleted, Toast.LENGTH_SHORT).show()
                    loadOrdersInBox()
                } else {
                    Toast.makeText(context, R.string.error_deleting, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (isAdded) Toast.makeText(context, getString(R.string.net_error, e.localizedMessage), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun printLabel() {
        val length = binding.etLength.text.toString().toIntOrNull()
        val width = binding.etWidth.text.toString().toIntOrNull()
        val height = binding.etHeight.text.toString().toIntOrNull()
        val weightValue = parseWeight(binding.etGrossWeight.text.toString())

        if (weightValue == null || weightValue <= 0.0) {
            Toast.makeText(requireContext(), R.string.incorrect_weight, Toast.LENGTH_SHORT).show()
            soundManager.playError()
            return
        }
        val weightClean = formatWeight(weightValue)

        val trimmedTracking = boxTracking.trim()
        val last4 = if (trimmedTracking.isNotEmpty()) trimmedTracking.takeLast(4).padStart(4, '0') else "0000"

        try {
            val act = requireActivity()
            val printHelper = PrintHelper(act)
            printHelper.printBoxLabel(
                trimmedTracking,
                weightClean,
                last4,
                partialName,
                length = length?.toString() ?: "0",
                width = width?.toString() ?: "0",
                height = height?.toString() ?: "0"
            )
            soundManager.playPrint()
            Toast.makeText(requireContext(), R.string.sent_for_printing, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("printHelper", "Ошибка печати", e)
            if (isAdded) Toast.makeText(requireContext(), getString(R.string.error_printing, e.localizedMessage), Toast.LENGTH_LONG).show()
            soundManager.playError()
        }
    }

    private fun addOrderByTrack(tcode: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val searchResponse = apiService.searchOrder(tcode)
                if (_binding == null || !isAdded) return@launch

                if (!searchResponse.isSuccessful || searchResponse.body().isNullOrEmpty()) {
                    val msg = if (!searchResponse.isSuccessful) getString(R.string.search_error, searchResponse.code()) else getString(R.string.order_not_found)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    soundManager.playError()
                    return@launch
                }

                val order = searchResponse.body()!![0]
                val orderGuid = order.guid ?: run {
                    Toast.makeText(context, R.string.no_GUID, Toast.LENGTH_SHORT).show()
                    soundManager.playError()
                    return@launch
                }

                val addRequest = BoxOrderRequest(order = orderGuid, box = boxGuid, desc = "España BMG")
                val addResponse = apiService.addOrderToBox(addRequest)
                if (_binding == null || !isAdded) return@launch

                if (addResponse.isSuccessful) {
                    Toast.makeText(context, getString(R.string.order_added), Toast.LENGTH_SHORT).show()
                    soundManager.playSuccess()
                    loadOrdersInBox()

                    val entry = BoxEntry(
                        orderTracking = tcode,
                        clientCode = order.clientCode,
                        client = order.client,
                        boxTracking = boxTracking,
                        date = Timestamp.now(),
                        orderWeight = order.grossWeight ?: 0.0
                    )
                    firebaseManager.saveBoxEntry(entry, {}, { e ->
                        Log.e("AddOrder", "Firebase error", e)
                    })
                } else {
                    Toast.makeText(context, getString(R.string.server_error, addResponse.code()), Toast.LENGTH_LONG).show()
                    soundManager.playError()
                }
            } catch (e: Exception) {
                if (isAdded) Toast.makeText(context, getString(R.string.net_error, e.localizedMessage), Toast.LENGTH_LONG).show()
                soundManager.playError()
            }
        }
    }

    private fun loadBoxInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            _binding?.progressBar?.visibility = View.VISIBLE
            try {
                val response = apiService.getBoxes(tCode = boxTracking)
                if (_binding == null || !isAdded) return@launch

                if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                    val box = response.body()!![0]
                    _binding?.tvBoxNumber?.text = getString(R.string.box, boxTracking)
                    _binding?.etLength?.setText(box.length?.toString() ?: "70")
                    _binding?.etWidth?.setText(box.width?.toString() ?: "45")
                    _binding?.etHeight?.setText(box.height?.toString() ?: "50")
                    if (_binding?.etBoxTareWeight?.text.isNullOrBlank()) {
                        _binding?.etBoxTareWeight?.setText(formatWeight(requireContext().getBoxTareWeight()))
                    }
                    updateGrossWeightFromOrdersAndBox()
                    _binding?.tvVWeight?.text = getString(R.string.volume_weight, box.vWeight ?: calculateVWeight())
                } else {
                    Toast.makeText(context, R.string.error_load_detal, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (isAdded) Toast.makeText(context, R.string.net_error_short, Toast.LENGTH_SHORT).show()
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun loadPartialName() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Загружаем только открытые партии (typePartial = 0)
                val response = apiService.getPartials(0)
                if (_binding == null || !isAdded) return@launch

                if (response.isSuccessful) {
                    val parties = response.body() ?: emptyList()
                    if (parties.isNotEmpty()) {
                        // Сортируем по дате убыванию, чтобы взять самую свежую
                        val latestParty = parties.sortedByDescending { it.date_created }.first()
                        partialName = latestParty.partial_name.trim()
                        Log.d("BoxDetail", "Загружено актуальное имя партии: $partialName")
                    }
                }
            } catch (e: Exception) {
                Log.e("BoxDetail", "Load partial error", e)
            }
        }
    }

    private fun parseWeight(value: String): Double? {
        return value
            .replace(Regex("[^0-9,.]"), "")
            .replace(",", ".")
            .trim()
            .toDoubleOrNull()
    }

    private fun formatWeight(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
