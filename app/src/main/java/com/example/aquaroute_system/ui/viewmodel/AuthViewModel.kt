//C:\Users\Roniel Cuaresma\AndroidStudioProjects\AquaRouteSystem\app\src\main\java\com\example\aquaroute_system\ui\viewmodel\AuthViewModel.kt
package com.example.aquaroute_system.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aquaroute_system.data.models.User
import com.example.aquaroute_system.data.models.UserPreferences
import com.example.aquaroute_system.data.repository.AuthRepository
import kotlinx.coroutines.launch

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    val isLoggedIn: Boolean
        get() = authRepository.isUserLoggedIn

    init {
        // Check if user is already logged in
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        viewModelScope.launch {
            if (authRepository.isUserLoggedIn) {
                _user.value = authRepository.getCurrentUser()
                _authState.value = AuthState.Authenticated
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun signUp(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _authState.value = AuthState.Loading

            val result = authRepository.signUpWithEmail(email, password, displayName)

            result.onSuccess { user ->
                _user.value = user
                _authState.value = AuthState.Authenticated
            }.onFailure { exception ->
                _errorMessage.value = exception.message
                _authState.value = AuthState.Error
            }

            _isLoading.value = false
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _authState.value = AuthState.Loading

            val result = authRepository.loginWithEmail(email, password)

            result.onSuccess { user ->
                _user.value = user
                _authState.value = AuthState.Authenticated
            }.onFailure { exception ->
                _errorMessage.value = exception.message
                _authState.value = AuthState.Error
            }

            _isLoading.value = false
        }
    }

    fun logout() {
        authRepository.logout()
        _user.value = null
        _authState.value = AuthState.Unauthenticated
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = authRepository.sendPasswordResetEmail(email)

            result.onFailure { exception ->
                _errorMessage.value = exception.message
            }

            _isLoading.value = false
        }
    }

    fun updateUserPreferences(preferences: UserPreferences) {
        viewModelScope.launch {
            _isLoading.value = true

            val result = authRepository.updateUserPreferences(preferences)

            result.onSuccess {
                // Refresh user data
                _user.value = authRepository.getCurrentUser()
            }.onFailure { exception ->
                _errorMessage.value = exception.message
            }

            _isLoading.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

sealed class AuthState {
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    object Error : AuthState()
}