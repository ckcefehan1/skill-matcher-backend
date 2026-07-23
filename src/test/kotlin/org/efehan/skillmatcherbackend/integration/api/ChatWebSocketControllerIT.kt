package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilNotNull
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.chat.ChatErrorResponse
import org.efehan.skillmatcherbackend.core.chat.ChatMessageResponse
import org.efehan.skillmatcherbackend.core.chat.MarkConversationReadRequest
import org.efehan.skillmatcherbackend.core.chat.PresenceResponse
import org.efehan.skillmatcherbackend.core.chat.ReadReceiptResponse
import org.efehan.skillmatcherbackend.core.chat.SendMessageRequest
import org.efehan.skillmatcherbackend.core.chat.TypingRequest
import org.efehan.skillmatcherbackend.core.chat.TypingResponse
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
import org.springframework.security.crypto.password.PasswordEncoder
import java.lang.reflect.Type
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

@DisplayName("ChatWebSocketController Integration Tests")
class ChatWebSocketControllerIT : AbstractWebSocketIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    @Test
    fun `should deliver message to both sender and recipient`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val alice =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val bob =
            userRepository.save(
                UserModel(
                    email = "bob@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Bob",
                    lastName = "Mueller",
                    role = role,
                ).apply { isEnabled = true },
            )
        val tokenAlice = jwtService.generateAccessToken(alice)
        val tokenBob = jwtService.generateAccessToken(bob)
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        val conversation = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))

        val aliceMessages = LinkedBlockingDeque<ChatMessageResponse>()
        val bobMessages = LinkedBlockingDeque<ChatMessageResponse>()

        val aliceSession = connectWithAuth(tokenAlice)
        val bobSession = connectWithAuth(tokenBob)

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
        bobSession.subscribe(
            "/user/queue/messages",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ChatMessageResponse::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    if (payload is ChatMessageResponse) bobMessages.add(payload)
                }
            },
        )

        // when
        val request = ChatFixtures.buildSendMessageRequest(conversationId = conversation.id, content = "Hello Bob!")
        await.atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200)).untilAsserted {
            aliceSession.send("/app/chat.send", request)
            val aliceMsg = aliceMessages.poll(1, TimeUnit.SECONDS)
            assertThat(aliceMsg).isNotNull()
        }

        // drain extra sends and get fresh results
        aliceMessages.clear()
        bobMessages.clear()
        aliceSession.send("/app/chat.send", request)

        // then
        val aliceMsg = await.atMost(Duration.ofSeconds(5)).untilNotNull { aliceMessages.poll(1, TimeUnit.SECONDS) }
        val bobMsg = await.atMost(Duration.ofSeconds(5)).untilNotNull { bobMessages.poll(1, TimeUnit.SECONDS) }

        assertThat(aliceMsg.content).isEqualTo("Hello Bob!")
        assertThat(aliceMsg.senderId).isEqualTo(alice.id)
        assertThat(aliceMsg.conversationId).isEqualTo(conversation.id)

        assertThat(bobMsg.content).isEqualTo("Hello Bob!")
        assertThat(bobMsg.senderId).isEqualTo(alice.id)
        assertThat(bobMsg.conversationId).isEqualTo(conversation.id)
    }

    @Test
    fun `should persist message with correct sentAt`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val alice =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val bob =
            userRepository.save(
                UserModel(
                    email = "bob@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Bob",
                    lastName = "Mueller",
                    role = role,
                ).apply { isEnabled = true },
            )
        val tokenAlice = jwtService.generateAccessToken(alice)
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        val conversation = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))

        val aliceMessages = LinkedBlockingDeque<ChatMessageResponse>()
        val aliceSession = connectWithAuth(tokenAlice)
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
        val request = ChatFixtures.buildSendMessageRequest(conversationId = conversation.id, content = "Timestamp test")
        aliceSession.send("/app/chat.send", request)

        // then
        val msg = await.atMost(Duration.ofSeconds(10)).untilNotNull { aliceMessages.poll(1, TimeUnit.SECONDS) }

        val dbMessage = chatMessageRepository.findAll().last()
        assertThat(dbMessage.content).isEqualTo("Timestamp test")
        assertThat(dbMessage.sentAt).isNotNull()
        assertThat(msg.sentAt.truncatedTo(ChronoUnit.MILLIS))
            .isEqualTo(dbMessage.sentAt.truncatedTo(ChronoUnit.MILLIS))
    }

    @Test
    fun `should not broadcast when validation fails`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val alice =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val bob =
            userRepository.save(
                UserModel(
                    email = "bob@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Bob",
                    lastName = "Mueller",
                    role = role,
                ).apply { isEnabled = true },
            )
        val tokenAlice = jwtService.generateAccessToken(alice)
        val tokenBob = jwtService.generateAccessToken(bob)
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        conversationRepository.save(ConversationModel(userOne = first, userTwo = second))

        val bobMessages = LinkedBlockingDeque<ChatMessageResponse>()
        val aliceSession = connectWithAuth(tokenAlice)
        val bobSession = connectWithAuth(tokenBob)

        bobSession.subscribe(
            "/user/queue/messages",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ChatMessageResponse::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    if (payload is ChatMessageResponse) bobMessages.add(payload)
                }
            },
        )

        // first send a valid message to confirm subscription is active
        val validConversation = conversationRepository.findAll().first()
        aliceSession.send(
            "/app/chat.send",
            ChatFixtures.buildSendMessageRequest(conversationId = validConversation.id, content = "warmup"),
        )
        await.atMost(Duration.ofSeconds(10)).untilNotNull { bobMessages.poll(1, TimeUnit.SECONDS) }
        bobMessages.clear()
        chatMessageRepository.deleteAll()

        // when — send with blank conversationId and content
        val invalidRequest = SendMessageRequest(conversationId = "", content = "")
        aliceSession.send("/app/chat.send", invalidRequest)

        // then
        val msg = bobMessages.poll(2, TimeUnit.SECONDS)
        assertThat(msg).isNull()
        assertThat(chatMessageRepository.findAll()).isEmpty()
    }

    @Test
    fun `should not deliver message to non-member`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val alice =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val bob =
            userRepository.save(
                UserModel(
                    email = "bob@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Bob",
                    lastName = "Mueller",
                    role = role,
                ).apply { isEnabled = true },
            )
        val charlie =
            userRepository.save(
                UserModel(
                    email = "charlie@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Charlie",
                    lastName = "Weber",
                    role = role,
                ).apply { isEnabled = true },
            )
        val tokenAlice = jwtService.generateAccessToken(alice)
        val tokenCharlie = jwtService.generateAccessToken(charlie)
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        val conversation = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))

        val charlieMessages = LinkedBlockingDeque<ChatMessageResponse>()
        val aliceMessages = LinkedBlockingDeque<ChatMessageResponse>()
        val aliceSession = connectWithAuth(tokenAlice)
        val charlieSession = connectWithAuth(tokenCharlie)

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
        charlieSession.subscribe(
            "/user/queue/messages",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ChatMessageResponse::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    if (payload is ChatMessageResponse) charlieMessages.add(payload)
                }
            },
        )

        // confirm subscriptions are active by sending and receiving a message
        aliceSession.send(
            "/app/chat.send",
            ChatFixtures.buildSendMessageRequest(conversationId = conversation.id, content = "warmup"),
        )
        await.atMost(Duration.ofSeconds(10)).untilNotNull { aliceMessages.poll(1, TimeUnit.SECONDS) }
        aliceMessages.clear()

        // when
        val request = ChatFixtures.buildSendMessageRequest(conversationId = conversation.id, content = "Secret message")
        aliceSession.send("/app/chat.send", request)

        // confirm alice received it (subscription is working)
        await.atMost(Duration.ofSeconds(5)).untilNotNull { aliceMessages.poll(1, TimeUnit.SECONDS) }

        // then — charlie should NOT have received anything
        val msg = charlieMessages.poll(2, TimeUnit.SECONDS)
        assertThat(msg).isNull()
    }

    @Test
    fun `should send error frame when message content exceeds 5000 characters`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val alice =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val bob =
            userRepository.save(
                UserModel(
                    email = "bob@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Bob",
                    lastName = "Mueller",
                    role = role,
                ).apply { isEnabled = true },
            )
        val tokenAlice = jwtService.generateAccessToken(alice)
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        val conversation = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))

        val aliceErrors = LinkedBlockingDeque<ChatErrorResponse>()
        val aliceSession = connectWithAuth(tokenAlice)
        aliceSession.subscribe(
            "/user/queue/errors",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ChatErrorResponse::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    if (payload is ChatErrorResponse) aliceErrors.add(payload)
                }
            },
        )

        // when
        val request =
            ChatFixtures.buildSendMessageRequest(conversationId = conversation.id, content = "x".repeat(5001))
        aliceSession.send("/app/chat.send", request)

        // then
        val error = await.atMost(Duration.ofSeconds(10)).untilNotNull { aliceErrors.poll(1, TimeUnit.SECONDS) }
        assertThat(error.errorCode).isEqualTo("VALIDATION_ERROR")
        assertThat(error.details).anyMatch { it.contains("content") }
        assertThat(chatMessageRepository.findAll()).isEmpty()
    }

    @Test
    fun `should send error frame when conversation does not exist`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val alice =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val tokenAlice = jwtService.generateAccessToken(alice)

        val aliceErrors = LinkedBlockingDeque<ChatErrorResponse>()
        val aliceSession = connectWithAuth(tokenAlice)
        aliceSession.subscribe(
            "/user/queue/errors",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ChatErrorResponse::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    if (payload is ChatErrorResponse) aliceErrors.add(payload)
                }
            },
        )

        // when
        aliceSession.send(
            "/app/chat.send",
            ChatFixtures.buildSendMessageRequest(conversationId = "nonexistent-id", content = "Hello?"),
        )

        // then
        val error = await.atMost(Duration.ofSeconds(10)).untilNotNull { aliceErrors.poll(1, TimeUnit.SECONDS) }
        assertThat(error.errorCode).isEqualTo("CONVERSATION_NOT_FOUND")
    }

    @Test
    fun `should mark messages read and push receipt to partner`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val alice =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val bob =
            userRepository.save(
                UserModel(
                    email = "bob@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Bob",
                    lastName = "Mueller",
                    role = role,
                ).apply { isEnabled = true },
            )
        val tokenAlice = jwtService.generateAccessToken(alice)
        val tokenBob = jwtService.generateAccessToken(bob)
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        val conversation = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))

        val aliceReceipts = LinkedBlockingDeque<ReadReceiptResponse>()
        val bobMessages = LinkedBlockingDeque<ChatMessageResponse>()
        val aliceSession = connectWithAuth(tokenAlice)
        val bobSession = connectWithAuth(tokenBob)

        aliceSession.subscribe(
            "/user/queue/read-receipts",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ReadReceiptResponse::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    if (payload is ReadReceiptResponse) aliceReceipts.add(payload)
                }
            },
        )
        bobSession.subscribe(
            "/user/queue/messages",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ChatMessageResponse::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    if (payload is ChatMessageResponse) bobMessages.add(payload)
                }
            },
        )

        // alice sends a message; bob receiving it confirms all subscriptions are active
        aliceSession.send(
            "/app/chat.send",
            ChatFixtures.buildSendMessageRequest(conversationId = conversation.id, content = "Hello Bob!"),
        )
        await.atMost(Duration.ofSeconds(10)).untilNotNull { bobMessages.poll(1, TimeUnit.SECONDS) }

        // when — bob marks the conversation as read
        bobSession.send("/app/chat.read", MarkConversationReadRequest(conversationId = conversation.id))

        // then — alice (the sender) receives a read receipt
        val receipt = await.atMost(Duration.ofSeconds(10)).untilNotNull { aliceReceipts.poll(1, TimeUnit.SECONDS) }
        assertThat(receipt.conversationId).isEqualTo(conversation.id)
        assertThat(receipt.readBy).isEqualTo(bob.id)
        assertThat(receipt.readAt).isNotNull()

        val messages = chatMessageRepository.findAll()
        assertThat(messages).hasSize(1)
        assertThat(messages[0].readAt).isNotNull()
    }

    @Test
    fun `should send error frame when non-member marks conversation read`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val alice =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val bob =
            userRepository.save(
                UserModel(
                    email = "bob@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Bob",
                    lastName = "Mueller",
                    role = role,
                ).apply { isEnabled = true },
            )
        val charlie =
            userRepository.save(
                UserModel(
                    email = "charlie@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Charlie",
                    lastName = "Weber",
                    role = role,
                ).apply { isEnabled = true },
            )
        val tokenCharlie = jwtService.generateAccessToken(charlie)
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        val conversation = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))

        val charlieErrors = LinkedBlockingDeque<ChatErrorResponse>()
        val charlieSession = connectWithAuth(tokenCharlie)
        charlieSession.subscribe(
            "/user/queue/errors",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ChatErrorResponse::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    if (payload is ChatErrorResponse) charlieErrors.add(payload)
                }
            },
        )

        // when
        charlieSession.send("/app/chat.read", MarkConversationReadRequest(conversationId = conversation.id))

        // then
        val error = await.atMost(Duration.ofSeconds(10)).untilNotNull { charlieErrors.poll(1, TimeUnit.SECONDS) }
        assertThat(error.errorCode).isEqualTo("CONVERSATION_ACCESS_DENIED")
    }

    @Test
    fun `should forward typing indicator to partner`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val alice =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val bob =
            userRepository.save(
                UserModel(
                    email = "bob@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Bob",
                    lastName = "Mueller",
                    role = role,
                ).apply { isEnabled = true },
            )
        val tokenAlice = jwtService.generateAccessToken(alice)
        val tokenBob = jwtService.generateAccessToken(bob)
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        val conversation = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))

        val aliceMessages = LinkedBlockingDeque<ChatMessageResponse>()
        val aliceTyping = LinkedBlockingDeque<TypingResponse>()
        val aliceSession = connectWithAuth(tokenAlice)
        val bobSession = connectWithAuth(tokenBob)

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
        aliceSession.subscribe(
            "/user/queue/typing",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = TypingResponse::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    if (payload is TypingResponse) aliceTyping.add(payload)
                }
            },
        )

        // bob sends a message; alice receiving it confirms her subscriptions are active
        bobSession.send(
            "/app/chat.send",
            ChatFixtures.buildSendMessageRequest(conversationId = conversation.id, content = "Hi Alice!"),
        )
        await.atMost(Duration.ofSeconds(10)).untilNotNull { aliceMessages.poll(1, TimeUnit.SECONDS) }

        // when — bob starts typing
        bobSession.send("/app/chat.typing", TypingRequest(conversationId = conversation.id))

        // then
        val typing = await.atMost(Duration.ofSeconds(10)).untilNotNull { aliceTyping.poll(1, TimeUnit.SECONDS) }
        assertThat(typing.conversationId).isEqualTo(conversation.id)
        assertThat(typing.userId).isEqualTo(bob.id)
    }

    @Test
    fun `should send error frame when non-member sends typing indicator`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val alice =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val bob =
            userRepository.save(
                UserModel(
                    email = "bob@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Bob",
                    lastName = "Mueller",
                    role = role,
                ).apply { isEnabled = true },
            )
        val charlie =
            userRepository.save(
                UserModel(
                    email = "charlie@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Charlie",
                    lastName = "Weber",
                    role = role,
                ).apply { isEnabled = true },
            )
        val tokenCharlie = jwtService.generateAccessToken(charlie)
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        val conversation = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))

        val charlieErrors = LinkedBlockingDeque<ChatErrorResponse>()
        val charlieSession = connectWithAuth(tokenCharlie)
        charlieSession.subscribe(
            "/user/queue/errors",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ChatErrorResponse::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    if (payload is ChatErrorResponse) charlieErrors.add(payload)
                }
            },
        )

        // when
        charlieSession.send("/app/chat.typing", TypingRequest(conversationId = conversation.id))

        // then
        val error = await.atMost(Duration.ofSeconds(10)).untilNotNull { charlieErrors.poll(1, TimeUnit.SECONDS) }
        assertThat(error.errorCode).isEqualTo("CONVERSATION_ACCESS_DENIED")
    }

    @Test
    fun `should notify partner when user connects and disconnects`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val alice =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val bob =
            userRepository.save(
                UserModel(
                    email = "bob@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Bob",
                    lastName = "Mueller",
                    role = role,
                ).apply { isEnabled = true },
            )
        val tokenAlice = jwtService.generateAccessToken(alice)
        val tokenBob = jwtService.generateAccessToken(bob)
        val (first, second) = if (alice.id < bob.id) alice to bob else bob to alice
        val conversation = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))

        val aliceMessages = LinkedBlockingDeque<ChatMessageResponse>()
        val alicePresence = LinkedBlockingDeque<PresenceResponse>()
        val aliceSession = connectWithAuth(tokenAlice)

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
        aliceSession.subscribe(
            "/user/queue/presence",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = PresenceResponse::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    if (payload is PresenceResponse) alicePresence.add(payload)
                }
            },
        )

        // alice sends a message to herself via the conversation; receiving it confirms subscriptions are active
        aliceSession.send(
            "/app/chat.send",
            ChatFixtures.buildSendMessageRequest(conversationId = conversation.id, content = "warmup"),
        )
        await.atMost(Duration.ofSeconds(10)).untilNotNull { aliceMessages.poll(1, TimeUnit.SECONDS) }

        // when — bob comes online
        val bobSession = connectWithAuth(tokenBob)

        // then
        val online = await.atMost(Duration.ofSeconds(10)).untilNotNull { alicePresence.poll(1, TimeUnit.SECONDS) }
        assertThat(online.userId).isEqualTo(bob.id)
        assertThat(online.online).isTrue()

        // when — bob disconnects
        bobSession.disconnect()

        // then
        val offline = await.atMost(Duration.ofSeconds(10)).untilNotNull { alicePresence.poll(1, TimeUnit.SECONDS) }
        assertThat(offline.userId).isEqualTo(bob.id)
        assertThat(offline.online).isFalse()
    }
}
