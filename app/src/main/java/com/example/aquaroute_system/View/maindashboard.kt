package com.example.aquaroute_system.View

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.repository.*
import com.example.aquaroute_system.databinding.ActivityMainDashboardBinding
import com.example.aquaroute_system.ui.viewmodel.UserDashboardViewModel
import com.example.aquaroute_system.ui.viewmodel.UserDashboardViewModelFactory
import com.example.aquaroute_system.util.SessionManager
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class MainDashboard : AppCompatActivity() {

    private lateinit var binding: ActivityMainDashboardBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: UserDashboardViewModel
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawerLayout = binding.drawerLayout

        initializeDependencies()
        checkUserSession()
        setupDrawerHeader()
        setupNavigation()
        requestUserLocation()
        setupClickListeners()
    }

    private fun initializeDependencies() {
        sessionManager = SessionManager(this)
        val firestore = FirebaseFirestore.getInstance()

        val dashboardRepository = DashboardRepository(firestore)
        val portStatusRepository = PortStatusRepository(firestore)
        val weatherRepository = WeatherRepository(firestore)
        val cargoRepository = CargoRepository(firestore)
        val ferryRepository = FerryRepository(firestore)
        val portRepository = PortRepository()
        val backend_base_url = "https://aquaroute-system-web.onrender.com/"
        val weatherRefreshRepository = WeatherRefreshRepository(backend_base_url)

        val factory = UserDashboardViewModelFactory(
            sessionManager,
            dashboardRepository,
            portStatusRepository,
            weatherRepository,
            cargoRepository,
            ferryRepository,
            portRepository,
            weatherRefreshRepository
        )
        viewModel = ViewModelProvider(this, factory).get(UserDashboardViewModel::class.java)
    }

    private fun checkUserSession() {
        if (sessionManager.getUserId() == null && sessionManager.getUserData() == null) {
            if (!sessionManager.isSessionValid()) {
                Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
                navigateToLogin()
            }
        }
    }

    private fun setupDrawerHeader() {
        val userName = sessionManager.getUserName() ?: "Guest"
        val userEmail = sessionManager.getUserEmail() ?: ""
        
        binding.drawerUserName.text = userName
        binding.drawerUserEmail.text = userEmail
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Wire up the BottomNavigationView with NavController
        binding.bottomNav.setupWithNavController(navController)

        // Override the item-selected listener to add a scale animation
        // while still delegating navigation to NavigationUI
        binding.bottomNav.setOnItemSelectedListener { item ->
            val view = binding.bottomNav.findViewById<View>(item.itemId)
            view?.animate()?.scaleX(0.9f)?.scaleY(0.9f)?.setDuration(100)?.withEndAction {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }?.start()

            NavigationUI.onNavDestinationSelected(item, navController)
        }

        // When the user taps the already-selected tab, pop back to its root
        binding.bottomNav.setOnItemReselectedListener { item ->
            navController.popBackStack(item.itemId, inclusive = false)
        }
    }

    /**
     * Public helper for fragments to switch tabs programmatically.
     * Sets selectedItemId on the BottomNav, which triggers the
     * onItemSelectedListener and keeps everything in sync.
     */
    fun navigateToTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    private fun requestUserLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            viewModel.setLocationPermissionGranted(true)
            getLastKnownLocation()
        } else {
            viewModel.setLocationPermissionGranted(false)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun getLastKnownLocation() {
        try {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                viewModel.setUserLocation(location)
            } else {
                val defaultLocation = Location("").apply {
                    latitude = 14.5995
                    longitude = 120.9842
                }
                viewModel.setUserLocation(defaultLocation)
            }
        } catch (e: Exception) {
            Log.e("MainDashboard", "Error getting location", e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty()) {
            val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
            viewModel.setLocationPermissionGranted(granted)
            if (granted) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    getLastKnownLocation()
                }
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        // Toolbar hamburger → open drawer
        binding.toolbarLayout.btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // ── QUICK ACCESS ──────────────────────────────────────────
        binding.drawerWeather.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, WeatherAdvisoriesActivity::class.java))
        }

        binding.drawerPorts.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            navigateToTab(R.id.nav_ports)
        }

        binding.drawerVoyageHistory.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, VoyageHistoryActivity::class.java))
        }

        // ── ACCOUNT ───────────────────────────────────────────────
        binding.drawerProfile.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            // TODO: navigate to ProfileFragment or ProfileActivity when created
            Toast.makeText(this, "My Profile — coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.drawerSettings.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            // TODO: navigate to SettingsActivity when created
            Toast.makeText(this, "Settings — coming soon!", Toast.LENGTH_SHORT).show()
        }

        // ── FOOTER ────────────────────────────────────────────────
        binding.drawerLogout.setOnClickListener {
            logout()
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