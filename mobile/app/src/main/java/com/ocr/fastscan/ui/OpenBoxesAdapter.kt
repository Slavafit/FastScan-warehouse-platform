package com.ocr.fastscan.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.ocr.fastscan.R
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ocr.fastscan.data.model.BoxInPartial
import com.ocr.fastscan.databinding.ItemOpenBoxBinding

class OpenBoxesAdapter(
    private val currentPartialGuid: String,
    initiallySelected: Set<String>,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<BoxInPartial, OpenBoxesAdapter.ViewHolder>(BoxDiffCallback()) {

    private val selectedGuids = initiallySelected.toMutableSet()

    fun selectedGuids(): Set<String> = selectedGuids.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOpenBoxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
//Add box dialog
    inner class ViewHolder(private val binding: ItemOpenBoxBinding) : RecyclerView.ViewHolder(binding.root) {

        private val context = binding.root.context
        fun bind(box: BoxInPartial) {
            binding.tvTrackingCode.text = context.getString(
                R.string.track_code, box.tracking_code)
            binding.tvDimensions.text = context.getString(
                R.string.sizes, box.width, box.height, box.length)
            binding.tvGrossWeight.text = context.getString(R.string.weight_format, box.gross_weight)
            binding.tvVWeight.text = context.getString(R.string.volume_weight, box.v_weight)
            val belongsToCurrent = box.partial == currentPartialGuid
            val belongsToAnother = box.partial.isNotBlank() && !belongsToCurrent
            binding.cbSelected.setOnCheckedChangeListener(null)
            binding.cbSelected.isChecked = selectedGuids.contains(box.guid)
            binding.cbSelected.isEnabled = !belongsToAnother
            binding.tvBatchStatus.visibility = if (belongsToAnother) View.VISIBLE else View.GONE
            binding.tvBatchStatus.text = context.getString(
                R.string.box_in_another_party,
                box.partial_name?.takeIf { it.isNotBlank() } ?: box.partial
            )
            binding.root.alpha = if (belongsToAnother) 0.55f else 1f

            val toggleSelection = {
                if (!belongsToAnother) {
                    if (selectedGuids.contains(box.guid)) selectedGuids.remove(box.guid)
                    else selectedGuids.add(box.guid)
                    binding.cbSelected.isChecked = selectedGuids.contains(box.guid)
                    onSelectionChanged(selectedGuids.size)
                }
            }
            binding.root.setOnClickListener { toggleSelection() }
            binding.cbSelected.setOnClickListener { toggleSelection() }
        }
    }
}

// Same DiffCallback as the other adapters
class BoxDiffCallback : DiffUtil.ItemCallback<BoxInPartial>() {
    override fun areItemsTheSame(oldItem: BoxInPartial, newItem: BoxInPartial) = oldItem.guid == newItem.guid
    override fun areContentsTheSame(oldItem: BoxInPartial, newItem: BoxInPartial) = oldItem == newItem
}
