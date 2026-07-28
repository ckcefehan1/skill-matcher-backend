package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cookie")
data class CookieProperties(
    val secure: Boolean,
    val accessTokenName: String = "access_token",
    val refreshTokenName: String = "refresh_token",
)
