package com.example.aquaroute_system.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.databinding.ItemFerryPreviewBinding

class FerryPreviewAdapter : RecyclerView.Adapter<FerryPreviewAdapter.FerryViewHolder>() {

    private var ferries: List<Ferry> = emptyList()

    fun submitList(list: List<Ferry>) {
        ferries = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FerryViewHolder {
        val binding = ItemFerryPreviewBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FerryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FerryViewHolder, position: Int) {
        holder.bind(ferries[position])
    }

    override fun getItemCount() = ferries.size

    class FerryViewHolder(private val binding: ItemFerryPreviewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ferry: Ferry) {
            binding.apply {
                tvFerryName.text = "🚢 ${ferry.name}"
                tvDestination.text = "🏢 ${ferry.getDestination()}"
                tvEta.text = "${ferry.eta} mins"

                // Calculate progress percentage
                val progress = calculateProgress(ferry)
                progressBar.progress = progress
                tvProgressPercent.text = "$progress%"
            }
        }

        private fun calculateProgress(ferry: Ferry): Int {
            // If we have pointA and pointB, compute based on distance traveled
            return if (ferry.pointA != null && ferry.pointB != null && ferry.pointA.size == 2 && ferry.pointB.size == 2) {
                val totalDistance = calculateDistance(
                    ferry.pointA[0], ferry.pointA[1],
                    ferry.pointB[0], ferry.pointB[1]
                )
                val traveledDistance = calculateDistance(
                    ferry.pointA[0], ferry.pointA[1],
                    ferry.lat, ferry.lon
                )
                if (totalDistance > 0) {
                    ((traveledDistance / totalDistance) * 100).toInt().coerceIn(0, 100)
                } else 0
            } else {
                // Fallback: estimate from ETA – assume max ETA of 120 minutes for demo
                // You can adjust this logic based on your actual data
                val maxEta = 120
                val progress = if (ferry.eta > 0) {
                    ((1 - ferry.eta.toFloat() / maxEta) * 100).toInt().coerceIn(0, 100)
                } else 0
                progress
            }
        }

        private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val R = 6371 // km
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return R * c
        }
    }
}