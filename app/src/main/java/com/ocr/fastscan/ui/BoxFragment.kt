package com.ocr.fastscan.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ocr.fastscan.R
import com.ocr.fastscan.databinding.FragmentBoxBinding
import com.ocr.fastscan.utils.DialogUtils
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
    
    private val boxesAdapter = BoxesAdapter { box ->
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
    }

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
                        loadBoxes(isOpenOnly = !isShowingAllBoxes)
                        Toast.makeText(requireContext(), getString(R.string.box_added_format, trackingCode), Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { errorMsg ->
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                }
            )
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

    private fun loadBoxes(isOpenOnly: Boolean = true) {
        if (isLoading) return
        isLoading = true

        binding.progressBar.visibility = View.VISIBLE
        binding.tvNoBoxes.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = apiService.getBoxes(status = if (isOpenOnly) 0 else null)
                
                if (_binding == null) return@launch

                if (response.isSuccessful && response.body() != null) {
                    val boxes = response.body()!!.map { box ->
                        box.copy(dateCreated = formatBoxDate(box.dateCreated))
                    }
                    boxesAdapter.submitList(boxes)

                    val baseText = if (isOpenOnly) getString(R.string.open_boxes) else getString(R.string.all_boxes)
                    binding.tvTitle.text = "$baseText (${boxes.size})"
                    binding.btnShowAll.text = if (isOpenOnly) getString(R.string.all_boxes) else getString(R.string.open_boxes)
                    
                    binding.tvNoBoxes.visibility = if (boxes.isEmpty()) View.VISIBLE else View.GONE
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
        super.onDestroyView()
        _binding = null
    }
}
