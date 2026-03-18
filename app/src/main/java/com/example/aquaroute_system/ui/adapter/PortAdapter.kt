package com.example.aquaroute_system.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.models.FirestorePort
import com.example.aquaroute_system.databinding.ItemPortBinding
import java.util.*

class PortAdapter(
    private var currentHour: Int,
    private val vesselCounts: () -> Map<String, Int>,
    private val onPortClick: (FirestorePort) -> Unit
) : ListAdapter<FirestorePort, PortAdapter.PortViewHolder>(PortDiffCallback()) {

    fun updateCurrentHour(hour: Int) {
        currentHour = hour
        notifyDataSetChanged()
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
        val binding = ItemPortBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PortViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PortViewHolder(private val binding: ItemPortBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(port: FirestorePort) {
            binding.tvPortName.text = port.name
            
            // Proximity (Placeholder for distance calculation - usually passed in or calculated by VM)
            // For now, we'll just show the type if distance isn't readily available in the model
            binding.tvDistance.text = port.type.replaceFirstChar { it.uppercase() }

            // Dynamic Status
            val statusText = port.getCurrentStatus(currentHour)
            binding.statusChip.text = statusText.uppercase(Locale.getDefault())
            
            val (bgColor, textColor) = when {
                statusText.contains("Open", ignoreCase = true) -> 
                    R.drawable.bg_status_pill_open to R.color.success_green
                statusText.contains("Closing", ignoreCase = true) -> 
                    R.drawable.bg_status_pill_warning to R.color.warning_orange
                else -> 
                    R.drawable.bg_status_pill_closed to R.color.error_red
            }
            
            // Safeguard if drawables don't exist under these specific names yet
            try {
                binding.statusChip.setBackgroundResource(bgColor)
                binding.statusChip.setTextColor(ContextCompat.getColor(binding.root.context, textColor))
            } catch (e: Exception) {}

            // Vessel Activity
            val activeVessels = vesselCounts()[port.id] ?: 0
            binding.tvActiveVessels.text = "$activeVessels Active"
            binding.vesselActivityLayout.alpha = if (activeVessels > 0) 1.0f else 0.5f

            // Port Icon
            val iconRes = when (port.type.lowercase()) {
                "terminal" -> R.drawable.ic_port_terminal
                "pier" -> R.drawable.ic_port_pier
                "boatyard" -> R.drawable.ic_port_boatyard
                else -> R.drawable.ic_port_marker
            }
            binding.ivPortType.setImageResource(iconRes)

            binding.root.setOnClickListener { onPortClick(port) }
        }
    }

    class PortDiffCallback : DiffUtil.ItemCallback<FirestorePort>() {
        override fun areItemsTheSame(oldItem: FirestorePort, newItem: FirestorePort): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: FirestorePort, newItem: FirestorePort): Boolean = oldItem == newItem
    }
}
