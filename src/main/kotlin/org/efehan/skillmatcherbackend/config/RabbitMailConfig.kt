package org.efehan.skillmatcherbackend.config

import org.efehan.skillmatcherbackend.config.properties.RabbitMQProperties
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@Configuration
@ConditionalOnProperty(name = ["mail.rabbitmq.enabled"], havingValue = "true")
class RabbitMailConfig(
    private val properties: RabbitMQProperties,
) {
    @Bean
    fun mailExchange() = TopicExchange(properties.exchange)

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
    fun mailBinding() = BindingBuilder.bind(mailQueue()).to(mailExchange()).with(properties.mailRoutingKey)

    @Bean
    fun mailMessageConverter(): MessageConverter = JacksonJsonMessageConverter(JsonMapper.builder().findAndAddModules().build())

    @Bean
    fun mailRabbitTemplate(
        connectionFactory: ConnectionFactory,
        mailMessageConverter: MessageConverter,
    ) = RabbitTemplate(connectionFactory).apply {
        messageConverter = mailMessageConverter
    }

    @Bean
    fun mailRabbitListenerContainerFactory(
        connectionFactory: ConnectionFactory,
        mailMessageConverter: MessageConverter,
    ): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        factory.setConnectionFactory(connectionFactory)
        factory.setMessageConverter(mailMessageConverter)
        factory.setDefaultRequeueRejected(false)
        factory.setAdviceChain(
            RetryInterceptorBuilder
                .stateless()
                .maxRetries(properties.retry.maxRetries) // plus initial delivery, then DLQ
                .backOffOptions(
                    properties.retry.initialIntervalMs,
                    properties.retry.multiplier,
                    properties.retry.maxIntervalMs,
                ).build(),
        )
        return factory
    }
}
