package com.example.aquaroute_system.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.models.WeatherCondition
import com.example.aquaroute_system.databinding.ItemWeatherAdvisoryBinding

class WeatherAdvisoryAdapter : ListAdapter<WeatherCondition, WeatherAdvisoryAdapter.ViewHolder>(WeatherDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWeatherAdvisoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemWeatherAdvisoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(weather: WeatherCondition) {
            binding.apply {
                tvLocation.text = weather.location.uppercase()
                tvIconCondition.text = "${weather.icon} ${weather.condition.uppercase()}"
                tvTemp.text = "🌡️ Temp: ${weather.temperature}°C"
                tvWind.text = "💨 Wind: ${weather.windSpeed} ${weather.windDirection}"
                tvWaves.text = "🌊 Waves: ${weather.waves}"
                tvHumidity.text = "💧 Humidity: ${weather.humidity}%"

                if (weather.hasAdvisory && !weather.advisoryMessage.isNullOrBlank()) {
                    tvAdvisoryMessage.text = "⚠️ ${weather.advisoryMessage}"
                    tvAdvisoryMessage.visibility = android.view.View.VISIBLE
                    // Set error red border equivalent if available, or just highlight with tint
                    weatherCard.strokeWidth = 4
                    weatherCard.setStrokeColor(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(root.context, R.color.error_red)))
                } else {
                    tvAdvisoryMessage.visibility = android.view.View.GONE
                    weatherCard.strokeWidth = 0
                }
            }
        }
    }

    class WeatherDiffCallback : DiffUtil.ItemCallback<WeatherCondition>() {
        override fun areItemsTheSame(oldItem: WeatherCondition, newItem: WeatherCondition): Boolean {
            return oldItem.locationId == newItem.locationId
        }

        override fun areContentsTheSame(oldItem: WeatherCondition, newItem: WeatherCondition): Boolean {
            return oldItem == newItem
        }
    }
}
