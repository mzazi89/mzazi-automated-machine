package com.mzazitech.automatedmachine.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mzazitech.automatedmachine.AppContainer
import com.mzazitech.automatedmachine.data.repository.AuthRepository
import com.mzazitech.automatedmachine.domain.model.AdminUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val admin: AdminUser? = null
)

class AuthViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState(admin = container.session.session.value))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.value = _state.value.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun login() {
        val current = _state.value
        if (current.loading) return
        _state.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = container.auth.login(current.email, current.password)) {
                is AuthRepository.LoginResult.Success ->
                    _state.value = _state.value.copy(
                        loading = false,
                        password = "",
                        admin = result.admin,
                        error = null
                    )

                is AuthRepository.LoginResult.Failure ->
                    _state.value = _state.value.copy(loading = false, error = result.message)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            container.auth.logout()
            _state.value = AuthUiState()
        }
    }
}
