package org.efehan.skillmatcherbackend.config

import org.efehan.skillmatcherbackend.config.properties.RabbitMQProperties
import org.efehan.skillmatcherbackend.core.chat.ChatEvent
import org.efehan.skillmatcherbackend.core.mail.rabbit.MailCommand
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

/**
 * Exchange, converter, template and listener factory are identical for mail and
 * chat; only queues, DLQs and bindings differ (see [RabbitMailConfig], [RabbitChatConfig]).
 */
@Configuration
@ConditionalOnExpression("\${mail.rabbitmq.enabled:false} or \${chat.rabbitmq.enabled:false}")
class RabbitCommonConfig(
    private val properties: RabbitMQProperties,
) {
    @Bean
    fun eventExchange() = TopicExchange(properties.exchange)

    /**
     * Logical type ids instead of the default fully qualified class name: the
     * listener parameters are sealed interfaces, so `__TypeId__` decides the
     * concrete type. Without this mapping a package rename or a rolling deploy
     * would push every in-flight message to the DLQ.
     */
    @Bean
    fun eventMessageConverter(): MessageConverter {
        val typeMapper =
            DefaultJacksonJavaTypeMapper().apply {
                setIdClassMapping(
                    mapOf(
                        ChatEvent.ROUTING_KEY_MESSAGE_CREATED to ChatEvent.MessageCreated::class.java,
                        ChatEvent.ROUTING_KEY_CONVERSATION_READ to ChatEvent.ConversationRead::class.java,
                        "mail.invitation" to MailCommand.Invitation::class.java,
                        "mail.welcome" to MailCommand.Welcome::class.java,
                        "mail.password-reset" to MailCommand.PasswordReset::class.java,
                        "mail.application-submitted" to MailCommand.ApplicationSubmitted::class.java,
                        "mail.application-decided" to MailCommand.ApplicationDecided::class.java,
                        "mail.project-invitation" to MailCommand.ProjectInvitation::class.java,
                        "mail.project-invitation-response" to MailCommand.ProjectInvitationResponse::class.java,
                    ),
                )
            }
        return JacksonJsonMessageConverter(JsonMapper.builder().findAndAddModules().build()).apply {
            javaTypeMapper = typeMapper
        }
    }

    @Bean
    fun rabbitTemplate(
        connectionFactory: ConnectionFactory,
        eventMessageConverter: MessageConverter,
    ) = RabbitTemplate(connectionFactory).apply {
        messageConverter = eventMessageConverter
    }

    @Bean
    fun eventRabbitListenerContainerFactory(
        connectionFactory: ConnectionFactory,
        eventMessageConverter: MessageConverter,
    ): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        factory.setConnectionFactory(connectionFactory)
        factory.setMessageConverter(eventMessageConverter)
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
