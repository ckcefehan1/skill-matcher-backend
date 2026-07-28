package org.efehan.skillmatcherbackend.integration.api

import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.chat.CreateConversationRequest
import org.efehan.skillmatcherbackend.fixtures.requests.ChatFixtures
import org.efehan.skillmatcherbackend.persistence.ChatMessageModel
import org.efehan.skillmatcherbackend.persistence.ConversationModel
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant

@DisplayName("ChatController Integration Tests")
class ChatControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    @Test
    fun `should return conversations for authenticated user`() {
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
        chatMessageRepository.save(
            ChatMessageModel(
                conversation = conversation,
                sender = alice,
                content = "Hello Bob!",
                sentAt = Instant.now(),
            ),
        )

        // when & then
        mockMvc
            .get("/api/chat/conversations") {
                withAuth(tokenAlice)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].id") { value(conversation.id) }
                jsonPath("$[0].partner.firstName") { value("Bob") }
                jsonPath("$[0].partner.online") { value(false) }
                jsonPath("$[0].lastMessage.content") { value("Hello Bob!") }
                jsonPath("$[0].lastMessage.sentAt") { isNotEmpty() }
            }
    }

    @Test
    fun `should return unread count per conversation`() {
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
        chatMessageRepository.save(
            ChatMessageModel(
                conversation = conversation,
                sender = bob,
                content = "Unread from Bob",
                sentAt = Instant.now(),
            ),
        )

        // when & then — alice has one unread message, bob (the sender) has none
        mockMvc
            .get("/api/chat/conversations") {
                withAuth(tokenAlice)
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].unreadCount") { value(1) }
            }
        mockMvc
            .get("/api/chat/conversations") {
                withAuth(tokenBob)
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].unreadCount") { value(0) }
            }
    }

    @Test
    fun `should return empty list when user has no conversations`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)

        // when & then
        mockMvc
            .get("/api/chat/conversations") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
    }

    @Test
    fun `should return 401 when getting conversations without auth`() {
        // when & then
        mockMvc
            .get("/api/chat/conversations")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `should return messages for conversation member`() {
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
        val sentAt = Instant.parse("2026-01-15T10:00:00Z")
        chatMessageRepository.save(
            ChatMessageModel(conversation = conversation, sender = alice, content = "Hello", sentAt = sentAt),
        )
        chatMessageRepository.save(
            ChatMessageModel(conversation = conversation, sender = bob, content = "Hi there", sentAt = sentAt.plusSeconds(60)),
        )

        // when & then
        mockMvc
            .get("/api/chat/conversations/${conversation.id}/messages") {
                withAuth(tokenAlice)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].content") { value("Hi there") }
                jsonPath("$[0].sentAt") { isNotEmpty() }
                jsonPath("$[1].content") { value("Hello") }
            }
    }

    @Test
    fun `should support cursor-based pagination with before param`() {
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
        val baseTime = Instant.parse("2026-01-15T10:00:00Z")
        chatMessageRepository.save(
            ChatMessageModel(conversation = conversation, sender = alice, content = "Message 1", sentAt = baseTime),
        )
        chatMessageRepository.save(
            ChatMessageModel(conversation = conversation, sender = bob, content = "Message 2", sentAt = baseTime.plusSeconds(60)),
        )
        chatMessageRepository.save(
            ChatMessageModel(conversation = conversation, sender = alice, content = "Message 3", sentAt = baseTime.plusSeconds(120)),
        )

        // when & then — fetch only messages before Message 3
        mockMvc
            .get("/api/chat/conversations/${conversation.id}/messages") {
                withAuth(tokenAlice)
                param("before", baseTime.plusSeconds(120).toString())
                param("limit", "10")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].content") { value("Message 2") }
                jsonPath("$[1].content") { value("Message 1") }
            }
    }

    @Test
    fun `should return 404 when conversation does not exist`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)

        // when & then
        mockMvc
            .get("/api/chat/conversations/nonexistent-id/messages") {
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("CONVERSATION_NOT_FOUND") }
            }
    }

    @Test
    fun `should return 403 when user is not a conversation member`() {
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

        // when & then
        mockMvc
            .get("/api/chat/conversations/${conversation.id}/messages") {
                withAuth(tokenCharlie)
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("CONVERSATION_ACCESS_DENIED") }
            }
    }

    @Test
    fun `should return 401 when getting messages without auth`() {
        // when & then
        mockMvc
            .get("/api/chat/conversations/some-id/messages")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `should create new conversation and return 201`() {
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
        val request = ChatFixtures.buildCreateConversationRequest(userId = bob.id)

        // when & then
        mockMvc
            .post("/api/chat/conversations") {
                withAuth(tokenAlice)
                withBodyRequest(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { isNotEmpty() }
                jsonPath("$.partner.id") { value(bob.id) }
                jsonPath("$.partner.firstName") { value("Bob") }
                jsonPath("$.lastMessage") { isEmpty() }
            }
    }

    @Test
    fun `should return existing conversation with 200`() {
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
        val existing = conversationRepository.save(ConversationModel(userOne = first, userTwo = second))
        val request = ChatFixtures.buildCreateConversationRequest(userId = bob.id)

        // when & then
        mockMvc
            .post("/api/chat/conversations") {
                withAuth(tokenAlice)
                withBodyRequest(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(existing.id) }
                jsonPath("$.partner.id") { value(bob.id) }
            }
    }

    @Test
    fun `should return 400 when creating conversation with yourself`() {
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
        val request = ChatFixtures.buildCreateConversationRequest(userId = alice.id)

        // when & then
        mockMvc
            .post("/api/chat/conversations") {
                withAuth(tokenAlice)
                withBodyRequest(request)
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `should return 404 when partner user does not exist`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        val request = ChatFixtures.buildCreateConversationRequest(userId = "nonexistent-id")

        // when & then
        mockMvc
            .post("/api/chat/conversations") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("USER_NOT_FOUND") }
            }
    }

    @Test
    fun `should return 400 when userId is blank`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "alice@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Alice",
                    lastName = "Schmidt",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)

        // when & then
        mockMvc
            .post("/api/chat/conversations") {
                withAuth(token)
                withBodyRequest(mapOf("userId" to "  "))
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `should return 401 when creating conversation without auth`() {
        // when & then
        mockMvc
            .post("/api/chat/conversations") {
                withBodyRequest(CreateConversationRequest(userId = "some-id"))
            }.andExpect {
                status { isUnauthorized() }
            }
    }
}
