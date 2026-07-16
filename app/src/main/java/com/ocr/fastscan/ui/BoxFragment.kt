package com.ocr.fastscan.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.BoxItem
import com.ocr.fastscan.data.model.BoxRequest
import com.ocr.fastscan.databinding.FragmentBoxBinding
import com.ocr.fastscan.utils.DialogUtils
import com.ocr.fastscan.utils.getBoxTareWeight
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.TimeZone

class BoxFragment : BaseFragment() {
    private var _binding: FragmentBoxBinding? = null
    private val binding get() = _binding!!
    
    private var isShowingAllBoxes = false
    private var isLoading = false
    private var isUpdatingTracking = false
    private var weightLoadRequestId = 0
    
    private val boxesAdapter = BoxesAdapter(
        onClick = { box ->
            val partialName = arguments?.getString("partial_name")
            val detailFragment = BoxDetailFragment.newInstance(
                boxGuid = box.guid ?: "",
                boxCodeid = box.codeid ?: "",
                boxTracking = box.trackingCode ?: "",
                boxWeight = box.grossWeight?.toString() ?: "0.0",
                partialName = partialName ?: "KG"
            )
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit()
        },
        onLongClick = { box ->
            showEditTrackingDialog(box)
        }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBoxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvBoxes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBoxes.adapter = boxesAdapter

        binding.btnAddNewBox.setOnClickListener {
            DialogUtils.showAddNewBoxDialog(
                lifecycleOwner = viewLifecycleOwner,
                context = requireContext(),
                onSuccess = { trackingCode ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(300)
                        loadBoxes(
                            isOpenOnly = !isShowingAllBoxes,
                            highlightTrackingCode = trackingCode
                        )
                        Toast.makeText(requireContext(), getString(R.string.box_added_format, trackingCode), Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { errorMsg ->
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                }
            )
        }

        binding.btnRefresh.setOnClickListener {
            loadBoxes(isOpenOnly = !isShowingAllBoxes)
        }

        binding.btnShowAll.setOnClickListener {
            isShowingAllBoxes = !isShowingAllBoxes
            binding.rvBoxes.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    loadBoxes(isOpenOnly = !isShowingAllBoxes)
                    binding.rvBoxes.animate().alpha(1f).setDuration(150).start()
                }.start()
        }
        
        loadBoxes(isOpenOnly = true)
    }

    private fun loadBoxes(
        isOpenOnly: Boolean = true,
        highlightTrackingCode: String? = null
    ) {
        if (isLoading) return
        isLoading = true

        binding.progressBar.visibility = View.VISIBLE
        binding.tvNoBoxes.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = apiService.getBoxes(status = if (isOpenOnly) 0 else null)
                
                if (_binding == null) return@launch

                if (response.isSuccessful && response.body() != null) {
                    val boxTareWeight = requireContext().getBoxTareWeight()
                    val requestId = ++weightLoadRequestId
                    val boxes = prepareBoxesForDisplay(response.body()!!, highlightTrackingCode)

                    boxesAdapter.submitList(boxes) {
                        if (highlightTrackingCode != null) {
                            val newBoxPosition = boxes.indexOfFirst {
                                it.trackingCode == highlightTrackingCode
                            }
                            if (newBoxPosition >= 0 && _binding != null) {
                                boxesAdapter.highlightBox(highlightTrackingCode)
                                binding.rvBoxes.scrollToPosition(newBoxPosition)
                                binding.rvBoxes.postDelayed(
                                    { boxesAdapter.clearHighlight(highlightTrackingCode) },
                                    NEW_BOX_HIGHLIGHT_DURATION_MS
                                )
                            }
                        }
                    }

                    val baseText = if (isOpenOnly) getString(R.string.open_boxes) else getString(R.string.all_boxes)
                    binding.tvTitle.text = "$baseText (${boxes.size})"
                    binding.btnShowAll.text = if (isOpenOnly) getString(R.string.all_boxes) else getString(R.string.open_boxes)
                    
                    binding.tvNoBoxes.visibility = if (boxes.isEmpty()) View.VISIBLE else View.GONE
                    updateCalculatedBoxWeights(boxes, boxTareWeight, requestId)
                } else {
                    Toast.makeText(requireContext(), R.string.error_load, Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BoxFragment", "Load error", e)
                Toast.makeText(requireContext(), R.string.net_error_short, Toast.LENGTH_SHORT).show()
            } finally {
                _binding?.progressBar?.visibility = View.GONE
                isLoading = false
            }
        }
    }

    private fun prepareBoxesForDisplay(
        boxes: List<BoxItem>,
        preferredTrackingCode: String? = null
    ): List<BoxItem> {
        val dedupedByIdentity = linkedMapOf<String, BoxItem>()
        boxes
            .map { box -> box.copy(dateCreated = formatBoxDate(box.dateCreated)) }
            .forEach { box ->
                val key = boxIdentityKey(box) ?: box.trackingCode.orEmpty()
                if (key.isBlank()) return@forEach

                val current = dedupedByIdentity[key]
                val selectedBox = when {
                    current == null -> box
                    box.trackingCode == preferredTrackingCode -> box
                    current.trackingCode == preferredTrackingCode -> current
                    else -> box
                }
                dedupedByIdentity[key] = selectedBox
            }
        return dedupedByIdentity.values.toList()
    }

    private fun boxIdentityKey(box: BoxItem): String? {
        return box.guid?.takeIf { it.isNotBlank() }
            ?: box.codeid?.takeIf { it.isNotBlank() }
    }

    private fun updateCalculatedBoxWeights(
        boxes: List<BoxItem>,
        boxTareWeight: Double,
        requestId: Int
    ) {
        if (boxes.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            boxes.chunked(BOX_WEIGHT_BATCH_SIZE).forEach { chunk ->
                val calculatedBoxes = chunk.map { box ->
                    async {
                        val ordersWeight = loadOrdersWeight(box.trackingCode)
                        if (ordersWeight == null) null else box.copy(grossWeight = ordersWeight + boxTareWeight)
                    }
                }.awaitAll().filterNotNull()

                if (_binding == null || requestId != weightLoadRequestId) return@launch
                if (calculatedBoxes.isEmpty()) return@forEach

                val calculatedByGuid = calculatedBoxes
                    .mapNotNull { box -> box.guid?.let { it to box } }
                    .toMap()
                val calculatedByTracking = calculatedBoxes
                    .mapNotNull { box -> box.trackingCode?.let { it to box } }
                    .toMap()
                val updatedList = boxesAdapter.currentList.map { currentBox ->
                    val calculatedWeight = calculatedByGuid[currentBox.guid]?.grossWeight
                        ?: calculatedByTracking[currentBox.trackingCode]?.grossWeight
                    if (calculatedWeight == null) currentBox else currentBox.copy(grossWeight = calculatedWeight)
                }
                boxesAdapter.submitList(updatedList)
            }
        }
    }

    private suspend fun loadOrdersWeight(trackingCode: String?): Double? {
        if (trackingCode.isNullOrBlank()) return null
        return try {
            val response = apiService.getOrderDetailsBox(trackingCode)
            if (response.isSuccessful) {
                response.body()?.sumOf { it.grossWeight ?: 0.0 }
            } else {
                Log.w("BoxFragment", "Failed to load box orders for $trackingCode: ${response.code()}")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("BoxFragment", "Failed to calculate box gross weight for $trackingCode", e)
            null
        }
    }

    private fun showEditTrackingDialog(box: BoxItem) {
        val currentTrackingCode = box.trackingCode.orEmpty()
        val dialogPadding = (24 * resources.displayMetrics.density).toInt()
        val fieldTopMargin = (12 * resources.displayMetrics.density).toInt()

        val input = EditText(requireContext()).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(currentTrackingCode)
            setSelection(text.length)
            hint = getString(R.string.tracking_code)
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dialogPadding, fieldTopMargin, dialogPadding, 0)
            addView(TextView(requireContext()).apply {
                text = getString(R.string.current_box_tracking, currentTrackingCode)
                textSize = 14f
            })
            addView(input, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = fieldTopMargin
            })
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_box_tracking_title)
            .setView(content)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            input.requestFocus()
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val newTrackingCode = normalizeTrackingCode(input.text.toString())
                val oldTrackingCode = currentTrackingCode

