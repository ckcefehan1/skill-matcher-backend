package org.efehan.skillmatcherbackend.core.mail.rabbit

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
class RabbitMailConfig {
    companion object {
        const val EXCHANGE = "skill-matcher.events"
        const val MAIL_QUEUE = "mail.send"
        const val MAIL_DLQ = "mail.send.dlq"
        const val ROUTING_KEY = "mail.send"
    }

    @Bean
    fun mailExchange() = TopicExchange(EXCHANGE)

    @Bean
    fun mailQueue() =
        QueueBuilder
            .durable(MAIL_QUEUE)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", MAIL_DLQ)
            .build()

    @Bean
    fun mailDlq() = QueueBuilder.durable(MAIL_DLQ).build()

    @Bean
    fun mailBinding() = BindingBuilder.bind(mailQueue()).to(mailExchange()).with(ROUTING_KEY)

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
                .maxRetries(2) // 3 delivery attempts total, then DLQ
                .backOffOptions(1000, 2.0, 5000)
                .build(),
        )
        return factory
    }
}
