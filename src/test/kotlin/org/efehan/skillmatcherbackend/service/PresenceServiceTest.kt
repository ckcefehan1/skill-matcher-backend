package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.config.WebSocketPrincipal
import org.efehan.skillmatcherbackend.core.auth.SecurityUser
import org.efehan.skillmatcherbackend.core.chat.PresenceResponse
import org.efehan.skillmatcherbackend.core.chat.PresenceService
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.ConversationRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.messaging.Message
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.support.MessageBuilder
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.security.Principal

@ExtendWith(MockKExtension::class)
@DisplayName("PresenceService Unit Tests")
class PresenceServiceTest {
    @MockK
    private lateinit var conversationRepo: ConversationRepository

    @MockK
    private lateinit var messagingTemplate: SimpMessagingTemplate

    @InjectMockKs
    private lateinit var presenceService: PresenceService

    @Test
    fun `isOnline returns false for unknown user`() {
        assertThat(presenceService.isOnline("unknown-id")).isFalse()
    }

    @Test
    fun `onConnected marks user online and notifies partners`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        every { conversationRepo.findPartnerIds(userA) } returns listOf(userB.id)
        every { messagingTemplate.convertAndSendToUser(any<String>(), any(), any()) } returns Unit

        // when
        presenceService.onConnected(connectedEvent(userA, "session-1"))

        // then
        assertThat(presenceService.isOnline(userA.id)).isTrue()
        verify(exactly = 1) {
            messagingTemplate.convertAndSendToUser(
                userB.id,
                "/queue/presence",
                PresenceResponse(userId = userA.id, online = true),
            )
        }
    }

    @Test
    fun `onConnected does not notify again for additional session`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        every { conversationRepo.findPartnerIds(userA) } returns listOf(userB.id)
        every { messagingTemplate.convertAndSendToUser(any<String>(), any(), any()) } returns Unit

        // when — same user opens a second tab
        presenceService.onConnected(connectedEvent(userA, "session-1"))
        presenceService.onConnected(connectedEvent(userA, "session-2"))

        // then — partner is notified only once
        verify(exactly = 1) { messagingTemplate.convertAndSendToUser(any<String>(), "/queue/presence", any()) }
    }

    @Test
    fun `onDisconnected keeps user online while another session remains`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        every { conversationRepo.findPartnerIds(userA) } returns listOf(userB.id)
        every { messagingTemplate.convertAndSendToUser(any<String>(), any(), any()) } returns Unit
        presenceService.onConnected(connectedEvent(userA, "session-1"))
        presenceService.onConnected(connectedEvent(userA, "session-2"))

        // when — one tab closes
        presenceService.onDisconnected(disconnectedEvent(userA, "session-1"))

        // then
        assertThat(presenceService.isOnline(userA.id)).isTrue()
        verify(exactly = 0) {
            messagingTemplate.convertAndSendToUser(any<String>(), "/queue/presence", match<PresenceResponse> { !it.online })
        }
    }

    @Test
    fun `onDisconnected notifies partners when last session disconnects`() {
        // given
        val userA = UserBuilder().build()
        val userB = UserBuilder().build(email = "bob@firma.de", firstName = "Bob", lastName = "Mueller")
        every { conversationRepo.findPartnerIds(userA) } returns listOf(userB.id)
        every { messagingTemplate.convertAndSendToUser(any<String>(), any(), any()) } returns Unit
        presenceService.onConnected(connectedEvent(userA, "session-1"))

        // when
        presenceService.onDisconnected(disconnectedEvent(userA, "session-1"))

        // then
        assertThat(presenceService.isOnline(userA.id)).isFalse()
        verify(exactly = 1) {
            messagingTemplate.convertAndSendToUser(
                userB.id,
                "/queue/presence",
                PresenceResponse(userId = userA.id, online = false),
            )
        }
    }

    @Test
    fun `events without websocket principal are ignored`() {
        // when
        presenceService.onConnected(connectedEvent(null, "session-1"))
        presenceService.onDisconnected(disconnectedEvent(null, "session-1"))

        // then
        verify(exactly = 0) { conversationRepo.findPartnerIds(any()) }
        verify(exactly = 0) { messagingTemplate.convertAndSendToUser(any<String>(), any(), any()) }
    }

    private fun connectedEvent(
        user: UserModel?,
        sessionId: String,
    ): SessionConnectedEvent = SessionConnectedEvent(this, stompMessage(sessionId), user?.let { principal(it) })

    private fun disconnectedEvent(
        user: UserModel?,
        sessionId: String,
    ): SessionDisconnectEvent =
        SessionDisconnectEvent(this, stompMessage(sessionId), sessionId, CloseStatus.NORMAL, user?.let { principal(it) })

    private fun stompMessage(sessionId: String): Message<ByteArray> =
        MessageBuilder
            .withPayload(ByteArray(0))
            .setHeader(SimpMessageHeaderAccessor.SESSION_ID_HEADER, sessionId)
            .build()

    private fun principal(user: UserModel): Principal = WebSocketPrincipal(SecurityUser(user))
}
