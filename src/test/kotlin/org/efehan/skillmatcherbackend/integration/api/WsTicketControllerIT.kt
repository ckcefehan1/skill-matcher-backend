package org.efehan.skillmatcherbackend.integration.api

import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.post

@DisplayName("WS ticket endpoint Integration Tests")
class WsTicketControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    private fun createUser(): UserModel {
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        return userRepository.save(
            UserModel(
                email = "ticket@example.com",
                passwordHash = passwordEncoder.encode("Secret-Password1!"),
                firstName = "Test",
                lastName = "User",
                role = role,
            ).apply { isEnabled = true },
        )
    }

    @Test
    fun `should issue ticket when authenticated`() {
        // given
        val user = createUser()
        val token = jwtService.generateAccessToken(user)

        // when + then
        mockMvc
            .post("/api/auth/ws-ticket") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.ticket") { isString() }
                jsonPath("$.expiresInSeconds") { value(60) }
            }
    }

    @Test
    fun `should reject ticket request when not authenticated`() {
        mockMvc
            .post("/api/auth/ws-ticket") {
                withBodyRequest("")
            }.andExpect {
                status { isUnauthorized() }
            }
    }
}
