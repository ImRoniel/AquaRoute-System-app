package com.example.aquaroute_system.View

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.aquaroute_system.R
import com.example.aquaroute_system.View.OnboardingActivity
import com.example.aquaroute_system.View.LoginSignupActivity

class SplashScreen : AppCompatActivity() {

    private val SPLASH_DELAY: Long = 2000 // 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Hide the action bar for full-screen splash
        supportActionBar?.hide()

        Handler(Looper.getMainLooper()).postDelayed({
            // Check if the user has already seen onboarding
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val isFirstRun = prefs.getBoolean("is_first_run", true)

            // Decide where to navigate
            val intent = if (isFirstRun) {
                // First-time user: show onboarding
                prefs.edit().putBoolean("is_first_run", false).apply() // mark as seen
                Intent(this, OnboardingActivity::class.java)
            } else {
                // Returning user: go to login/signup
                Intent(this, LoginSignupActivity::class.java)
            }

            startActivity(intent)
            finish()
        }, SPLASH_DELAY)
    }
}