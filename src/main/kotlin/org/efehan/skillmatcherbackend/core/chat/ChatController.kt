package org.efehan.skillmatcherbackend.core.chat

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.efehan.skillmatcherbackend.core.auth.SecurityUser
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
import org.efehan.skillmatcherbackend.persistence.ChatMessageModel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "Direct messaging")
class ChatController(
    private val chatService: ChatService,
    private val presenceService: PresenceService,
) {
    @Operation(
        summary = "Search chat partners",
        description = "Searches enabled users by name or email. Any authenticated user may search for chat partners.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Users retrieved."),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/users/search")
    @ResponseStatus(HttpStatus.OK)
    fun searchChatPartners(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @RequestParam q: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): List<ChatUserResponse> =
        chatService.searchChatPartners(securityUser.user, q, limit).map {
            ChatUserResponse(
                id = it.id,
                firstName = it.firstName ?: "",
                lastName = it.lastName ?: "",
                online = presenceService.isOnline(it.id),
            )
        }

    @Operation(summary = "Get my conversations", description = "Returns all conversations for the authenticated user.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Conversations retrieved.",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Not authenticated",
                                value = """
                                {
                                    "errorCode": "USER_MUST_LOGIN",
                                    "errorMessage": "User must be logged in."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/conversations")
    @ResponseStatus(HttpStatus.OK)
    fun getConversations(
        @AuthenticationPrincipal securityUser: SecurityUser,
    ): List<ConversationResponse> {
        val user = securityUser.user
        val conversations = chatService.getConversations(user)
        val lastMessages = chatService.getLastMessages(conversations)
        val unreadCounts = chatService.getUnreadCounts(user, conversations)
        return conversations.map {
            val partnerId = if (it.userOne.id == user.id) it.userTwo.id else it.userOne.id
            it.toDTO(
                currentUser = user,
                lastMessage = lastMessages[it.id],
                unreadCount = unreadCounts[it.id] ?: 0,
                partnerOnline = presenceService.isOnline(partnerId),
            )
        }
    }

    @Operation(
        summary = "Get message history",
        description = "Returns messages for a conversation with cursor-based pagination.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Messages retrieved.",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Not authenticated",
                                value = """
                                {
                                    "errorCode": "USER_MUST_LOGIN",
                                    "errorMessage": "User must be logged in."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Not a conversation member.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Forbidden",
                                value = """
                                {
                                    "errorCode": "CONVERSATION_ACCESS_DENIED",
                                    "errorMessage": "Not allowed to modify conversation."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Conversation not found.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Not found",
                                value = """
                                {
                                    "errorCode": "CONVERSATION_NOT_FOUND",
                                    "errorMessage": "Conversation with id '550e8400-e29b-41d4-a716-446655440000' could not be found."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.OK)
    fun getMessages(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable id: String,
        @RequestParam(required = false) before: Instant?,
        @RequestParam(defaultValue = "20") limit: Int,
    ): List<ChatMessageResponse> =
        chatService
            .getMessages(
                user = securityUser.user,
                conversationId = id,
                before = before ?: Instant.now(),
                limit = limit,
            ).map(ChatMessageModel::toDTO)

    @Operation(
        summary = "Create or find a conversation",
        description = "Creates a new conversation or returns an existing one.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Conversation created.",
            ),
            ApiResponse(
                responseCode = "200",
                description = "Conversation already exists.",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Cannot chat with yourself.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Self chat",
                                value = """
                                {
                                    "errorCode": "VALIDATION_ERROR",
                                    "errorMessage": "Request validation failed."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Not authenticated.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Not authenticated",
                                value = """
                                {
                                    "errorCode": "USER_MUST_LOGIN",
                                    "errorMessage": "User must be logged in."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "User not found.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GlobalErrorCodeResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Not found",
                                value = """
                                {
                                    "errorCode": "USER_NOT_FOUND",
                                    "errorMessage": "User with id '550e8400-e29b-41d4-a716-446655440000' could not be found."
                                }
                                """,
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/conversations")
    fun createConversation(
        @AuthenticationPrincipal securityUser: SecurityUser,
        @Valid @RequestBody request: CreateConversationRequest,
    ): ResponseEntity<ConversationResponse> {
        val user = securityUser.user
        val (conversation, created) =
            chatService.createConversation(
                user = user,
                partnerId = request.userId,
            )
        val response =
            conversation
                .toDTO(
                    currentUser = user,
                    lastMessage = chatService.getLastMessage(conversation),
                    partnerOnline = presenceService.isOnline(request.userId),
                )

        return if (created) {
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } else {
            ResponseEntity.ok(response)
        }
    }
}
