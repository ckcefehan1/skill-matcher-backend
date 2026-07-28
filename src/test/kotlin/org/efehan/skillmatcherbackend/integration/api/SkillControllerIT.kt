package org.efehan.skillmatcherbackend.integration.api

import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.SkillModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.get

@DisplayName("SkillController Integration Tests")
class SkillControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    @Test
    fun `should return all skills`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)
        skillRepository.save(SkillModel(name = "kotlin"))
        skillRepository.save(SkillModel(name = "java"))
        skillRepository.save(SkillModel(name = "spring"))

        // when & then
        mockMvc
            .get("/api/skills") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(3) }
                jsonPath("$.content[0].id") { isNotEmpty() }
                jsonPath("$.content[0].name") { isNotEmpty() }
            }
    }

    @Test
    fun `should return empty list when no skills exist`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(user)

        // when & then
        mockMvc
            .get("/api/skills") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(0) }
            }
    }

    @Test
    fun `should return 401 when not authenticated`() {
        // when & then
        mockMvc
            .get("/api/skills")
            .andExpect {
                status { isUnauthorized() }
            }
    }
}
