package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.fixtures.requests.MySkillFixtures
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.SkillModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserSkillModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@DisplayName("MySkillController Integration Tests")
class MySkillControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    @Test
    fun `should add a new skill and return 201`() {
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
        val request = MySkillFixtures.buildAddSkillRequest()

        // when & then
        mockMvc
            .post("/api/me/skills") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { isNotEmpty() }
                jsonPath("$.name") { value("kotlin") }
                jsonPath("$.level") { value(request.level) }
            }
    }

    @Test
    fun `should update existing skill and return 200`() {
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
        val skill = skillRepository.save(SkillModel(name = "kotlin"))
        userSkillRepository.save(UserSkillModel(user = user, skill = skill, level = 2))
        val request = MySkillFixtures.buildAddSkillRequest(name = "Kotlin", level = 5)

        // when & then
        mockMvc
            .post("/api/me/skills") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("kotlin") }
                jsonPath("$.level") { value(5) }
            }
    }

    @Test
    fun `should return 400 when level is below 1`() {
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
        val request = MySkillFixtures.buildAddSkillRequest(level = 0)

        // when & then
        mockMvc
            .post("/api/me/skills") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `should return 400 when level is above 5`() {
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
        val request = MySkillFixtures.buildAddSkillRequest(level = 6)

        // when & then
        mockMvc
            .post("/api/me/skills") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `should return 400 when name is blank`() {
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
        val request = MySkillFixtures.buildAddSkillRequest(name = "  ", level = 3)

        // when & then
        mockMvc
            .post("/api/me/skills") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `should return all skills for authenticated user`() {
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
        val skill1 = skillRepository.save(SkillModel(name = "kotlin"))
        val skill2 = skillRepository.save(SkillModel(name = "java"))
        userSkillRepository.save(UserSkillModel(user = user, skill = skill1, level = 4))
        userSkillRepository.save(UserSkillModel(user = user, skill = skill2, level = 3))

        // when & then
        mockMvc
            .get("/api/me/skills") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].id") { isNotEmpty() }
                jsonPath("$[0].name") { isNotEmpty() }
                jsonPath("$[0].level") { isNotEmpty() }
            }
    }

    @Test
    fun `should return empty list when user has no skills`() {
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
            .get("/api/me/skills") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
    }

    @Test
    fun `should not return skills of other users`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user1 =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val user2 =
            userRepository.save(
                UserModel(
                    email = "other@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Other",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token1 = jwtService.generateAccessToken(user1)
        val skill = skillRepository.save(SkillModel(name = "kotlin"))
        userSkillRepository.save(UserSkillModel(user = user1, skill = skill, level = 4))
        userSkillRepository.save(UserSkillModel(user = user2, skill = skill, level = 2))

        // when & then
        mockMvc
            .get("/api/me/skills") {
                withAuth(token1)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].level") { value(4) }
            }
    }

    @Test
    fun `should delete skill and return 204`() {
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
        val skill = skillRepository.save(SkillModel(name = "kotlin"))
        val userSkill = userSkillRepository.save(UserSkillModel(user = user, skill = skill, level = 3))

        // when & then
        mockMvc
            .delete("/api/me/skills/${userSkill.id}") {
                withAuth(token)
            }.andExpect {
                status { isNoContent() }
            }

        assertThat(userSkillRepository.findById(userSkill.id)).isEmpty
    }

    @Test
    fun `should return 404 when deleting nonexistent skill`() {
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
            .delete("/api/me/skills/nonexistent-id") {
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("USER_SKILL_NOT_FOUND") }
            }
    }

    @Test
    fun `should return 403 when deleting another users skill`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user1 =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val user2 =
            userRepository.save(
                UserModel(
                    email = "other@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Other",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token1 = jwtService.generateAccessToken(user1)
        val skill = skillRepository.save(SkillModel(name = "kotlin"))
        val otherUsersSkill = userSkillRepository.save(UserSkillModel(user = user2, skill = skill, level = 3))

        // when & then
        mockMvc
            .delete("/api/me/skills/${otherUsersSkill.id}") {
                withAuth(token1)
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("USER_SKILL_ACCESS_DENIED") }
            }
    }

    @Test
    fun `should return 401 when not authenticated`() {
        // when & then
        mockMvc
            .get("/api/me/skills")
            .andExpect {
                status { isUnauthorized() }
            }

        mockMvc
            .post("/api/me/skills") {
                withBodyRequest(MySkillFixtures.buildAddSkillRequest())
            }.andExpect {
                status { isUnauthorized() }
            }

        // CSRF check rejects unauthenticated mutations before authentication: 403
        mockMvc
            .delete("/api/me/skills/some-id")
            .andExpect {
                status { isForbidden() }
            }
    }
}
