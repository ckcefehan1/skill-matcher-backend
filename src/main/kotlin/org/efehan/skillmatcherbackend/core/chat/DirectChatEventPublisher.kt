package org.efehan.skillmatcherbackend.core.chat

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

// Fallback when RabbitMQ is disabled (tests, dev without compose): same
// after-commit semantics, delivery happens in-process.
@Service
@ConditionalOnProperty(name = ["chat.rabbitmq.enabled"], havingValue = "false", matchIfMissing = true)
class DirectChatEventPublisher(
    private val dispatcher: ChatEventDispatcher,
) : ChatEventPublisher {
    override fun publish(event: ChatEvent) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        dispatcher.dispatch(event)
                    }
                },
            )
        } else {
            dispatcher.dispatch(event)
        }
    }
}
