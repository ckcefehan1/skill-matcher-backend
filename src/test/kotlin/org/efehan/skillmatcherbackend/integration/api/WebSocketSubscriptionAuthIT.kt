package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.efehan.skillmatcherbackend.core.auth.WsTicketService
import org.efehan.skillmatcherbackend.core.chat.ChatMessageResponse
import org.efehan.skillmatcherbackend.fixtures.requests.ChatFixtures
import org.efehan.skillmatcherbackend.persistence.ConversationModel
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractWebSocketIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.security.crypto.password.PasswordEncoder
import java.lang.reflect.Type
import java.time.Duration
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * The SimpleBroker matches subscriptions with an AntPathMatcher, so a wildcard
 * subscription used to match every session's translated user destination and
 * leaked all conversations to any authenticated user.
 */
@DisplayName("WebSocket subscription authorization Integration Tests")
class WebSocketSubscriptionAuthIT : AbstractWebSocketIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var wsTicketService: WsTicketService

    private fun createUser(email: String): UserModel {
        val role = roleRepository.findAll().firstOrNull() ?: roleRepository.save(RoleModel("EMPLOYER", null))
        return userRepository.save(
            UserModel(
                email = email,
                passwordHash = passwordEncoder.encode("Test-Password1!"),
                firstName = "Test",
                lastName = "User",
                role = role,
            ).apply { isEnabled = true },
        )
    }

    private fun collectFrames(
        session: StompSession,
        destination: String,
    ): LinkedBlockingDeque<Any> {
        val frames = LinkedBlockingDeque<Any>()
        session.subscribe(
            destination,
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ByteArray::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    frames.add(payload ?: ByteArray(0))
                }
            },
        )
        return frames
    }

    @Test
    fun `should not deliver other users messages to a wildcard subscription`() {
        // given — mallory subscribes to everything, alice and bob chat
        val mallory = createUser("mallory@firma.de")
        val alice = createUser("alice@firma.de")
        val bob = createUser("bob@firma.de")
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        val conversation = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))

        val mallorySession = connectWithTicket(wsTicketService.issue(mallory.id))
        val stolen = collectFrames(mallorySession, "/queue/**")

        val aliceMessages = LinkedBlockingDeque<ChatMessageResponse>()
        val aliceSession = connectWithTicket(wsTicketService.issue(alice.id))
        aliceSession.subscribe(
            "/user/queue/messages",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ChatMessageResponse::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    if (payload is ChatMessageResponse) aliceMessages.add(payload)
                }
            },
        )

        // when
        val bobSession = connectWithTicket(wsTicketService.issue(bob.id))
        bobSession.send(
            "/app/chat.send",
            ChatFixtures.buildSendMessageRequest(conversationId = conversation.id, content = "Streng geheim"),
        )

        // then — alice gets her message, mallory gets nothing
        await.atMost(Duration.ofSeconds(10)).until { aliceMessages.isNotEmpty() }
        assertThat(stolen.poll(2, TimeUnit.SECONDS)).isNull()
    }

    @Test
    fun `should reject a subscription to a destination outside the allowlist`() {
        val mallory = createUser("mallory@firma.de")
        val session = connectWithTicket(wsTicketService.issue(mallory.id))

        collectFrames(session, "/queue/messages")

        await.atMost(Duration.ofSeconds(10)).until { !session.isConnected }
    }

    @Test
    fun `should reject sending straight to a broker destination`() {
        val mallory = createUser("mallory@firma.de")
        val session = connectWithTicket(wsTicketService.issue(mallory.id))

        session.send("/queue/messages", ChatFixtures.buildSendMessageRequest(conversationId = "x", content = "spoof"))

        await.atMost(Duration.ofSeconds(10)).until { !session.isConnected }
    }
}
