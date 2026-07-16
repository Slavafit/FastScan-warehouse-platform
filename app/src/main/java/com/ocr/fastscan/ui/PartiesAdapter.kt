package com.ocr.fastscan.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ocr.fastscan.R
import com.ocr.fastscan.data.model.Partial
import com.ocr.fastscan.databinding.ItemPartyBinding
import java.text.SimpleDateFormat
import java.util.Locale

class PartiesAdapter(
    private val onClick: (Partial) -> Unit,
    private val onLongClick: (Partial) -> Unit
) : ListAdapter<Partial, PartiesAdapter.PartyViewHolder>(PartyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartyViewHolder {
        val binding = ItemPartyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PartyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PartyViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PartyViewHolder(private val binding: ItemPartyBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(party: Partial) {
            val context = binding.root.context

            binding.tvPartialName.text = PartyNumberFormatter.display(party.partial_name)
            binding.tvPartialCode.text = context.getString(R.string.track_code, party.partial_code)
            binding.tvWarehouse.text = context.getString(R.string.country, party.warehouse_name)
            binding.tvBoxCount.text = context.getString(R.string.boxes_count, party.boxCount)
            binding.tvDate.text = context.getString(R.string.date, formatPartyDate(context, party.date_created))

            if (party.status != 0) {
                binding.tvStatus.text = context.getString(R.string.party_status_finished)
                binding.tvStatus.setBackgroundResource(R.drawable.bg_status_finished)
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_finished_text))
            } else {
                binding.tvStatus.text = context.getString(R.string.party_status_open)
                binding.tvStatus.setBackgroundResource(R.drawable.bg_status_open)
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_open_text))
            }

            binding.root.setOnClickListener {
                onClick(party)
            }

            binding.root.setOnLongClickListener {
                onLongClick(party)
                true
            }
        }
    }

    private fun formatPartyDate(context: Context, isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return context.getString(R.string.no_date)
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(isoDate) ?: return context.getString(R.string.no_date)
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            isoDate
        }
    }
}

class PartyDiffCallback : DiffUtil.ItemCallback<Partial>() {
    override fun areItemsTheSame(oldItem: Partial, newItem: Partial): Boolean =
        oldItem.guid == newItem.guid

    override fun areContentsTheSame(oldItem: Partial, newItem: Partial): Boolean =
        oldItem == newItem
}
