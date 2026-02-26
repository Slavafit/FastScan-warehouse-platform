package com.ocr.fastscan.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ocr.fastscan.data.model.Partial
import com.ocr.fastscan.databinding.ItemPartyBinding

class PartiesAdapter(
    private val onClick: (Partial) -> Unit
) : ListAdapter<Partial, PartiesAdapter.PartyViewHolder>(PartyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartyViewHolder {
        val binding = ItemPartyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PartyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PartyViewHolder, position: Int) {
        val party = getItem(position)
        holder.bind(party)
    }

    inner class PartyViewHolder(private val binding: ItemPartyBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(party: Partial) {
            binding.tvPartialCode.text = party.partial_code
            binding.tvPartialName.text = party.partial_name
            binding.tvWarehouse.text = party.warehouse_name
            binding.tvBoxCount.text = "Коробок: ${party.boxCount}"
            binding.tvDate.text = party.date_created  // можно отформатировать

            binding.root.setOnClickListener {
                onClick(party)
            }
        }
    }
}

class PartyDiffCallback : DiffUtil.ItemCallback<Partial>() {
    override fun areItemsTheSame(oldItem: Partial, newItem: Partial): Boolean =
        oldItem.guid == newItem.guid

    override fun areContentsTheSame(oldItem: Partial, newItem: Partial): Boolean =
        oldItem == newItem
}