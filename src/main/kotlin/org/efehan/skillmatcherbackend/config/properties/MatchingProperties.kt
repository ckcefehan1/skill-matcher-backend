package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "matching")
data class MatchingProperties(
    val mustHaveCoverageThreshold: Double,
    val capacityMode: CapacityMode,
    val capacityPenalty: Double,
    val mlEnabled: Boolean,
    val mlWeight: Double,
)

enum class CapacityMode {
    HARD,
    SOFT,
}
