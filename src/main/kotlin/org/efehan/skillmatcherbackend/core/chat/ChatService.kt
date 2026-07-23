package org.efehan.skillmatcherbackend.core.chat

import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.ChatMessageModel
import org.efehan.skillmatcherbackend.persistence.ChatMessageRepository
import org.efehan.skillmatcherbackend.persistence.ConversationModel
import org.efehan.skillmatcherbackend.persistence.ConversationRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class ChatService(
    private val conversationRepo: ConversationRepository,
    private val messageRepo: ChatMessageRepository,
    private val userRepo: UserRepository,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    fun getConversations(user: UserModel): List<ConversationModel> = conversationRepo.findByUser(user)

    fun getLastMessages(conversations: List<ConversationModel>): Map<String, ChatMessageModel> {
        if (conversations.isEmpty()) return emptyMap()
        return messageRepo.findLastMessagesByConversations(conversations).associateBy { it.conversation.id }
    }

    fun getLastMessage(conversation: ConversationModel): ChatMessageModel? =
        messageRepo.findTopByConversationOrderBySentAtDesc(conversation)

    fun getMessages(
        user: UserModel,
        conversationId: String,
        before: Instant,
        limit: Int,
    ): List<ChatMessageModel> {
        val conversation =
            conversationRepo.findById(conversationId).orElseThrow {
                EntryNotFoundException(
                    resource = "Conversation",
                    field = "id",
                    value = conversationId,
                    errorCode = GlobalErrorCode.CONVERSATION_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
            }

        if (conversation.userOne.id != user.id && conversation.userTwo.id != user.id) {
            throw AccessDeniedException(
                resource = "Conversation",
                errorCode = GlobalErrorCode.CONVERSATION_ACCESS_DENIED,
                status = HttpStatus.FORBIDDEN,
            )
        }

        val safeLimit = limit.coerceIn(1, 100)
        return messageRepo.findByConversationBefore(conversation, before, PageRequest.of(0, safeLimit))
    }

    fun createConversation(
        user: UserModel,
        partnerId: String,
    ): Pair<ConversationModel, Boolean> {
        if (user.id == partnerId) {
            throw IllegalArgumentException("Cannot create a conversation with yourself.")
        }

        val partner =
            userRepo.findById(partnerId).orElseThrow {
                EntryNotFoundException(
                    resource = "User",
                    field = "id",
                    value = partnerId,
                    errorCode = GlobalErrorCode.USER_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
            }

        val existing = conversationRepo.findByUsers(user, partner)
        if (existing != null) {
            return existing to false
        }

        val userOne = if (user.id < partner.id) user else partner
        val userTwo = if (user.id < partner.id) partner else user

        return try {
            val conversation = conversationRepo.save(ConversationModel(userOne = userOne, userTwo = userTwo))
            conversation to true
        } catch (_: DataIntegrityViolationException) {
            conversationRepo.findByUsers(user, partner)!! to false
        }
    }

    fun sendMessage(
        user: UserModel,
        conversationId: String,
        content: String,
    ): ChatMessageModel {
        val conversation =
            conversationRepo.findById(conversationId).orElseThrow {
                EntryNotFoundException(
                    resource = "Conversation",
                    field = "id",
                    value = conversationId,
                    errorCode = GlobalErrorCode.CONVERSATION_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
            }

        if (conversation.userOne.id != user.id && conversation.userTwo.id != user.id) {
            throw AccessDeniedException(
                resource = "Conversation",
                errorCode = GlobalErrorCode.CONVERSATION_ACCESS_DENIED,
                status = HttpStatus.FORBIDDEN,
            )
        }

        val sentAt = Instant.now()

        val message =
            messageRepo.save(
                ChatMessageModel(
                    conversation = conversation,
                    sender = user,
                    content = content,
                    sentAt = sentAt,
                ),
            )

        conversation.lastMessageAt = sentAt

        val recipient = if (conversation.userOne.id == user.id) conversation.userTwo else conversation.userOne
        val response = message.toDTO()
        messagingTemplate.convertAndSendToUser(recipient.id, "/queue/messages", response)
        messagingTemplate.convertAndSendToUser(user.id, "/queue/messages", response)

        return message
    }

    fun getUnreadCounts(
        user: UserModel,
        conversations: List<ConversationModel>,
    ): Map<String, Long> {
        if (conversations.isEmpty()) return emptyMap()
        return messageRepo
            .countUnreadByConversations(conversations, user)
            .associate { (it[0] as String) to (it[1] as Long) }
    }

    fun markConversationRead(
        user: UserModel,
        conversationId: String,
    ) {
        val conversation =
            conversationRepo.findById(conversationId).orElseThrow {
                EntryNotFoundException(
                    resource = "Conversation",
                    field = "id",
                    value = conversationId,
                    errorCode = GlobalErrorCode.CONVERSATION_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
            }

        if (conversation.userOne.id != user.id && conversation.userTwo.id != user.id) {
            throw AccessDeniedException(
                resource = "Conversation",
                errorCode = GlobalErrorCode.CONVERSATION_ACCESS_DENIED,
                status = HttpStatus.FORBIDDEN,
            )
        }

        val readAt = Instant.now()
        val updated = messageRepo.markConversationRead(conversation, user, readAt)

        if (updated > 0) {
            val partner = if (conversation.userOne.id == user.id) conversation.userTwo else conversation.userOne
            val receipt = ReadReceiptResponse(conversationId = conversation.id, readBy = user.id, readAt = readAt)
            messagingTemplate.convertAndSendToUser(partner.id, "/queue/read-receipts", receipt)
        }
    }

    fun notifyTyping(
        user: UserModel,
        conversationId: String,
    ) {
        val conversation =
            conversationRepo.findById(conversationId).orElseThrow {
                EntryNotFoundException(
                    resource = "Conversation",
                    field = "id",
                    value = conversationId,
                    errorCode = GlobalErrorCode.CONVERSATION_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
            }

        if (conversation.userOne.id != user.id && conversation.userTwo.id != user.id) {
            throw AccessDeniedException(
                resource = "Conversation",
                errorCode = GlobalErrorCode.CONVERSATION_ACCESS_DENIED,
                status = HttpStatus.FORBIDDEN,
            )
        }

        val partner = if (conversation.userOne.id == user.id) conversation.userTwo else conversation.userOne
        val typing = TypingResponse(conversationId = conversation.id, userId = user.id)
        messagingTemplate.convertAndSendToUser(partner.id, "/queue/typing", typing)
    }
}
