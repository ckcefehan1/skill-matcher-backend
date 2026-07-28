package org.efehan.skillmatcherbackend.integration.api

import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.persistence.NotificationModel
import org.efehan.skillmatcherbackend.persistence.NotificationType
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@DisplayName("Notification Controller Integration Tests")
class NotificationControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    private fun createUser(email: String): UserModel {
        val role = roleRepository.findAll().firstOrNull() ?: roleRepository.save(RoleModel("EMPLOYER", null))
        return userRepository.save(
            UserModel(
                email = email,
                passwordHash = passwordEncoder.encode("Secret-Password1!"),
                firstName = "Test",
                lastName = "User",
                role = role,
            ).apply { isEnabled = true },
        )
    }

    private fun createNotification(
        user: UserModel,
        referenceId: String,
    ): NotificationModel =
        notificationRepository.save(
            NotificationModel(
                user = user,
                type = NotificationType.CHAT_MESSAGE,
                title = "Nachricht von Sender",
                body = "Hallo",
                referenceId = referenceId,
            ),
        )

    @Test
    fun `should list own notifications newest first`() {
        // given
        val user = createUser("n1@example.com")
        val other = createUser("n2@example.com")
        createNotification(user, "msg-1")
        createNotification(user, "msg-2")
        createNotification(other, "msg-3")
        val token = jwtService.generateAccessToken(user)

        // when + then
        mockMvc
            .get("/api/notifications") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].type") { value("CHAT_MESSAGE") }
                jsonPath("$[0].readAt") { doesNotExist() }
            }
    }

    @Test
    fun `should return unread count`() {
        // given
        val user = createUser("n1@example.com")
        createNotification(user, "msg-1")
        createNotification(user, "msg-2")
        val token = jwtService.generateAccessToken(user)

        // when + then
        mockMvc
            .get("/api/notifications/unread-count") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.count") { value(2) }
            }
    }

    @Test
    fun `should mark notification read`() {
        // given
        val user = createUser("n1@example.com")
        val notification = createNotification(user, "msg-1")
        val token = jwtService.generateAccessToken(user)

        // when + then
        mockMvc
            .post("/api/notifications/${notification.id}/read") {
                withAuth(token)
            }.andExpect {
                status { isNoContent() }
            }

        mockMvc
            .get("/api/notifications/unread-count") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.count") { value(0) }
            }
    }

    @Test
    fun `should return 404 when marking foreign notification read`() {
        // given
        val user = createUser("n1@example.com")
        val other = createUser("n2@example.com")
        val foreign = createNotification(other, "msg-1")
        val token = jwtService.generateAccessToken(user)

        // when + then
        mockMvc
            .post("/api/notifications/${foreign.id}/read") {
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `should mark all notifications read`() {
        // given
        val user = createUser("n1@example.com")
        createNotification(user, "msg-1")
        createNotification(user, "msg-2")
        val token = jwtService.generateAccessToken(user)

        // when + then
        mockMvc
            .post("/api/notifications/read-all") {
                withAuth(token)
            }.andExpect {
                status { isNoContent() }
            }

        mockMvc
            .get("/api/notifications/unread-count") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.count") { value(0) }
            }
    }

    @Test
    fun `should require authentication`() {
        mockMvc
            .get("/api/notifications")
            .andExpect {
                status { isUnauthorized() }
            }
    }
}
