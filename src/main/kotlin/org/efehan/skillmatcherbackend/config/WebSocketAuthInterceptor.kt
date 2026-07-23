package org.efehan.skillmatcherbackend.config

import org.efehan.skillmatcherbackend.core.auth.CustomUserDetailsService
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.auth.SecurityUser
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
) : ChannelInterceptor {
    override fun preSend(
        message: Message<*>,
        channel: MessageChannel,
    ): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)

        if (accessor != null && accessor.command == StompCommand.CONNECT) {
            val token =
                accessor
                    .getFirstNativeHeader("Authorization")
                    ?.removePrefix("Bearer ")
                    ?.ifBlank { null }
                    ?: throw MessageDeliveryException("Missing or empty Authorization header")

            try {
                val email = jwtService.getEmail(token)
                val userDetails = userDetailsService.loadUserByUsername(email) as SecurityUser

                if (!userDetails.isEnabled) {
                    throw MessageDeliveryException("User account is disabled")
                }

                accessor.user = WebSocketPrincipal(userDetails)
            } catch (_: InvalidTokenException) {
                throw MessageDeliveryException("Invalid or expired token")
            }
        }
        return message
    }
}
