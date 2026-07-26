package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "password-reset")
data class PasswordResetProperties(
    val tokenExpirationHours: Long,
)
