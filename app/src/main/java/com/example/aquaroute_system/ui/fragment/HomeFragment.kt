package com.example.aquaroute_system.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.aquaroute_system.databinding.FragmentHomeBinding
import com.example.aquaroute_system.ui.adapter.FerryPreviewAdapter
import com.example.aquaroute_system.ui.adapter.WeatherAdapter
import com.example.aquaroute_system.ui.viewmodel.UserDashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UserDashboardViewModel by activityViewModels()
    private lateinit var ferryAdapter: FerryPreviewAdapter
    private lateinit var weatherAdapter: WeatherAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerViews()
        setupObservers()
        setupSwipeRefresh()
    }

    private fun setupRecyclerViews() {
        ferryAdapter = FerryPreviewAdapter()
        binding.rvFerryPreview.adapter = ferryAdapter

        weatherAdapter = WeatherAdapter()
        binding.rvWeather.adapter = weatherAdapter
    }

    private fun setupObservers() {
        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            user?.let { updateUserInfo(it) }
        }

        viewModel.nearbyFerries.observe(viewLifecycleOwner) { ferries ->
            ferryAdapter.submitList(ferries)
        }

        viewModel.weatherConditions.observe(viewLifecycleOwner) { weatherList ->
            if (weatherList.isEmpty()) {
                binding.tvWeatherEmpty.visibility = View.VISIBLE
                binding.rvWeather.visibility = View.GONE
            } else {
                binding.tvWeatherEmpty.visibility = View.GONE
                binding.rvWeather.visibility = View.VISIBLE
                weatherAdapter.submitList(weatherList)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }
    }

    private fun updateUserInfo(user: com.example.aquaroute_system.data.models.User) {
        val currentTime = Calendar.getInstance()
        val hour = currentTime.get(Calendar.HOUR_OF_DAY)

        val greeting = when (hour) {
            in 0..11 -> "GOOD MORNING"
            in 12..16 -> "GOOD AFTERNOON"
            else -> "GOOD EVENING"
        }

        val displayName = user.displayName ?: "Captain"
        binding.tvWelcome.text = "$greeting, ${displayName.uppercase()}!"

        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        val memberSince = if (user.createdAt > 0) Date(user.createdAt) else Date()
        binding.tvMemberSince.text = "Member since: ${dateFormat.format(memberSince)}"

        val lastLogin = Date(user.lastLoginAt)
        binding.tvLastLogin.text = "Last login: Today ${timeFormat.format(lastLogin)}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
