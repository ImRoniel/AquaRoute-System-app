package com.example.aquaroute_system.View

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.aquaroute_system.R
import com.example.aquaroute_system.util.SessionManager

class SplashScreen : AppCompatActivity() {

    private val SPLASH_DELAY: Long = 2000 // 2 seconds
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        sessionManager = SessionManager(this)

        Handler(Looper.getMainLooper()).postDelayed({
            // Check if user is already logged in
            if (sessionManager.isSessionValid()) {
                // User has valid session - go to MainDashboard
                navigateToMainDashboard()
            } else {
                // Check if first time user
                val isFirstRun = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .getBoolean("is_first_run", true)

                if (isFirstRun) {
                    // First time user - show onboarding
                    navigateToOnboarding()
                } else {
                    // Returning user - go to login
                    navigateToLogin()
                }
            }
        }, SPLASH_DELAY)
    }

    private fun navigateToMainDashboard() {
        val intent = Intent(this, LiveMapView::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginSignupActivity::class.java)
        startActivity(intent)
        finish()
    }
}