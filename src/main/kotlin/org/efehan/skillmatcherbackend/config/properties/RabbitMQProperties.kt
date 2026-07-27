package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rabbitmq")
data class RabbitMQProperties(
    val exchange: String,
    val mailQueue: String,
    val mailDlq: String,
    val mailRoutingKey: String,
    val retry: Retry,
) {
    data class Retry(
        val maxRetries: Int,
        val initialIntervalMs: Long,
        val multiplier: Double,
        val maxIntervalMs: Long,
    )
}
