package com.ocr.fastscan.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ocr.fastscan.data.model.BoxItem
import com.ocr.fastscan.databinding.ItemBoxBinding

class BoxesAdapter(
    private val onClick: (BoxItem) -> Unit
) : ListAdapter<BoxItem, BoxesAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemBoxBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(box: BoxItem, onClick: (BoxItem) -> Unit) {
            binding.tvTrackingCode.text = box.trackingCode
            binding.tvGrossWeight.text = "Вес: ${box.grossWeight} кг"
            binding.tvVWeight.text = "Объёмный вес: ${box.vWeight} кг"
            binding.tvSizes.text = "Размеры: ${box.length} x ${box.width} x ${box.height}"
            binding.tvOrderCount.text = "Заказов: ${box.orderCount}"
            binding.tvDate.text = box.dateCreated

            binding.root.setOnClickListener { onClick(box) }
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