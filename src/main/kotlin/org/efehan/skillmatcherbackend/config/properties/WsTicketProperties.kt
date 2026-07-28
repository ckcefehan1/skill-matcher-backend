package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ws-ticket")
data class WsTicketProperties(
    val ttl: Duration = Duration.ofSeconds(60),
    val maxSize: Long = 10_000,
)
