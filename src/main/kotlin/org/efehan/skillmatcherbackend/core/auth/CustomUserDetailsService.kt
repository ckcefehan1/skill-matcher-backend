package org.efehan.skillmatcherbackend.core.auth

import org.efehan.skillmatcherbackend.core.user.UserService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userService: UserService,
) : UserDetailsService {
    override fun loadUserByUsername(email: String): UserDetails {
        val user =
            userService.findByEmail(email)
                ?: throw UsernameNotFoundException("User not found")
        return SecurityUser(user)
    }

    fun loadUserById(id: String): SecurityUser {
        val user =
            userService.findById(id)
                ?: throw UsernameNotFoundException("User not found")
        return SecurityUser(user)
    }
}
