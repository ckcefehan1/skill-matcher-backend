package org.efehan.skillmatcherbackend.config

import org.efehan.skillmatcherbackend.config.properties.RabbitMQProperties
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["mail.rabbitmq.enabled"], havingValue = "true")
class RabbitMailConfig(
    private val properties: RabbitMQProperties,
) {
    @Bean
    fun mailQueue() =
        QueueBuilder
            .durable(properties.mailQueue)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", properties.mailDlq)
            .build()

    @Bean
    fun mailDlq() = QueueBuilder.durable(properties.mailDlq).build()

    @Bean
    fun mailBinding(eventExchange: TopicExchange) = BindingBuilder.bind(mailQueue()).to(eventExchange).with(properties.mailRoutingKey)
}
