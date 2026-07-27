package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "cache")
data class CacheProperties(
    val skillCatalogTtl: Duration,
    val skillCatalogMaxSize: Long,
    val matchingTtl: Duration,
    val matchingMaxSize: Long,
)
