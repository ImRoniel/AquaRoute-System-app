package com.example.aquaroute_system.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.aquaroute_system.databinding.FragmentProfileBinding
import com.example.aquaroute_system.util.SessionManager

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        sessionManager = SessionManager(requireContext())
        setupUserProfile()
        return binding.root
    }

    private fun setupUserProfile() {
        val userName = sessionManager.getUserName() ?: "User"
        val userEmail = sessionManager.getUserEmail() ?: "No email provided"
        val userUid = sessionManager.getUserId() ?: "N/A"

        binding.profileName.text = userName
        binding.profileEmail.text = userEmail
        binding.profileUid.text = userUid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
