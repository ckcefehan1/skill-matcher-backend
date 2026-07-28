package org.efehan.skillmatcherbackend.config

import org.efehan.skillmatcherbackend.config.properties.RabbitMQProperties
import org.efehan.skillmatcherbackend.core.chat.ChatEvent
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The queue buys the durable half of the work: the notification insert leaves the
 * request path and gets retry plus DLQ. The WebSocket push is best effort on top —
 * the SimpleBroker only reaches sessions of the consuming instance.
 *
 * ponytail: shared work queue, single instance. At two or more instances a chat
 * event lands on one instance while the recipient's session may hang off another.
 * Upgrade path: an exclusive per-instance queue for the push (fanout) next to this
 * durable work queue, or replace the SimpleBroker with enableStompBrokerRelay.
 * Presence and WS tickets are in-memory today and would need the same treatment.
 */
@Configuration
@ConditionalOnProperty(name = ["chat.rabbitmq.enabled"], havingValue = "true")
class RabbitChatConfig(
    private val properties: RabbitMQProperties,
) {
    @Bean
    fun chatQueue() =
        QueueBuilder
            .durable(properties.chatQueue)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", properties.chatDlq)
            .build()

    @Bean
    fun chatDlq() = QueueBuilder.durable(properties.chatDlq).build()

    @Bean
    fun chatMessageCreatedBinding(eventExchange: TopicExchange) =
        BindingBuilder
            .bind(chatQueue())
            .to(eventExchange)
            .with(ChatEvent.ROUTING_KEY_MESSAGE_CREATED)

    @Bean
    fun chatConversationReadBinding(eventExchange: TopicExchange) =
        BindingBuilder
            .bind(chatQueue())
            .to(eventExchange)
            .with(ChatEvent.ROUTING_KEY_CONVERSATION_READ)
}
