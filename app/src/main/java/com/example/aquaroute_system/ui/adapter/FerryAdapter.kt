package com.example.aquaroute_system.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.databinding.ItemFerryBinding

class FerryAdapter(private val onTrackClick: (Ferry) -> Unit) :
    ListAdapter<Ferry, FerryAdapter.FerryViewHolder>(FerryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FerryViewHolder {
        val binding = ItemFerryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FerryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FerryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FerryViewHolder(private val binding: ItemFerryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ferry: Ferry) {
            binding.tvFerryName.text = ferry.name
            binding.tvFerryRoute.text = ferry.route.ifEmpty { "No specific route" }
            binding.tvFerryStatus.text = ferry.status.uppercase()
            binding.tvFerrySpeed.text = "${ferry.speed_knots} knots"
            binding.tvFerryEta.text = "${ferry.eta} mins"

            // Status Color Coding
            val statusColor = when (ferry.status.lowercase()) {
                "active", "in_transit" -> binding.root.context.getColor(R.color.success_green)
                "delayed" -> binding.root.context.getColor(R.color.error_red)
                else -> binding.root.context.getColor(R.color.medium_text)
            }
            binding.tvFerryStatus.setTextColor(statusColor)

            binding.btnTrackFerry.setOnClickListener {
                onTrackClick(ferry)
            }
        }
    }

    class FerryDiffCallback : DiffUtil.ItemCallback<Ferry>() {
        override fun areItemsTheSame(oldItem: Ferry, newItem: Ferry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Ferry, newItem: Ferry): Boolean {
            return oldItem == newItem
        }
    }
}
