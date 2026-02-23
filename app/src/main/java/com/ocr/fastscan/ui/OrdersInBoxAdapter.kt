package com.ocr.fastscan.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ocr.fastscan.data.model.OrderDetails
import com.ocr.fastscan.databinding.ItemOrderInBoxBinding

class OrdersAdapter(
    private val onDelete: (OrderDetails) -> Unit
) : ListAdapter<OrderDetails, OrdersAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemOrderInBoxBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(order: OrderDetails, onDelete: (OrderDetails) -> Unit) {
            binding.tvTrackingNumber.text = order.trackingNumber ?: "Нет номера"
            binding.tvWeight.text = "Вес: %.2f кг".format(order.grossWeight ?: 0.0)

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

    object DiffCallback : DiffUtil.ItemCallback<OrderDetails>() {
        override fun areItemsTheSame(oldItem: OrderDetails, newItem: OrderDetails): Boolean {
            return oldItem.guid == newItem.guid  // сравниваем по уникальному guid
        }

        override fun areContentsTheSame(oldItem: OrderDetails, newItem: OrderDetails): Boolean {
            return oldItem == newItem  // полное сравнение
        }
    }
}