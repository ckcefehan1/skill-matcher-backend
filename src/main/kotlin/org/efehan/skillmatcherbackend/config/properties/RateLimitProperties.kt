package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rate-limit")
data class RateLimitProperties(
    val loginPerMinute: Long = 5,
    val passwordResetPerMinute: Long = 5,
    val invitationPerMinute: Long = 10,
    val refreshPerMinute: Long = 30,
)
