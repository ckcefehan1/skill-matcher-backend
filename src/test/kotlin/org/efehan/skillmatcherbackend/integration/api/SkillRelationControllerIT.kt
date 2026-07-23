package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.fixtures.builder.SkillBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.SkillRelationBuilder
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.SkillRelationType
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@DisplayName("SkillRelationController Integration Tests")
class SkillRelationControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    private fun createRoleAndUser(
        roleName: String,
        email: String,
    ): Pair<RoleModel, UserModel> {
        val role = roleRepository.save(RoleModel(roleName, null))
        val user =
            userRepository.save(
                UserModel(
                    email = email,
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        return role to user
    }

    private fun createRelationRequest(
        fromSkillId: String,
        toSkillId: String,
        transferPenalty: Double = 0.7,
        relationType: String = "SIMILAR_TO",
    ): Map<String, Any> =
        mapOf(
            "fromSkillId" to fromSkillId,
            "toSkillId" to toSkillId,
            "relationType" to relationType,
            "transferPenalty" to transferPenalty,
        )

    @Test
    fun `admin can create a curated skill relation and gets 201`() {
        val (_, admin) = createRoleAndUser("ADMIN", "admin@firma.de")
        val token = jwtService.generateAccessToken(admin)
        val kotlin = skillRepository.save(SkillBuilder().build(name = "kotlin"))
        val java = skillRepository.save(SkillBuilder().build(name = "java"))

        mockMvc
            .post("/api/admin/skill-relations") {
                header("Authorization", "Bearer $token")
                withBodyRequest(createRelationRequest(kotlin.id, java.id))
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { isNotEmpty() }
                jsonPath("$.fromSkillName") { value("kotlin") }
                jsonPath("$.toSkillName") { value("java") }
                jsonPath("$.relationType") { value("SIMILAR_TO") }
                jsonPath("$.transferPenalty") { value(0.7) }
                jsonPath("$.source") { value("CURATED") }
            }
    }

    @Test
    fun `non-admin gets 403 when creating skill relation`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val token = jwtService.generateAccessToken(pm)
        val kotlin = skillRepository.save(SkillBuilder().build(name = "kotlin"))
        val java = skillRepository.save(SkillBuilder().build(name = "java"))

        mockMvc
            .post("/api/admin/skill-relations") {
                header("Authorization", "Bearer $token")
                withBodyRequest(createRelationRequest(kotlin.id, java.id))
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `returns 400 when fromSkillId equals toSkillId`() {
        val (_, admin) = createRoleAndUser("ADMIN", "admin@firma.de")
        val token = jwtService.generateAccessToken(admin)
        val kotlin = skillRepository.save(SkillBuilder().build(name = "kotlin"))

        mockMvc
            .post("/api/admin/skill-relations") {
                header("Authorization", "Bearer $token")
                withBodyRequest(createRelationRequest(kotlin.id, kotlin.id))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `returns 404 when skill does not exist`() {
        val (_, admin) = createRoleAndUser("ADMIN", "admin@firma.de")
        val token = jwtService.generateAccessToken(admin)
        val kotlin = skillRepository.save(SkillBuilder().build(name = "kotlin"))

        mockMvc
            .post("/api/admin/skill-relations") {
                header("Authorization", "Bearer $token")
                withBodyRequest(createRelationRequest(kotlin.id, "nonexistent-id"))
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("SKILL_NOT_FOUND") }
            }
    }

    @Test
    fun `returns 409 when relation already exists`() {
        val (_, admin) = createRoleAndUser("ADMIN", "admin@firma.de")
        val token = jwtService.generateAccessToken(admin)
        val kotlin = skillRepository.save(SkillBuilder().build(name = "kotlin"))
        val java = skillRepository.save(SkillBuilder().build(name = "java"))
        skillRelationRepository.save(
            SkillRelationBuilder().build(
                fromSkill = kotlin,
                toSkill = java,
                relationType = SkillRelationType.SIMILAR_TO,
            ),
        )

        mockMvc
            .post("/api/admin/skill-relations") {
                header("Authorization", "Bearer $token")
                withBodyRequest(createRelationRequest(kotlin.id, java.id))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("SKILL_RELATION_DUPLICATE") }
            }
    }

    @Test
    fun `admin can delete a skill relation and gets 204`() {
        val (_, admin) = createRoleAndUser("ADMIN", "admin@firma.de")
        val token = jwtService.generateAccessToken(admin)
        val kotlin = skillRepository.save(SkillBuilder().build(name = "kotlin"))
        val java = skillRepository.save(SkillBuilder().build(name = "java"))
        val relation =
            skillRelationRepository.save(
                SkillRelationBuilder().build(fromSkill = kotlin, toSkill = java),
            )

        mockMvc
            .delete("/api/admin/skill-relations/${relation.id}") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isNoContent() }
            }

        assertThat(skillRelationRepository.findById(relation.id)).isEmpty
    }

    @Test
    fun `returns 404 when deleting nonexistent relation`() {
        val (_, admin) = createRoleAndUser("ADMIN", "admin@firma.de")
        val token = jwtService.generateAccessToken(admin)

        mockMvc
            .delete("/api/admin/skill-relations/nonexistent-id") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("SKILL_RELATION_NOT_FOUND") }
            }
    }

    @Test
    fun `non-admin gets 403 when deleting skill relation`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val token = jwtService.generateAccessToken(pm)
        val kotlin = skillRepository.save(SkillBuilder().build(name = "kotlin"))
        val java = skillRepository.save(SkillBuilder().build(name = "java"))
        val relation =
            skillRelationRepository.save(
                SkillRelationBuilder().build(fromSkill = kotlin, toSkill = java),
            )

        mockMvc
            .delete("/api/admin/skill-relations/${relation.id}") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `any authenticated user can list relations for a skill`() {
        val (_, employer) = createRoleAndUser("EMPLOYER", "emp@firma.de")
        val token = jwtService.generateAccessToken(employer)
        val kotlin = skillRepository.save(SkillBuilder().build(name = "kotlin"))
        val java = skillRepository.save(SkillBuilder().build(name = "java"))
        val scala = skillRepository.save(SkillBuilder().build(name = "scala"))
        skillRelationRepository.save(
            SkillRelationBuilder().build(
                fromSkill = kotlin,
                toSkill = java,
                transferPenalty = 0.7,
            ),
        )
        skillRelationRepository.save(
            SkillRelationBuilder().build(
                fromSkill = scala,
                toSkill = kotlin,
                transferPenalty = 0.8,
            ),
        )

        mockMvc
            .get("/api/skill-relations?skillId=${kotlin.id}") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
            }
    }

    @Test
    fun `returns 404 when listing relations for nonexistent skill`() {
        val (_, employer) = createRoleAndUser("EMPLOYER", "emp@firma.de")
        val token = jwtService.generateAccessToken(employer)

        mockMvc
            .get("/api/skill-relations?skillId=nonexistent-id") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("SKILL_NOT_FOUND") }
            }
    }

    @Test
    fun `returns 401 when not authenticated`() {
        val kotlin = skillRepository.save(SkillBuilder().build(name = "kotlin"))
        val java = skillRepository.save(SkillBuilder().build(name = "java"))

        mockMvc
            .post("/api/admin/skill-relations") {
                withBodyRequest(createRelationRequest(kotlin.id, java.id))
            }.andExpect {
                status { isUnauthorized() }
            }

        mockMvc
            .get("/api/skill-relations?skillId=${kotlin.id}")
            .andExpect {
                status { isUnauthorized() }
            }
    }
}
