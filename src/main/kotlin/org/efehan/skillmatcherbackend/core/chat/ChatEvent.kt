package org.efehan.skillmatcherbackend.core.chat

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/** See MailEnvelope — companyId lets the listener scope its DB work to the publisher's tenant. */
data class ChatEventEnvelope(
    val companyId: String?,
    val event: ChatEvent,
)

/**
 * Nested inside the envelope the AMQP `__TypeId__` header only names the envelope, so the
 * concrete event type has to travel in the payload itself.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ChatEvent.MessageCreated::class, name = "message.created"),
    JsonSubTypes.Type(value = ChatEvent.ConversationRead::class, name = "conversation.read"),
)
sealed interface ChatEvent {
    val routingKey: String

    data class MessageCreated(
        val message: ChatMessageResponse,
        val recipientId: String,
        val senderDisplayName: String,
    ) : ChatEvent {
        override val routingKey = ROUTING_KEY_MESSAGE_CREATED
    }

    data class ConversationRead(
        val receipt: ReadReceiptResponse,
        val partnerId: String,
    ) : ChatEvent {
        override val routingKey = ROUTING_KEY_CONVERSATION_READ
    }

    companion object {
        const val ROUTING_KEY_MESSAGE_CREATED = "chat.message.created"
        const val ROUTING_KEY_CONVERSATION_READ = "chat.conversation.read"
    }
}
