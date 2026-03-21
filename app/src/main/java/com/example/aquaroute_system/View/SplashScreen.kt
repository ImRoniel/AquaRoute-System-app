package com.example.aquaroute_system.View

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.aquaroute_system.util.SessionManager

/**
 * SplashScreen — Instant session router. NO UI layout.
 *
 * The visual splash experience is provided entirely by the Android system:
 *  • API 31+  → native SplashScreen API (windowSplashScreenBackground + animated icon in values-v31/themes.xml)
 *  • API < 31 → Theme.Splash windowBackground (auth_background navy gradient)
 *
 * This activity never calls setContentView(), so the system splash is dismissed
 * the moment this Activity's onCreate completes — making the transition instant.
 *
 * Routing logic:
 *  - Valid 30-day session → MainDashboard (LiveMapView), bypassing onboarding + login
 *  - First run            → OnboardingActivity
 *  - Returning user       → LoginActivity
 */
class SplashScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No setContentView — the themed window background handles the visual splash

        val sessionManager = SessionManager(this)

        if (sessionManager.isSessionValid()) {
            startActivity(
                Intent(this, LiveMapView::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        } else {
            val isFirstRun = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("is_first_run", true)

            if (isFirstRun) {
                startActivity(Intent(this, OnboardingActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }

        finish()
    }
}