package org.efehan.skillmatcherbackend.core.chat

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateConversationRequest(
    @field:NotBlank
    val userId: String,
)

data class SendMessageRequest(
    @field:NotBlank
    val conversationId: String,
    @field:NotBlank
    @field:Size(max = 5000)
    val content: String,
)

data class MarkConversationReadRequest(
    @field:NotBlank
    val conversationId: String,
)

data class TypingRequest(
    @field:NotBlank
    val conversationId: String,
)

data class TypingResponse(
    val conversationId: String,
    val userId: String,
)

data class PresenceResponse(
    val userId: String,
    val online: Boolean,
)

data class ChatErrorResponse(
    val errorCode: String,
    val message: String,
    val details: List<String> = emptyList(),
)

data class ReadReceiptResponse(
    val conversationId: String,
    val readBy: String,
    val readAt: Instant,
)

data class ConversationResponse(
    val id: String,
    val partner: ChatUserResponse,
    val lastMessage: ChatMessageResponse?,
    val unreadCount: Long,
    val createdDate: Instant,
)

data class ChatMessageResponse(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val sentAt: Instant,
    val readAt: Instant?,
)

data class ChatUserResponse(
    val id: String,
    val firstName: String,
    val lastName: String,
    val online: Boolean,
)
