package org.efehan.skillmatcherbackend.core.auth

import org.efehan.skillmatcherbackend.persistence.UserModel
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class SecurityUser(
    val user: UserModel,
) : UserDetails {
    val companyId: String
        get() = user.companyId

    override fun getAuthorities() = listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))

    override fun getPassword() = user.passwordHash ?: ""

    override fun getUsername() = user.email

    override fun isEnabled() = user.isEnabled
}
