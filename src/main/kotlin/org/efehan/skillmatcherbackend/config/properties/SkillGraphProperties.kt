package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "matching.skill-graph")
data class SkillGraphProperties(
    val enabled: Boolean,
    val minCoOccurrence: Int,
    val derivationEnabled: Boolean,
    val minTransferPenalty: Double,
)
