package org.efehan.skillmatcherbackend.core.chat

import org.efehan.skillmatcherbackend.config.properties.RabbitMQProperties
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

// Chat events must not reach the queue if the surrounding transaction rolls back
@Service
@ConditionalOnProperty(name = ["chat.rabbitmq.enabled"], havingValue = "true")
class RabbitChatEventPublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val properties: RabbitMQProperties,
) : ChatEventPublisher {
    override fun publish(event: ChatEvent) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        send(event)
                    }
                },
            )
        } else {
            send(event)
        }
    }

    /**
     * ponytail: publishing after commit trades one failure mode for the other — if
     * the broker is down here, the message is committed but the notification is
     * lost for good. Accepted over publishing inside the transaction, which would
     * emit phantom events on rollback. Upgrade path is a transactional outbox.
     */
    private fun send(event: ChatEvent) {
        rabbitTemplate.convertAndSend(properties.exchange, event.routingKey, ChatEventEnvelope(TenantContext.get(), event))
    }
}
