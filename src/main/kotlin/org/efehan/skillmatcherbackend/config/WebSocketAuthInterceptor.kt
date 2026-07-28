package org.efehan.skillmatcherbackend.config

import org.efehan.skillmatcherbackend.core.auth.CustomUserDetailsService
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.auth.SecurityUser
import org.efehan.skillmatcherbackend.core.auth.WsTicketService
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidTokenException
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageDeliveryException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor

@Configuration
class WebSocketAuthInterceptor(
    private val jwtService: JwtService,
    private val userDetailsService: CustomUserDetailsService,
    private val wsTicketService: WsTicketService,
) : ChannelInterceptor {
    override fun preSend(
        message: Message<*>,
        channel: MessageChannel,
    ): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java) ?: return message

        when (accessor.command) {
            StompCommand.CONNECT -> authenticate(accessor)
            StompCommand.SUBSCRIBE -> authorizeSubscribe(accessor.destination)
            StompCommand.SEND -> authorizeSend(accessor.destination)
            else -> {}
        }
        return message
    }

    private fun authenticate(accessor: StompHeaderAccessor) {
        val ticket = accessor.getFirstNativeHeader("ticket")?.ifBlank { null }
        val bearer =
            accessor
                .getFirstNativeHeader("Authorization")
                ?.removePrefix("Bearer ")
                ?.ifBlank { null }

        val userDetails =
            when {
                ticket != null -> resolveByTicket(ticket)
                bearer != null -> resolveByJwt(bearer)
                else -> throw MessageDeliveryException("Missing ticket or Authorization header")
            }

        if (!userDetails.isEnabled) {
            throw MessageDeliveryException("User account is disabled")
        }

        accessor.user = WebSocketPrincipal(userDetails)
    }

    /**
     * Exact match, never a pattern: the SimpleBroker matches subscriptions with an
     * AntPathMatcher, so an unchecked wildcard under the queue prefix would match
     * every session's translated user destination and leak all conversations.
     */
    private fun authorizeSubscribe(destination: String?) {
        if (destination !in ALLOWED_SUBSCRIPTIONS) {
            throw MessageDeliveryException("Subscription to $destination is not allowed")
        }
    }

    /** Only @MessageMapping handlers, so nobody can publish straight onto a broker destination. */
    private fun authorizeSend(destination: String?) {
        if (destination == null || !destination.startsWith("/app/")) {
            throw MessageDeliveryException("Send to $destination is not allowed")
        }
    }

    private fun resolveByTicket(ticket: String): SecurityUser {
        val userId =
            wsTicketService.consume(ticket)
                ?: throw MessageDeliveryException("Invalid or expired ticket")
        return userDetailsService.loadUserById(userId)
    }

    private fun resolveByJwt(token: String): SecurityUser =
        try {
            val email = jwtService.getEmail(token)
            userDetailsService.loadUserByUsername(email) as SecurityUser
        } catch (_: InvalidTokenException) {
            throw MessageDeliveryException("Invalid or expired token")
        }

    private companion object {
        val ALLOWED_SUBSCRIPTIONS =
            setOf(
                "/user/queue/messages",
                "/user/queue/typing",
                "/user/queue/read-receipts",
                "/user/queue/presence",
                "/user/queue/notifications",
                "/user/queue/errors",
            )
    }
}
