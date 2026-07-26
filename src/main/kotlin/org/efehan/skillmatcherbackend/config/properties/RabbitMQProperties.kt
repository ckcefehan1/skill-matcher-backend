package org.efehan.skillmatcherbackend.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rabbitmq")
data class RabbitMQProperties(
    val exchange: String = "skill-matcher.events",
    val mailQueue: String = "mail.send",
    val mailDlq: String = "mail.send.dlq",
    val mailRoutingKey: String = "mail.send",
    val retry: Retry = Retry(),
) {
    data class Retry(
        val maxRetries: Int = 2,
        val initialIntervalMs: Long = 1000,
        val multiplier: Double = 2.0,
        val maxIntervalMs: Long = 5000,
    )
}
