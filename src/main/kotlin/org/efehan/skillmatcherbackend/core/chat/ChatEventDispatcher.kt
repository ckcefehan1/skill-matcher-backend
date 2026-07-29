package org.efehan.skillmatcherbackend.core.chat

import org.efehan.skillmatcherbackend.core.notification.NotificationService
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

/**
 * Shared delivery logic for chat events, reached either through the RabbitMQ
 * listener (prod) or the direct publisher (tests/dev without RabbitMQ).
 */
@Component
class ChatEventDispatcher(
    private val messagingTemplate: SimpMessagingTemplate,
    private val notificationService: NotificationService,
    private val userRepository: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(ChatEventDispatcher::class.java)

    fun dispatch(event: ChatEvent) {
        when (event) {
            is ChatEvent.MessageCreated -> deliverMessage(event)
            is ChatEvent.ConversationRead -> deliverReadReceipt(event)
        }
    }

    private fun deliverMessage(event: ChatEvent.MessageCreated) {
        val message = event.message
        messagingTemplate.convertAndSendToUser(event.recipientId, "/queue/messages", message)
        messagingTemplate.convertAndSendToUser(message.senderId, "/queue/messages", message)

        val recipient = userRepository.findById(event.recipientId).orElse(null)
        if (recipient == null) {
            logger.warn("Skipping chat notification: recipient {} no longer exists", event.recipientId)
            return
        }
        val notification =
            notificationService.createChatNotification(
                recipient = recipient,
                senderName = event.senderDisplayName,
                messageId = message.id,
                messagePreview = message.content,
            ) ?: return
        messagingTemplate.convertAndSendToUser(event.recipientId, "/queue/notifications", notification.toDTO())
    }

    private fun deliverReadReceipt(event: ChatEvent.ConversationRead) {
        messagingTemplate.convertAndSendToUser(event.partnerId, "/queue/read-receipts", event.receipt)
    }
}
