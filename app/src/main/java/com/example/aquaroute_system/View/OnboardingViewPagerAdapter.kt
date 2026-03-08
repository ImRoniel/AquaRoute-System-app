package com.example.aquaroute_system.View

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aquaroute_system.databinding.OnboardingSlideMapBinding
import com.example.aquaroute_system.databinding.OnboardingSlideCargoBinding
import com.example.aquaroute_system.databinding.OnboardingSlideWeatherBinding

class OnboardingViewPagerAdapter(
    private val items: List<OnboardingItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_MAP = 0
        private const val TYPE_CARGO = 1
        private const val TYPE_WEATHER = 2
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            TYPE_MAP -> {
                val binding = OnboardingSlideMapBinding.inflate(layoutInflater, parent, false)
                MapViewHolder(binding)
            }
            TYPE_CARGO -> {
                val binding = OnboardingSlideCargoBinding.inflate(layoutInflater, parent, false)
                CargoViewHolder(binding)
            }
            TYPE_WEATHER -> {
                val binding = OnboardingSlideWeatherBinding.inflate(layoutInflater, parent, false)
                WeatherViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        when (holder) {
            is MapViewHolder -> holder.bind(item)
            is CargoViewHolder -> holder.bind(item)
            is WeatherViewHolder -> holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class MapViewHolder(private val binding: OnboardingSlideMapBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OnboardingItem) {
            binding.imgIllustration.setImageResource(item.imageRes)
            binding.tvOnboardingTitle.text = item.title
            binding.tvOnboardingDescription.text = item.description
        }
    }

    inner class CargoViewHolder(private val binding: OnboardingSlideCargoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OnboardingItem) {
            binding.imgIllustration.setImageResource(item.imageRes)
            binding.tvOnboardingTitle.text = item.title
            binding.tvOnboardingDescription.text = item.description
        }
    }

    inner class WeatherViewHolder(private val binding: OnboardingSlideWeatherBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OnboardingItem) {
            binding.imgIllustration.setImageResource(item.imageRes)
            binding.tvOnboardingTitle.text = item.title
            binding.tvOnboardingDescription.text = item.description
        }
    }
}