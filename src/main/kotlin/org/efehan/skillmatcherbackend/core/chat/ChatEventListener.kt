package org.efehan.skillmatcherbackend.core.chat

import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

// Idempotency: notification insert is deduped by (user, type, message id),
// WebSocket push duplicates are dropped client-side by message id.
@Component
@ConditionalOnProperty(name = ["chat.rabbitmq.enabled"], havingValue = "true")
class ChatEventListener(
    private val dispatcher: ChatEventDispatcher,
) {
    @RabbitListener(queues = ["\${rabbitmq.chat-queue}"], containerFactory = "eventRabbitListenerContainerFactory")
    fun handle(envelope: ChatEventEnvelope) {
        try {
            envelope.companyId?.let { TenantContext.set(it) } ?: TenantContext.allowRoot()
            dispatcher.dispatch(envelope.event)
        } finally {
            TenantContext.clear()
        }
    }
}
