package org.efehan.skillmatcherbackend.config

import org.springframework.beans.factory.ObjectProvider
import org.springframework.messaging.simp.user.SimpUserRegistry
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the raw WebSocket sessions so they can be closed server-side. A STOMP
 * session outlives the 60s ticket and the 15min access token, so logout,
 * password change and account deactivation have to tear it down explicitly.
 *
 * ponytail: in-memory, single instance — same ceiling as [org.efehan.skillmatcherbackend.core.auth.WsTicketService].
 */
@Component
class WebSocketSessionRegistry(
    // SimpUserRegistry comes from @EnableWebSocketMessageBroker, which itself needs WebSocketConfig
    private val userRegistry: ObjectProvider<SimpUserRegistry>,
) {
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    fun register(session: WebSocketSession) {
        sessions[session.id] = session
    }

    fun unregister(sessionId: String) {
        sessions.remove(sessionId)
    }

    fun disconnect(userId: String) {
        val user = userRegistry.getObject().getUser(userId) ?: return
        user.sessions.forEach { sessions.remove(it.id)?.close(CloseStatus.NORMAL) }
    }
}
