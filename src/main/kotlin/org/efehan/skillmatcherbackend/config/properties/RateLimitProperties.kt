package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rate-limit")
data class RateLimitProperties(
    val loginPerMinute: Long,
    val passwordResetPerMinute: Long,
    val invitationPerMinute: Long,
    val refreshPerMinute: Long,
    val wsTicketPerMinute: Long,
)
