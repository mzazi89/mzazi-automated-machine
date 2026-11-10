package com.mzazitech.automatedmachine.data.session

import com.mzazitech.automatedmachine.domain.model.AdminUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the authenticated admin session and the device credentials used to talk to
 * the MZAZI backend. Everything sensitive lives in [SecureStore].
 */
class SessionManager(private val store: SecureStore) {

    private val _session = MutableStateFlow(currentSession())
    val session: StateFlow<AdminUser?> = _session.asStateFlow()

    private val _deviceId = MutableStateFlow(store.get(KEY_DEVICE_ID))
    val deviceId: StateFlow<String?> = _deviceId.asStateFlow()

    fun accessToken(): String? = store.get(KEY_ACCESS_TOKEN)?.takeIf { it.isNotBlank() }

    fun deviceKey(): String? = store.get(KEY_DEVICE_KEY)?.takeIf { it.isNotBlank() }

    fun hasDeviceCredentials(): Boolean = !deviceId.value.isNullOrBlank() && deviceKey() != null

    fun isLoggedIn(): Boolean = accessToken() != null

    fun saveSession(accessToken: String, refreshToken: String?, admin: AdminUser) {
        store.put(KEY_ACCESS_TOKEN, accessToken)
        store.put(KEY_REFRESH_TOKEN, refreshToken)
        store.put(KEY_ADMIN_EMAIL, admin.email)
        store.put(KEY_ADMIN_NAME, admin.name)
        store.put(KEY_ADMIN_ROLE, admin.role)
        _session.value = admin
    }

    fun saveDeviceCredentials(deviceId: String, deviceKey: String) {
        store.put(KEY_DEVICE_ID, deviceId)
        store.put(KEY_DEVICE_KEY, deviceKey)
        _deviceId.value = deviceId
    }

    fun clearDeviceCredentials() {
        store.put(KEY_DEVICE_ID, null)
        store.put(KEY_DEVICE_KEY, null)
        _deviceId.value = null
    }

    fun logout() {
        store.put(KEY_ACCESS_TOKEN, null)
        store.put(KEY_REFRESH_TOKEN, null)
        store.put(KEY_ADMIN_EMAIL, null)
        store.put(KEY_ADMIN_NAME, null)
        store.put(KEY_ADMIN_ROLE, null)
        _session.value = null
    }

    private fun currentSession(): AdminUser? {
        val email = store.get(KEY_ADMIN_EMAIL) ?: return null
        if (accessToken() == null) return null
        return AdminUser(
            id = email,
            email = email,
            name = store.get(KEY_ADMIN_NAME) ?: email,
            role = store.get(KEY_ADMIN_ROLE) ?: "operator"
        )
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ADMIN_EMAIL = "admin_email"
        const val KEY_ADMIN_NAME = "admin_name"
        const val KEY_ADMIN_ROLE = "admin_role"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_KEY = "device_key"
    }
}
