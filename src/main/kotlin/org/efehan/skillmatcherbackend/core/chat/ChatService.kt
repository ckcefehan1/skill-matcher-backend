package org.efehan.skillmatcherbackend.core.chat

import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.ChatMessageModel
import org.efehan.skillmatcherbackend.persistence.ChatMessageRepository
import org.efehan.skillmatcherbackend.persistence.ConversationModel
import org.efehan.skillmatcherbackend.persistence.ConversationRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
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
    private val userService: UserService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val chatEventPublisher: ChatEventPublisher,
) {
    fun getConversations(user: UserModel): List<ConversationModel> = conversationRepo.findByUser(user)

    // the endpoint returns no email, so matching on it would turn this into an address oracle
    fun searchChatPartners(
        user: UserModel,
        q: String,
        limit: Int,
    ): List<UserModel> {
        val term = q.trim()
        if (term.length < MIN_SEARCH_TERM_LENGTH) return emptyList()
        return userRepo.searchChatPartners(term, user.id, PageRequest.of(0, limit.coerceIn(1, 50)))
    }

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

        val partner = userService.getUser(partnerId)

        val existing = conversationRepo.findByUsers(user, partner)
        if (existing != null) {
            return existing to false
        }

        val userOne = if (user.id < partner.id) user else partner
        val userTwo = if (user.id < partner.id) partner else user

        // A simultaneous create by the other party is left to uq_conversations_user_pair:
        // the violation rolls this transaction back and the retry finds the row above.
        // Catching it here cannot work — the id is entity-assigned, so the INSERT is
        // deferred to flush time and never throws inside the call.
        return conversationRepo.save(ConversationModel(userOne = userOne, userTwo = userTwo)) to true
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
        chatEventPublisher.publish(
            ChatEvent.MessageCreated(
                message = message.toDTO(),
                recipientId = recipient.id,
                senderDisplayName = listOfNotNull(user.firstName, user.lastName).joinToString(" ").ifBlank { user.email },
            ),
        )

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
            chatEventPublisher.publish(ChatEvent.ConversationRead(receipt = receipt, partnerId = partner.id))
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
        // ponytail: typing stays a direct push — ephemeral, latency-critical, no durability needed
        messagingTemplate.convertAndSendToUser(partner.id, "/queue/typing", typing)
    }

    private companion object {
        const val MIN_SEARCH_TERM_LENGTH = 2
    }
}
