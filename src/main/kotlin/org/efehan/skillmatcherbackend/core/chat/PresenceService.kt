package org.efehan.skillmatcherbackend.core.chat

import org.efehan.skillmatcherbackend.config.WebSocketPrincipal
import org.efehan.skillmatcherbackend.persistence.ConversationRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Service
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks online users by their WebSocket sessions and notifies conversation
 * partners when a user comes online or goes offline. A user is online as long
 * as at least one session is open (multiple tabs/devices are supported).
 */
@Service
class PresenceService(
    private val conversationRepo: ConversationRepository,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    private val sessionsByUser = ConcurrentHashMap<String, MutableSet<String>>()

    fun isOnline(userId: String): Boolean = sessionsByUser[userId]?.isNotEmpty() == true

    @EventListener
    fun onConnected(event: SessionConnectedEvent) {
        val user = (event.user as? WebSocketPrincipal)?.securityUser?.user ?: return
        val sessionId = StompHeaderAccessor.wrap(event.message).sessionId ?: return

        val sessions =
            sessionsByUser.compute(user.id) { _, existing ->
                (existing ?: ConcurrentHashMap.newKeySet()).also { it.add(sessionId) }
            }

        if (sessions?.size == 1) {
            notifyPartners(user, online = true)
        }
    }

    @EventListener
    fun onDisconnected(event: SessionDisconnectEvent) {
        val user = (event.user as? WebSocketPrincipal)?.securityUser?.user ?: return

        var becameOffline = false
        sessionsByUser.computeIfPresent(user.id) { _, existing ->
            existing.remove(event.sessionId)
            if (existing.isEmpty()) {
                becameOffline = true
                null
            } else {
                existing
            }
        }

        if (becameOffline) {
            notifyPartners(user, online = false)
        }
    }

    private fun notifyPartners(
        user: UserModel,
        online: Boolean,
    ) {
        conversationRepo.findByUser(user).forEach { conversation ->
            val partner = if (conversation.userOne.id == user.id) conversation.userTwo else conversation.userOne
            messagingTemplate.convertAndSendToUser(
                partner.id,
                "/queue/presence",
                PresenceResponse(userId = user.id, online = online),
            )
        }
    }
}
