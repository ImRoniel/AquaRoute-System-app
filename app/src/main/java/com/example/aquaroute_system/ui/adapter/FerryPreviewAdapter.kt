package com.example.aquaroute_system.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.databinding.ItemFerryPreviewBinding

class FerryPreviewAdapter : RecyclerView.Adapter<FerryPreviewAdapter.FerryViewHolder>() {

    private var ferries: List<Ferry> = emptyList()

    fun submitList(list: List<Ferry>) {
        // We use notifyDataSetChanged here because we only have a few items (max 3-5)
        // and we WANT to force a re-bind every 30 seconds to refresh the progress bar
        // calculation even if the Firestore data hasn't changed.
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
                tvFerryName.text = "🚢 ${ferry.name.uppercase()}"
                tvDestination.text = "🏢 ${ferry.getDestination().uppercase()}"
                tvEta.text = "${ferry.eta} mins"

                // Calculate progress percentage locally (includes time-based estimation)
                val progress = calculateProgress(ferry)
                progressBar.progress = progress
                tvProgressPercent.text = "$progress%"
            }
        }

        private fun calculateProgress(ferry: Ferry): Int {
            // Priority 1: Use startTime and endTime if available (most accurate)
            if (ferry.startTime != null && ferry.endTime != null && ferry.endTime > ferry.startTime) {
                val currentTime = System.currentTimeMillis()
                val totalDuration = ferry.endTime - ferry.startTime
                val elapsed = currentTime - ferry.startTime
                if (totalDuration > 0) {
                    return ((elapsed.toFloat() / totalDuration) * 100).toInt().coerceIn(0, 100)
                }
            }

            // Priority 2: Use GPS coordinates if available
            if (ferry.pointA != null && ferry.pointB != null && ferry.pointA.size == 2 && ferry.pointB.size == 2) {
                val totalDistance = calculateDistance(
                    ferry.pointA[0], ferry.pointA[1],
                    ferry.pointB[0], ferry.pointB[1]
                )
                val traveledDistance = calculateDistance(
                    ferry.pointA[0], ferry.pointA[1],
                    ferry.lat, ferry.lon
                )
                if (totalDistance > 0.1) { // At least 100m
                    return ((traveledDistance / totalDistance) * 100).toInt().coerceIn(0, 100)
                }
            }

            // Priority 3: Fallback to status
            if (ferry.status.lowercase() == "arrived" || ferry.status.lowercase() == "docked") {
                return 100
            }

            // Priority 4: Fallback to ETA estimation
            // If ETA is very large (e.g. 800), it's probably just started or far away
            val maxEta = 120 // Assume 2 hours max journey for default progress scaling
            return if (ferry.eta > 0) {
                if (ferry.eta >= maxEta) 5 // If far away, show at least 5% progress if active
                else ((1 - ferry.eta.toFloat() / maxEta) * 100).toInt().coerceIn(0, 100)
            } else {
                0
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