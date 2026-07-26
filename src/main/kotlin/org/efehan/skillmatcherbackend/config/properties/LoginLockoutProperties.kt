package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "login-lockout")
data class LoginLockoutProperties(
    val maxFailedAttempts: Int = 5,
    val lockoutDurationMinutes: Long = 15,
)