                if (newTrackingCode.isBlank()) {
                    Toast.makeText(requireContext(), R.string.box_tracking_empty, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newTrackingCode == oldTrackingCode) {
                    Toast.makeText(requireContext(), R.string.box_tracking_unchanged, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setOnClickListener
                }

                updateBoxTracking(box, newTrackingCode, dialog)
            }
        }

        dialog.show()
    }

    private fun normalizeTrackingCode(value: String): String {
        return value
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .lastOrNull()
            .orEmpty()
    }

    private fun updateBoxTracking(box: BoxItem, newTrackingCode: String, dialog: AlertDialog) {
        if (isUpdatingTracking) return
        weightLoadRequestId++
        viewLifecycleOwner.lifecycleScope.launch {
            _binding?.progressBar?.visibility = View.VISIBLE
            try {
                isUpdatingTracking = true
                val request = BoxRequest(
                    guid = box.guid.orEmpty(),
                    codeid = box.codeid.orEmpty(),
                    trackingCode = newTrackingCode,
                    width = box.width ?: 45,
                    height = box.height ?: 50,
                    length = box.length ?: 70,
                    vWeight = box.vWeight ?: 0.0,
                    weight = box.grossWeight
                )
                val response = apiService.createOrUpdateBox(request)
                if (_binding == null || !isAdded) return@launch

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(requireContext(), R.string.box_tracking_updated, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    replaceBoxTrackingInCurrentList(box, newTrackingCode)
                    loadBoxes(
                        isOpenOnly = !isShowingAllBoxes,
                        highlightTrackingCode = newTrackingCode
                    )
                } else {
                    Toast.makeText(requireContext(), R.string.error_update_box_tracking, Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BoxFragment", "Failed to update box tracking", e)
                if (isAdded) {
                    Toast.makeText(requireContext(), getString(R.string.net_error, e.localizedMessage), Toast.LENGTH_SHORT).show()
                }
            } finally {
                isUpdatingTracking = false
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun replaceBoxTrackingInCurrentList(editedBox: BoxItem, newTrackingCode: String) {
        val editedKey = boxIdentityKey(editedBox)
        val oldTrackingCode = editedBox.trackingCode
        val updatedList = boxesAdapter.currentList
            .map { currentBox ->
                val isSameBox = if (editedKey != null) {
                    boxIdentityKey(currentBox) == editedKey
                } else {
                    currentBox.trackingCode == oldTrackingCode
                }
                if (isSameBox) currentBox.copy(trackingCode = newTrackingCode) else currentBox
            }
            .let { prepareBoxesForDisplay(it, newTrackingCode) }
        boxesAdapter.submitList(updatedList)
    }

    private fun formatBoxDate(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        return try {
            // Попытка распарсить стандартный ISO формат
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            // Очищаем строку от миллисекунд и Z для стабильности парсинга вручную, если нужно
            val cleanedDate = isoDate.split(".")[0].replace("Z", "")
            val date = inputFormat.parse(cleanedDate) ?: return isoDate
            
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    override fun onDestroyView() {
        weightLoadRequestId++
        _binding?.rvBoxes?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val NEW_BOX_HIGHLIGHT_DURATION_MS = 2500L
        const val BOX_WEIGHT_BATCH_SIZE = 4
    }
}
