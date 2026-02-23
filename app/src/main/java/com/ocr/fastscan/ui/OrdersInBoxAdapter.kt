package com.ocr.fastscan.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ocr.fastscan.data.model.OrderInBox
import com.ocr.fastscan.databinding.ItemOrderInBoxBinding

class OrdersInBoxAdapter(
    private val onDelete: (OrderInBox) -> Unit
) : ListAdapter<OrderInBox, OrdersInBoxAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemOrderInBoxBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(order: OrderInBox, onDelete: (OrderInBox) -> Unit) {
            binding.tvTrackingNumber.text = order.trackingNumber
            binding.tvWeight.text = "Вес: ${order.weight} кг"

            binding.btnDelete.setOnClickListener { onDelete(order) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrderInBoxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onDelete)
    }

    object DiffCallback : DiffUtil.ItemCallback<OrderInBox>() {
        override fun areItemsTheSame(oldItem: OrderInBox, newItem: OrderInBox): Boolean = oldItem.guid == newItem.guid
        override fun areContentsTheSame(oldItem: OrderInBox, newItem: OrderInBox): Boolean = oldItem == newItem
    }
}