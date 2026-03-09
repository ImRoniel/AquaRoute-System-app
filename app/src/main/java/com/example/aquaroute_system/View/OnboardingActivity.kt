package com.example.aquaroute_system.View

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.aquaroute_system.R
import com.example.aquaroute_system.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var viewPagerAdapter: OnboardingViewPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupOnboardingItems()
        setupViewPager()
        setupIndicators()
        setupClickListeners()
    }

    private fun setupOnboardingItems() {
        val onboardingItems = listOf(
            OnboardingItem(
                title = getString(R.string.onboarding_title_1),
                description = getString(R.string.onboarding_desc_1),
                imageRes = R.drawable.map_3d
            ),
            OnboardingItem(
                title = getString(R.string.onboarding_title_2),
                description = getString(R.string.onboarding_desc_2),
                imageRes = R.drawable.cargo_3d
            ),
            OnboardingItem(
                title = getString(R.string.onboarding_title_3),
                description = getString(R.string.onboarding_desc_3),
                imageRes = R.drawable.weather_3d
            )
        )

        viewPagerAdapter = OnboardingViewPagerAdapter(onboardingItems)
    }

    private fun setupViewPager() {
        binding.viewPager.apply {
            adapter = viewPagerAdapter
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updateIndicators(position)
                    updateButtons(position)
                }
            })
        }
    }

    private fun setupIndicators() {
        val indicators = listOf(
            binding.indicator0,
            binding.indicator1,
            binding.indicator2
        )

        indicators.forEachIndexed { index, imageView ->
            imageView.setImageResource(
                if (index == 0) R.drawable.page_indicator_dot_active
                else R.drawable.page_indicator_dot
            )
        }
    }

    private fun updateIndicators(position: Int) {
        val indicators = listOf(
            binding.indicator0,
            binding.indicator1,
            binding.indicator2
        )

        indicators.forEachIndexed { index, imageView ->
            imageView.setImageResource(
                if (index == position) R.drawable.page_indicator_dot_active
                else R.drawable.page_indicator_dot
            )
        }
    }

    private fun updateButtons(position: Int) {
        binding.btnNext.text = if (position == 2) {
            getString(R.string.get_started)
        } else {
            getString(R.string.next)
        }
    }

    private fun setupClickListeners() {
        binding.btnSkip.setOnClickListener {
            navigateToAuth()
        }

        binding.btnNext.setOnClickListener {
            if (binding.viewPager.currentItem == 2) {
                navigateToAuth()
            } else {
                binding.viewPager.currentItem = binding.viewPager.currentItem + 1
            }
        }
    }

    private fun navigateToAuth() {
        // Mark that user has seen onboarding
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
            .putBoolean("is_first_run", false)
            .apply()

        val intent = Intent(this, LoginSignupActivity::class.java)
        startActivity(intent)
        finish()
    }
}