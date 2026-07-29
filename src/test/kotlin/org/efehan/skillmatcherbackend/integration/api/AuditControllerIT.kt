package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.fixtures.requests.AdminUserFixtures
import org.efehan.skillmatcherbackend.persistence.AuditAction
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@DisplayName("Audit Controller Integration Tests")
class AuditControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    private fun createAdmin(): UserModel {
        val role = roleRepository.save(RoleModel("ADMIN", null))
        return userRepository.save(
            UserModel(
                email = "admin@firma.de",
                passwordHash = passwordEncoder.encode("Admin-Password1!"),
                firstName = "Admin",
                lastName = "User",
                role = role,
            ).apply { isEnabled = true },
        )
    }

    @Test
    fun `admin action lands in the audit log`() {
        // given
        val admin = createAdmin()
        val token = jwtService.generateAccessToken(admin)
        roleRepository.save(RoleModel("EMPLOYER", null))

        // when
        mockMvc
            .post("/api/admin/users") {
                withAuth(token)
                withBodyRequest(AdminUserFixtures.buildCreateUserRequest())
            }.andExpect { status { isCreated() } }

        // then
        mockMvc
            .get("/api/admin/audit-logs") { withAuth(token) }
            .andExpect {
                status { isOk() }
                jsonPath("$.content[0].action") { value(AuditAction.USER_CREATED.name) }
                jsonPath("$.content[0].actorEmail") { value("admin@firma.de") }
                jsonPath("$.content[0].detail") { value("max.mustermann@firma.de") }
            }
    }

    @Test
    fun `failed login is recorded even though the request fails`() {
        // given
        val user = createAdmin()

        // when
        mockMvc
            .post("/api/auth/login") {
                withBodyRequest(mapOf("email" to user.email, "password" to "Wrong-Password1!"))
            }.andExpect { status { isUnauthorized() } }

        // then — the entry only survives because record() runs in its own transaction
        assertThat(auditLogRepository.findAll().map { it.action }).contains(AuditAction.LOGIN_FAILED)
    }

    @Test
    fun `audit log is not readable without the admin role`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "employer@firma.de",
                    passwordHash = passwordEncoder.encode("Secret-Password1!"),
                    firstName = "Emp",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )

        // when & then
        mockMvc
            .get("/api/admin/audit-logs") { withAuth(jwtService.generateAccessToken(user)) }
            .andExpect { status { isForbidden() } }
    }
}
