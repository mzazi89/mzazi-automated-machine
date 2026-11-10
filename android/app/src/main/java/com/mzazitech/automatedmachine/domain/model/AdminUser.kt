package com.mzazitech.automatedmachine.domain.model

data class AdminUser(
    val id: String,
    val email: String,
    val name: String,
    val role: String
) {
    val isSuperAdmin: Boolean get() = role.equals("super_admin", ignoreCase = true) ||
        role.equals("admin", ignoreCase = true)
}
