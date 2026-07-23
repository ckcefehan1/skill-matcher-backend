package org.efehan.skillmatcherbackend.core.chat

import jakarta.validation.Validator
import org.efehan.skillmatcherbackend.config.WebSocketPrincipal
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.shared.exceptions.AccessDeniedException
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class ChatWebSocketController(
    private val chatService: ChatService,
    private val validator: Validator,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    @MessageMapping("/chat.send")
    fun sendMessage(
        principal: Principal,
        request: SendMessageRequest,
    ) {
        val user = resolveUser(principal) ?: return
        if (!isValid(user.id, request)) return

        try {
            chatService.sendMessage(user, request.conversationId, request.content)
        } catch (ex: EntryNotFoundException) {
            sendError(user.id, ex.errorCode, ex.message)
        } catch (ex: AccessDeniedException) {
            sendError(user.id, ex.errorCode, ex.message)
        }
    }

    @MessageMapping("/chat.read")
    fun markConversationRead(
        principal: Principal,
        request: MarkConversationReadRequest,
    ) {
        val user = resolveUser(principal) ?: return
        if (!isValid(user.id, request)) return

        try {
            chatService.markConversationRead(user, request.conversationId)
        } catch (ex: EntryNotFoundException) {
            sendError(user.id, ex.errorCode, ex.message)
        } catch (ex: AccessDeniedException) {
            sendError(user.id, ex.errorCode, ex.message)
        }
    }

    @MessageMapping("/chat.typing")
    fun notifyTyping(
        principal: Principal,
        request: TypingRequest,
    ) {
        val user = resolveUser(principal) ?: return
        if (!isValid(user.id, request)) return

        try {
            chatService.notifyTyping(user, request.conversationId)
        } catch (ex: EntryNotFoundException) {
            sendError(user.id, ex.errorCode, ex.message)
        } catch (ex: AccessDeniedException) {
            sendError(user.id, ex.errorCode, ex.message)
        }
    }

    private fun resolveUser(principal: Principal): UserModel? = (principal as? WebSocketPrincipal)?.securityUser?.user

    private fun isValid(
        userId: String,
        request: Any,
    ): Boolean {
        val violations = validator.validate(request)
        if (violations.isEmpty()) return true

        sendError(
            userId,
            GlobalErrorCode.VALIDATION_ERROR,
            GlobalErrorCode.VALIDATION_ERROR.description,
            violations.map { "${it.propertyPath}: ${it.message}" },
        )
        return false
    }

    private fun sendError(
        userId: String,
        errorCode: GlobalErrorCode,
        message: String,
        details: List<String> = emptyList(),
    ) {
        messagingTemplate.convertAndSendToUser(
            userId,
            "/queue/errors",
            ChatErrorResponse(errorCode = errorCode.name, message = message, details = details),
        )
    }
}
