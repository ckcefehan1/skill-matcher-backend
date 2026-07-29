package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilNotNull
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.auth.WsTicketService
import org.efehan.skillmatcherbackend.core.chat.ChatMessageResponse
import org.efehan.skillmatcherbackend.fixtures.requests.ChatFixtures
import org.efehan.skillmatcherbackend.persistence.ConversationModel
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractWebSocketIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.security.crypto.password.PasswordEncoder
import java.lang.reflect.Type
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

@DisplayName("WebSocket ticket auth Integration Tests")
class WsTicketAuthIT : AbstractWebSocketIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

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

    @Test
    fun `should connect and receive messages with ticket auth`() {
        // given
        val alice = createUser("alice@firma.de")
        val bob = createUser("bob@firma.de")
        val ticketAlice = wsTicketService.issue(alice.id)
        val tokenBob = jwtService.generateAccessToken(bob)
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        val conversation = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))

        val aliceMessages = LinkedBlockingDeque<ChatMessageResponse>()
        val aliceSession = connectWithTicket(ticketAlice)
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

        // when — bob (bearer) sends to alice (ticket)
        val bobSession = connectWithAuth(tokenBob)
        bobSession.send(
            "/app/chat.send",
            ChatFixtures.buildSendMessageRequest(conversationId = conversation.id, content = "Hallo Ticket!"),
        )

        // then
        val msg = await.atMost(Duration.ofSeconds(10)).untilNotNull { aliceMessages.poll(1, TimeUnit.SECONDS) }
        assertThat(msg.content).isEqualTo("Hallo Ticket!")
        assertThat(msg.senderId).isEqualTo(bob.id)
    }

    @Test
    fun `should reject reused ticket`() {
        // given
        val alice = createUser("alice@firma.de")
        val ticket = wsTicketService.issue(alice.id)
        connectWithTicket(ticket)

        // when + then — second connect with the same (consumed) ticket fails
        assertThrows<ExecutionException> {
            connectWithTicket(ticket)
        }
    }

    @Test
    fun `should reject unknown ticket`() {
        assertThrows<ExecutionException> {
            connectWithTicket("not-a-real-ticket")
        }
    }
}
