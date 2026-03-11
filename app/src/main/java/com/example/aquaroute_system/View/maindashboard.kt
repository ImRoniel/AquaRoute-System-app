package com.example.aquaroute_system.View

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.aquaroute_system.data.repository.*
import com.example.aquaroute_system.databinding.ActivityMainDashboardBinding
import com.example.aquaroute_system.ui.viewmodel.UserDashboardViewModel
import com.example.aquaroute_system.ui.viewmodel.UserDashboardViewModelFactory
import com.example.aquaroute_system.util.SessionManager
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MainDashboard : AppCompatActivity() {

    private lateinit var binding: ActivityMainDashboardBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: UserDashboardViewModel
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawerLayout = binding.drawerLayout
        swipeRefreshLayout = binding.swipeRefreshLayout

        initializeDependencies()
        checkUserSession()
        setupObservers()
        setupUserInfo()
        setupClickListeners()
    }

    private fun initializeDependencies() {
        sessionManager = SessionManager(this)
        val firestore = FirebaseFirestore.getInstance()

        val dashboardRepository = DashboardRepository(firestore)
        val portStatusRepository = PortStatusRepository(firestore)
        val weatherRepository = WeatherRepository(firestore)
        val cargoRepository = CargoRepository(firestore)

        val factory = UserDashboardViewModelFactory(
            sessionManager,
            dashboardRepository,
            portStatusRepository,
            weatherRepository,
            cargoRepository
        )
        viewModel = ViewModelProvider(this, factory).get(UserDashboardViewModel::class.java)
    }

    private fun checkUserSession() {
        // Add debug logging
        Log.d("MainDashboard", "=== SESSION CHECK ===")
        Log.d("MainDashboard", "isLoggedIn: ${sessionManager.isLoggedIn()}")
        Log.d("MainDashboard", "isSessionValid: ${sessionManager.isSessionValid()}")
        Log.d("MainDashboard", "UserId: ${sessionManager.getUserId()}")
        Log.d("MainDashboard", "UserEmail: ${sessionManager.getUserEmail()}")
        Log.d("MainDashboard", "UserData: ${sessionManager.getUserData()}")

        val loginTime = sessionManager.getLoginTime()
        Log.d("MainDashboard", "LoginTime: $loginTime")
        Log.d("MainDashboard", "CurrentTime: ${System.currentTimeMillis()}")
        Log.d("MainDashboard", "Time diff: ${System.currentTimeMillis() - (loginTime ?: 0)}")

        // TEMPORARY FIX: Allow access if we have ANY user data
        if (sessionManager.getUserId() != null || sessionManager.getUserData() != null) {
            Log.d("MainDashboard", "User has data, allowing access temporarily")
            return
        }

        if (!sessionManager.isSessionValid()) {
            Log.d("MainDashboard", "Session invalid, navigating to login")
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
            navigateToLogin()
        } else {
            Log.d("MainDashboard", "Session valid, continuing")
        }
    }

    private fun setupObservers() {
        viewModel.currentUser.observe(this) { user ->
            if (user != null) {
                updateUserInterface(user)
            }
        }

        viewModel.dashboardStats.observe(this) { stats ->
            if (stats != null) {
                updateStatistics(stats)
            }
        }

        viewModel.portStatuses.observe(this) { ports ->
            updatePortStatuses(ports)
        }

        viewModel.weatherConditions.observe(this) { weather ->
            updateWeatherConditions(weather)
        }

        viewModel.activeCargo.observe(this) { cargoList ->
            updateCargoItems(cargoList)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            swipeRefreshLayout.isRefreshing = isLoading
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun setupUserInfo() {
        val user = sessionManager.getUserData()
        val currentTime = Calendar.getInstance()
        val hour = currentTime.get(Calendar.HOUR_OF_DAY)

        val greeting = when (hour) {
            in 0..11 -> "GOOD MORNING"
            in 12..16 -> "GOOD AFTERNOON"
            else -> "GOOD EVENING"
        }

        if (user != null) {
            val displayName = user.displayName ?: "Captain"
            binding.tvWelcome.text = "$greeting, ${displayName.uppercase()}!"
            binding.tvMemberSince.text = "Member since: ${formatDate(user.createdAt)}"
            binding.tvLastLogin.text = "Last login: Today ${formatDateTime(user.lastLoginAt)}"

            binding.drawerUserName.text = displayName.uppercase()
            binding.drawerUserEmail.text = user.email
            binding.drawerLogout.visibility = android.view.View.VISIBLE
        } else {
            binding.tvWelcome.text = "$greeting, GUEST!"
            binding.tvMemberSince.text = "You are browsing as guest"
            binding.tvLastLogin.text = "Sign up to save your data"

            binding.drawerUserName.text = "GUEST USER"
            binding.drawerUserEmail.text = "guest@aquaroute.com"
            binding.drawerLogout.visibility = android.view.View.GONE
        }
    }

    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return format.format(date)
    }

    private fun formatDateTime(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
        return format.format(date)
    }

    private fun updateUserInterface(user: com.example.aquaroute_system.data.models.User) {
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

        binding.drawerUserName.text = displayName.uppercase()
        binding.drawerUserEmail.text = user.email
    }

    private fun updateStatistics(stats: com.example.aquaroute_system.data.models.DashboardStats) {
        binding.tvTotalShipments.text = stats.totalShipments.toString()
        binding.tvInTransit.text = stats.inTransit.toString()
        binding.tvDelivered.text = stats.delivered.toString()
        binding.tvDelayed.text = stats.delayed.toString()
        binding.tvActiveVessels.text = "${stats.activeVessels} vessels active"
    }

    private fun updatePortStatuses(ports: List<com.example.aquaroute_system.data.models.PortStatus>) {
        ports.forEach { port ->
            when {
                port.portName.contains("Manila", ignoreCase = true) -> {
                    binding.tvPortManila.text = port.getDisplayText()
                    binding.tvPortManila.setTextColor(getStatusColor(port.status))
                }
                port.portName.contains("Batangas", ignoreCase = true) -> {
                    binding.tvPortBatangas.text = port.getDisplayText()
                    binding.tvPortBatangas.setTextColor(getStatusColor(port.status))
                }
                port.portName.contains("Cebu", ignoreCase = true) -> {
                    binding.tvPortCebu.text = port.getDisplayText()
                    binding.tvPortCebu.setTextColor(getStatusColor(port.status))
                }
                port.portName.contains("Davao", ignoreCase = true) -> {
                    binding.tvPortDavao.text = port.getDisplayText()
                    binding.tvPortDavao.setTextColor(getStatusColor(port.status))
                }
            }
        }
    }

    private fun updateWeatherConditions(weatherList: List<com.example.aquaroute_system.data.models.WeatherCondition>) {
        weatherList.forEach { weather ->
            when {
                weather.location.contains("Manila", ignoreCase = true) -> {
                    binding.tvWeatherManila.text = weather.displayText
                }
                weather.location.contains("Batangas", ignoreCase = true) -> {
                    binding.tvWeatherBatangas.text = weather.displayText
                }
                weather.location.contains("Cebu", ignoreCase = true) -> {
                    binding.tvWeatherCebu.text = weather.displayText
                    if (weather.hasAdvisory) {
                        binding.tvWeatherCebu.setTextColor(getColor(com.example.aquaroute_system.R.color.error_red))
                    }
                }
            }
        }
    }

    private fun updateCargoItems(cargoList: List<com.example.aquaroute_system.data.models.Cargo>) {
        binding.btnViewAllCargo.text = "[VIEW ALL (${cargoList.size})]"

        if (cargoList.isNotEmpty()) {
            val cargo1 = cargoList[0]
            binding.cargoItem1.visibility = android.view.View.VISIBLE
            binding.tvCargoTitle1.text = "${cargo1.getDisplayReference()} │ ${cargo1.origin} → ${cargo1.destination}"
            binding.tvCargoStatus1.text = "Status: ${getStatusEmoji(cargo1.status)} ${cargo1.status.replace("_", " ").uppercase()}"
            binding.tvCargoDetails1.text = "Cargo: ${cargo1.cargoType} (${cargo1.weight} kg)"

            binding.btnTrackCargo1.tag = cargo1.id
            binding.btnDetailsCargo1.tag = cargo1.id
        }

        if (cargoList.size > 1) {
            val cargo2 = cargoList[1]
            binding.cargoItem2.visibility = android.view.View.VISIBLE
            binding.tvCargoTitle2.text = "${cargo2.getDisplayReference()} │ ${cargo2.origin} → ${cargo2.destination}"
            binding.tvCargoStatus2.text = "Status: ${getStatusEmoji(cargo2.status)} ${cargo2.status.replace("_", " ").uppercase()}"
            binding.tvCargoDetails2.text = "Cargo: ${cargo2.cargoType} (${cargo2.weight} kg)"

            binding.btnEditCargo2.tag = cargo2.id
            binding.btnDetailsCargo2.tag = cargo2.id
        }

        binding.tvNoCargo.visibility = if (cargoList.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun getStatusEmoji(status: String): String {
        return when (status) {
            "in_transit" -> "🟢"
            "processing" -> "🟡"
            "delivered" -> "✅"
            "delayed" -> "🔴"
            else -> "⚪"
        }
    }

    private fun getStatusColor(status: String): Int {
        return when (status.lowercase()) {
            "open" -> getColor(com.example.aquaroute_system.R.color.success_green)
            "limited" -> getColor(com.example.aquaroute_system.R.color.warning_orange)
            "closed" -> getColor(com.example.aquaroute_system.R.color.error_red)
            else -> getColor(com.example.aquaroute_system.R.color.medium_text)
        }
    }

    private fun setupClickListeners() {
        binding.btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.btnLiveMap.setOnClickListener {
            startActivity(Intent(this, LiveMapView::class.java))
        }

        binding.btnMyCargo.setOnClickListener {
            startActivity(Intent(this, CargoTrackerActivity::class.java))
        }

        binding.btnOpenMap.setOnClickListener {
            startActivity(Intent(this, LiveMapView::class.java))
        }

        binding.btnViewAllCargo.setOnClickListener {
            startActivity(Intent(this, CargoTrackerActivity::class.java))
        }

        binding.btnViewAllPorts.setOnClickListener {
            Toast.makeText(this, "All ports coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnQuickTrack.setOnClickListener {
            startActivity(Intent(this, CargoTrackerActivity::class.java))
        }

        binding.btnTrackCargo1.setOnClickListener {
            val cargoId = binding.btnTrackCargo1.tag as? String
            if (cargoId != null) {
                val intent = Intent(this, CargoTrackerActivity::class.java)
                intent.putExtra("CARGO_ID", cargoId)
                startActivity(intent)
            }
        }

        binding.btnDetailsCargo1.setOnClickListener {
            val cargoId = binding.btnDetailsCargo1.tag as? String
            if (cargoId != null) {
                val intent = Intent(this, CargoDetailsActivity::class.java)
                intent.putExtra("CARGO_ID", cargoId)
                startActivity(intent)
            }
        }

        binding.drawerDashboard.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        binding.drawerLiveMap.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, LiveMapView::class.java))
        }

        binding.drawerMyCargo.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, CargoTrackerActivity::class.java))
        }

        binding.drawerVoyageHistory.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, VoyageHistoryActivity::class.java))
        }

        binding.drawerLogout.setOnClickListener {
            logout()
        }

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }
    }

    private fun logout() {
        sessionManager.clearSession()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginSignupActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}