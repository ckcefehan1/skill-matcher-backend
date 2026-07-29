package org.efehan.skillmatcherbackend.core.auth

import com.github.benmanes.caffeine.cache.Caffeine
import org.efehan.skillmatcherbackend.config.properties.WsTicketProperties
import org.springframework.stereotype.Service
import java.util.UUID

// ponytail: in-memory, eine Instanz — bei Multi-Instanz Redis (siehe Roadmap)

/**
 * One-time tickets for STOMP CONNECT authentication. The SPA cannot read the
 * httpOnly access_token cookie, so it exchanges its cookie-authenticated
 * session for a short-lived ticket and sends it as a STOMP native header.
 */
@Service
class WsTicketService(
    properties: WsTicketProperties,
) {
    val ttlSeconds: Long = properties.ttl.toSeconds()

    private val tickets =
        Caffeine
            .newBuilder()
            .expireAfterWrite(properties.ttl)
            .maximumSize(properties.maxSize)
            .build<String, String>()

    fun issue(userId: String): String {
        val ticket = UUID.randomUUID().toString()
        tickets.put(ticket, userId)
        return ticket
    }

    fun consume(ticket: String): String? = tickets.asMap().remove(ticket)
}
