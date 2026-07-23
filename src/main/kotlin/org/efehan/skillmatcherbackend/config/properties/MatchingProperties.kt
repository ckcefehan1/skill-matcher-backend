package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "matching")
data class MatchingProperties(
    val mustHaveCoverageThreshold: Double = 0.6,
    val capacityMode: CapacityMode = CapacityMode.SOFT,
    val capacityPenalty: Double = 0.15,
    val mlEnabled: Boolean = false,
    val mlWeight: Double = 0.3,
)

enum class CapacityMode {
    HARD,
    SOFT,
}
