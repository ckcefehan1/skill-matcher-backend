package org.efehan.skillmatcherbackend.core.auth

import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {
    override fun loadUserByUsername(email: String): UserDetails {
        val user =
            userRepository.findByEmail(email)
                ?: throw UsernameNotFoundException("User not found")
        return SecurityUser(user)
    }

    fun loadUserById(id: String): SecurityUser {
        val user =
            userRepository
                .findById(id)
                .orElseThrow { UsernameNotFoundException("User not found") }
        return SecurityUser(user)
    }
}
