package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "invitation")
data class InvitationProperties(
    val tokenExpirationHours: Long,
    val codeExpirationMinutes: Long,
)
