package org.efehan.skillmatcherbackend.core.chat

/** See MailEnvelope — companyId lets the listener scope its DB work to the publisher's tenant. */
data class ChatEventEnvelope(
    val companyId: String?,
    val event: ChatEvent,
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
