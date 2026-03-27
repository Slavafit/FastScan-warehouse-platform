package com.ocr.fastscan.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.BoxItem
import com.ocr.fastscan.databinding.ItemBoxBinding

class BoxesAdapter(
    private val onClick: (BoxItem) -> Unit
) : ListAdapter<BoxItem, BoxesAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemBoxBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(box: BoxItem, onClick: (BoxItem) -> Unit) {
            binding.tvTrackingCode.text = box.trackingCode
            binding.tvGrossWeight.text = getString(R.string.weight_format, box.grossWeight?: 0.0)
            binding.tvVWeight.text = getString(R.string.volume_weight, box.vWeight ?: 0.0)
            binding.tvSizes.text = getString(R.string.sizes, box.length?: 0.0, box.width?: 0.0, box.height?: 0.0)
            binding.tvOrderCount.text = getString(R.string.orders_count, box.orderCount?: 0.0)
            binding.tvDate.text = box.dateCreated

            binding.root.setOnClickListener { onClick(box) }
        }
        // Важно: добавляем функцию getString в ViewHolder
        private fun getString(resId: Int, vararg formatArgs: Any): String {
            return itemView.context.getString(resId, *formatArgs)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBoxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    object DiffCallback : DiffUtil.ItemCallback<BoxItem>() {
        override fun areItemsTheSame(oldItem: BoxItem, newItem: BoxItem): Boolean = oldItem.guid == newItem.guid
        override fun areContentsTheSame(oldItem: BoxItem, newItem: BoxItem): Boolean = oldItem == newItem
    }
}