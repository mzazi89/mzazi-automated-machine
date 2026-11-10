package com.mzazitech.automatedmachine.data.repository

import com.mzazitech.automatedmachine.data.remote.ApiErrorMapper
import com.mzazitech.automatedmachine.data.remote.LoginRequest
import com.mzazitech.automatedmachine.data.remote.MzaziApi
import com.mzazitech.automatedmachine.data.session.SessionManager
import com.mzazitech.automatedmachine.domain.model.AdminUser

/**
 * Admin authentication against the MZAZI backend.
 *
 * There is no offline password check and no hard-coded credential anywhere in the app:
 * passwords are verified server-side against a scrypt hash, and the app only keeps the
 * short-lived bearer token in the Android Keystore-backed store.
 */
class AuthRepository(
    private val api: MzaziApi,
    private val session: SessionManager,
    private val audit: AuditRepository
) {

    sealed interface LoginResult {
        data class Success(val admin: AdminUser) : LoginResult
        data class Failure(val message: String) : LoginResult
    }

    suspend fun login(email: String, password: String): LoginResult {
        val trimmedEmail = email.trim()
        if (!trimmedEmail.contains("@") || trimmedEmail.length < 5) {
            return LoginResult.Failure("Enter the administrator email address.")
        }
        if (password.length < 8) {
            return LoginResult.Failure("Password must be at least 8 characters.")
        }

        return runCatching {
            val response = api.login(LoginRequest(trimmedEmail, password))
            val admin = response.toAdmin()
            session.saveSession(response.accessToken, response.refreshToken, admin)
            audit.record(admin.email, "auth.login", admin.id, "Signed in from Android terminal")
            LoginResult.Success(admin)
        }.getOrElse { throwable ->
            LoginResult.Failure(ApiErrorMapper.from(throwable).message)
        }
    }

    suspend fun logout() {
        val email = session.session.value?.email
        session.logout()
        audit.record(email ?: "unknown", "auth.logout")
    }

    suspend fun refreshIdentity(): AdminUser? {
        if (!session.isLoggedIn()) return null
        return runCatching { api.me() }.getOrNull()?.let { dto ->
            AdminUser(
                id = dto.id,
                email = dto.email,
                name = dto.name ?: dto.email,
                role = dto.role ?: "operator"
            )
        }
    }

    fun currentAdmin(): AdminUser? = session.session.value
}
