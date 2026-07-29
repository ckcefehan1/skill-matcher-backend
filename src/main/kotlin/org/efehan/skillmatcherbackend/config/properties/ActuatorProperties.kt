package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "actuator")
data class ActuatorProperties(
    val username: String,
    val password: String,
)
