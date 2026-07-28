package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectSkillBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.SkillBuilder
import org.efehan.skillmatcherbackend.fixtures.requests.ProjectSkillFixtures
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@DisplayName("ProjectSkillController Integration Tests")
class ProjectSkillControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    @Test
    fun `should add a new skill to project and return 201`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)
        val request = ProjectSkillFixtures.buildAddProjectSkillRequest(name = "Kotlin", level = 4, priority = "NICE_TO_HAVE")

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/skills") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { isNotEmpty() }
                jsonPath("$.name") { value("kotlin") }
                jsonPath("$.level") { value(4) }
                jsonPath("$.priority") { value("NICE_TO_HAVE") }
            }
    }

    @Test
    fun `should update existing skill and return 200`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)
        val skill = skillRepository.save(SkillBuilder().build(name = "kotlin"))
        projectSkillRepository.save(ProjectSkillBuilder().build(project = project, skill = skill, level = 2))
        val request = ProjectSkillFixtures.buildAddProjectSkillRequest(name = "Kotlin", level = 5, priority = "NICE_TO_HAVE")

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/skills") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("kotlin") }
                jsonPath("$.level") { value(5) }
                jsonPath("$.priority") { value("NICE_TO_HAVE") }
            }
    }

    @Test
    fun `should return 400 when level is below 1`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)
        val request = ProjectSkillFixtures.buildAddProjectSkillRequest(name = "Kotlin", level = 0)

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/skills") {
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
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)
        val request = ProjectSkillFixtures.buildAddProjectSkillRequest(name = "Kotlin", level = 6)

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/skills") {
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
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)
        val request = ProjectSkillFixtures.buildAddProjectSkillRequest(name = "  ")

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/skills") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `should return 400 when priority is invalid`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)
        val request = ProjectSkillFixtures.buildAddProjectSkillRequest(name = "Kotlin", priority = "invalid")

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/skills") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `should return all skills for project`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)
        val skill1 = skillRepository.save(SkillBuilder().build(name = "kotlin"))
        val skill2 = skillRepository.save(SkillBuilder().build(name = "java"))
        projectSkillRepository.save(ProjectSkillBuilder().build(project = project, skill = skill1, level = 4))
        projectSkillRepository.save(ProjectSkillBuilder().build(project = project, skill = skill2, level = 3))

        // when & then
        mockMvc
            .get("/api/projects/${project.id}/skills") {
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
    fun `should return empty list when project has no skills`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)

        // when & then
        mockMvc
            .get("/api/projects/${project.id}/skills") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
    }

    @Test
    fun `should not return skills of other projects`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner1 =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val owner2 =
            userRepository.save(
                UserModel(
                    email = "other.pm@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Other",
                    lastName = "PM",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project1 = projectRepository.save(ProjectBuilder().build(owner = owner1))
        val project2 = projectRepository.save(ProjectBuilder().build(owner = owner2))
        val token1 = jwtService.generateAccessToken(owner1)
        val skill = skillRepository.save(SkillBuilder().build(name = "kotlin"))
        projectSkillRepository.save(ProjectSkillBuilder().build(project = project1, skill = skill, level = 4))
        projectSkillRepository.save(ProjectSkillBuilder().build(project = project2, skill = skill, level = 2))

        // when & then
        mockMvc
            .get("/api/projects/${project1.id}/skills") {
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
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)
        val skill = skillRepository.save(SkillBuilder().build(name = "kotlin"))
        val projectSkill = projectSkillRepository.save(ProjectSkillBuilder().build(project = project, skill = skill))

        // when & then
        mockMvc
            .delete("/api/projects/${project.id}/skills/${projectSkill.id}") {
                withAuth(token)
            }.andExpect {
                status { isNoContent() }
            }

        assertThat(projectSkillRepository.findById(projectSkill.id)).isEmpty
    }

    @Test
    fun `should return 404 when deleting nonexistent skill`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)

        // when & then
        mockMvc
            .delete("/api/projects/${project.id}/skills/nonexistent-id") {
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("PROJECT_SKILL_NOT_FOUND") }
            }
    }

    @Test
    fun `should return 403 when other project manager tries to access skills`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val otherPm =
            userRepository.save(
                UserModel(
                    email = "other.pm@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Other",
                    lastName = "PM",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val otherToken = jwtService.generateAccessToken(otherPm)
        val skill = skillRepository.save(SkillBuilder().build(name = "kotlin"))
        val projectSkill = projectSkillRepository.save(ProjectSkillBuilder().build(project = project, skill = skill))

        // when & then
        mockMvc
            .get("/api/projects/${project.id}/skills") {
                withAuth(otherToken)
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("PROJECT_ACCESS_DENIED") }
            }

        mockMvc
            .post("/api/projects/${project.id}/skills") {
                withAuth(otherToken)
                withBodyRequest(ProjectSkillFixtures.buildAddProjectSkillRequest(name = "Java"))
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("PROJECT_ACCESS_DENIED") }
            }

        mockMvc
            .delete("/api/projects/${project.id}/skills/${projectSkill.id}") {
                withAuth(otherToken)
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("PROJECT_ACCESS_DENIED") }
            }
    }

    @Test
    fun `should return 404 when project does not exist`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(owner)
        val request = ProjectSkillFixtures.buildAddProjectSkillRequest()

        // when & then
        mockMvc
            .post("/api/projects/nonexistent-id/skills") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("PROJECT_NOT_FOUND") }
            }

        mockMvc
            .get("/api/projects/nonexistent-id/skills") {
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("PROJECT_NOT_FOUND") }
            }
    }

    @Test
    fun `should return 401 when not authenticated`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "max@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Max",
                    lastName = "Mustermann",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))

        // when & then
        mockMvc
            .get("/api/projects/${project.id}/skills")
            .andExpect {
                status { isUnauthorized() }
            }

        mockMvc
            .post("/api/projects/${project.id}/skills") {
                withBodyRequest(ProjectSkillFixtures.buildAddProjectSkillRequest())
            }.andExpect {
                status { isUnauthorized() }
            }

        mockMvc
            .delete("/api/projects/${project.id}/skills/some-id")
            .andExpect {
                status { isForbidden() }
            }
    }
}
