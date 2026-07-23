package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "matching.skill-graph")
data class SkillGraphProperties(
    val enabled: Boolean = true,
    val minCoOccurrence: Int = 5,
    val derivationEnabled: Boolean = true,
    val minTransferPenalty: Double = 0.5,
)
