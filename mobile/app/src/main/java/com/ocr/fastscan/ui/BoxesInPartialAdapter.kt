package com.ocr.fastscan.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ocr.fastscan.data.model.BoxInPartial
import com.ocr.fastscan.databinding.ItemBoxInPartialBinding
import com.ocr.fastscan.R

class BoxesInPartialAdapter(
    private val onOpen: (BoxInPartial) -> Unit,
    private val onRemove: (BoxInPartial) -> Unit
) : ListAdapter<BoxInPartial, BoxesInPartialAdapter.ViewHolder>(BoxInPartialDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBoxInPartialBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val box = getItem(position)
        holder.bind(box)
    }

    inner class ViewHolder(private val binding: ItemBoxInPartialBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(box: BoxInPartial) {
            binding.tvTrackingCode.text = getString(R.string.box, box.tracking_code)
            binding.tvDimensions.text = getString(R.string.sizes, box.length, box.width, box.height)
            binding.tvGrossWeight.text = getString(R.string.weight_format, box.gross_weight)
            binding.tvVWeight.text = getString(R.string.volume_weight, box.v_weight)

            binding.root.setOnClickListener { onOpen(box) }
            binding.btnRemoveBox.setOnClickListener { onRemove(box) }
            binding.root.setOnLongClickListener {
                onRemove(box)
                true
            }
        }

        private fun getString(resId: Int, vararg formatArgs: Any): String {
            return itemView.context.getString(resId, *formatArgs)
        }
    }
}

class BoxInPartialDiffCallback : DiffUtil.ItemCallback<BoxInPartial>() {
    override fun areItemsTheSame(oldItem: BoxInPartial, newItem: BoxInPartial): Boolean {
        // Use guid plus tracking_code for maximum accuracy
        return oldItem.guid == newItem.guid && oldItem.tracking_code == newItem.tracking_code
    }

    override fun areContentsTheSame(oldItem: BoxInPartial, newItem: BoxInPartial): Boolean {
        return oldItem == newItem
    }
}
