package org.efehan.skillmatcherbackend.core.chat

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
