package org.efehan.skillmatcherbackend.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration
import org.springframework.web.socket.handler.WebSocketHandlerDecorator

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val webSocketAuthInterceptor: WebSocketAuthInterceptor,
    private val sessionRegistry: WebSocketSessionRegistry,
) : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // /topic deliberately absent: everything is per-user, a broadcast prefix is only attack surface
        registry.enableSimpleBroker("/queue")
        registry.setApplicationDestinationPrefixes("/app")
        registry.setUserDestinationPrefix("/user")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry
            .addEndpoint("/ws")
            .setAllowedOrigins("http://localhost:5173")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(webSocketAuthInterceptor)
    }

    override fun configureWebSocketTransport(registration: WebSocketTransportRegistration) {
        registration.addDecoratorFactory { handler ->
            object : WebSocketHandlerDecorator(handler) {
                override fun afterConnectionEstablished(session: WebSocketSession) {
                    sessionRegistry.register(session)
                    super.afterConnectionEstablished(session)
                }

                override fun afterConnectionClosed(
                    session: WebSocketSession,
                    closeStatus: CloseStatus,
                ) {
                    sessionRegistry.unregister(session.id)
                    super.afterConnectionClosed(session, closeStatus)
                }
            }
        }
    }
}
