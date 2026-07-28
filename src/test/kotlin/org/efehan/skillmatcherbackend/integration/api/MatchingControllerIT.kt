package org.efehan.skillmatcherbackend.integration.api

import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.ProjectSkillModel
import org.efehan.skillmatcherbackend.persistence.ProjectStatus
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.SkillModel
import org.efehan.skillmatcherbackend.persistence.SkillPriority
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserSkillModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.get
import java.time.LocalDate

@DisplayName("MatchingController Integration Tests")
class MatchingControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    @Test
    fun `should return matching candidates sorted by score`() {
        // given
        val pmRole = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val empRole = roleRepository.save(RoleModel("EMPLOYER", null))
        val pm =
            userRepository.save(
                UserModel(
                    email = "pm@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "PM",
                    lastName = "User",
                    role = pmRole,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(pm)

        val kotlin = skillRepository.save(SkillModel("kotlin"))
        val spring = skillRepository.save(SkillModel("spring boot"))
        val docker = skillRepository.save(SkillModel("docker"))

        val project =
            projectRepository.save(
                ProjectModel(
                    name = "Test Project",
                    description = "Test Description",
                    status = ProjectStatus.PLANNED,
                    startDate = LocalDate.of(2026, 3, 1),
                    endDate = LocalDate.of(2026, 9, 1),
                    maxMembers = 5,
                    owner = pm,
                ),
            )
        projectSkillRepository.save(ProjectSkillModel(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE))
        projectSkillRepository.save(ProjectSkillModel(project = project, skill = spring, level = 4, priority = SkillPriority.MUST_HAVE))
        projectSkillRepository.save(ProjectSkillModel(project = project, skill = docker, level = 2, priority = SkillPriority.NICE_TO_HAVE))

        val user1 =
            userRepository.save(
                UserModel(
                    email = "user1@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "User",
                    lastName = "One",
                    role = empRole,
                ).apply { isEnabled = true },
            )
        userSkillRepository.save(UserSkillModel(user = user1, skill = kotlin, level = 4))
        userSkillRepository.save(UserSkillModel(user = user1, skill = spring, level = 5))
        userSkillRepository.save(UserSkillModel(user = user1, skill = docker, level = 3))

        val user2 =
            userRepository.save(
                UserModel(
                    email = "user2@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "User",
                    lastName = "Two",
                    role = empRole,
                ).apply { isEnabled = true },
            )
        userSkillRepository.save(UserSkillModel(user = user2, skill = kotlin, level = 3))

        // when & then
        mockMvc
            .get("/api/matching/projects/${project.id}/candidates") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].userId") { value(user1.id) }
                jsonPath("$[0].score") { isNumber() }
                jsonPath("$[0].breakdown.mustHaveCoverage") { value(1.0) }
                jsonPath("$[0].matchedSkills.length()") { value(3) }
                jsonPath("$[0].missingSkills.length()") { value(0) }
                jsonPath("$[1].userId") { value(user2.id) }
                jsonPath("$[1].breakdown.mustHaveCoverage") { value(0.5) }
            }
    }

    @Test
    fun `should return 404 when project not found`() {
        // given
        val pmRole = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val pm =
            userRepository.save(
                UserModel(
                    email = "pm@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "PM",
                    lastName = "User",
                    role = pmRole,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(pm)

        // when & then
        mockMvc
            .get("/api/matching/projects/nonexistent-id/candidates") {
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("PROJECT_NOT_FOUND") }
            }
    }

    @Test
    fun `should return 403 when employer tries to find candidates`() {
        // given
        val empRole = roleRepository.save(RoleModel("EMPLOYER", null))
        val emp =
            userRepository.save(
                UserModel(
                    email = "emp@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Emp",
                    lastName = "User",
                    role = empRole,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(emp)

        // when & then
        mockMvc
            .get("/api/matching/projects/some-id/candidates") {
                withAuth(token)
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `should return 401 when not authenticated`() {
        // when & then
        mockMvc
            .get("/api/matching/projects/some-id/candidates")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `should filter by minScore`() {
        // given
        val pmRole = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val empRole = roleRepository.save(RoleModel("EMPLOYER", null))
        val pm =
            userRepository.save(
                UserModel(
                    email = "pm@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "PM",
                    lastName = "User",
                    role = pmRole,
                ).apply { isEnabled = true },
            )
        val token = jwtService.generateAccessToken(pm)

        val kotlin = skillRepository.save(SkillModel("kotlin"))
        val spring = skillRepository.save(SkillModel("spring boot"))

        val project =
            projectRepository.save(
                ProjectModel(
                    name = "Test Project",
                    description = "Test Description",
                    status = ProjectStatus.PLANNED,
                    startDate = LocalDate.of(2026, 3, 1),
                    endDate = LocalDate.of(2026, 9, 1),
                    maxMembers = 5,
                    owner = pm,
                ),
            )
        projectSkillRepository.save(ProjectSkillModel(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE))
        projectSkillRepository.save(ProjectSkillModel(project = project, skill = spring, level = 4, priority = SkillPriority.MUST_HAVE))

        val user =
            userRepository.save(
                UserModel(
                    email = "user@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "User",
                    lastName = "One",
                    role = empRole,
                ).apply { isEnabled = true },
            )
        userSkillRepository.save(UserSkillModel(user = user, skill = kotlin, level = 3))

        // when & then
        mockMvc
            .get("/api/matching/projects/${project.id}/candidates?minScore=0.9") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
    }

    @Test
    fun `should return matching projects for authenticated user`() {
        // given
        val pmRole = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val empRole = roleRepository.save(RoleModel("EMPLOYER", null))
        val pm =
            userRepository.save(
                UserModel(
                    email = "pm@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "PM",
                    lastName = "User",
                    role = pmRole,
                ).apply { isEnabled = true },
            )

        val kotlin = skillRepository.save(SkillModel("kotlin"))
        val spring = skillRepository.save(SkillModel("spring boot"))

        val project =
            projectRepository.save(
                ProjectModel(
                    name = "Test Project",
                    description = "Test Description",
                    status = ProjectStatus.PLANNED,
                    startDate = LocalDate.of(2026, 3, 1),
                    endDate = LocalDate.of(2026, 9, 1),
                    maxMembers = 5,
                    owner = pm,
                ),
            )
        projectSkillRepository.save(ProjectSkillModel(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE))
        projectSkillRepository.save(ProjectSkillModel(project = project, skill = spring, level = 3, priority = SkillPriority.NICE_TO_HAVE))

        val user =
            userRepository.save(
                UserModel(
                    email = "user@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "User",
                    lastName = "One",
                    role = empRole,
                ).apply { isEnabled = true },
            )
        userSkillRepository.save(UserSkillModel(user = user, skill = kotlin, level = 4))
        userSkillRepository.save(UserSkillModel(user = user, skill = spring, level = 5))
        val userToken = jwtService.generateAccessToken(user)

        // when & then
        mockMvc
            .get("/api/matching/me/projects") {
                withAuth(userToken)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].projectId") { value(project.id) }
                jsonPath("$[0].score") { isNumber() }
                jsonPath("$[0].projectName") { value("Test Project") }
                jsonPath("$[0].breakdown.mustHaveCoverage") { value(1.0) }
            }
    }

    @Test
    fun `should return empty list when user has no skills`() {
        // given
        val pmRole = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val empRole = roleRepository.save(RoleModel("EMPLOYER", null))
        val pm =
            userRepository.save(
                UserModel(
                    email = "pm@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "PM",
                    lastName = "User",
                    role = pmRole,
                ).apply { isEnabled = true },
            )

        val kotlin = skillRepository.save(SkillModel("kotlin"))
        val project =
            projectRepository.save(
                ProjectModel(
                    name = "Test Project",
                    description = "Test Description",
                    status = ProjectStatus.PLANNED,
                    startDate = LocalDate.of(2026, 3, 1),
                    endDate = LocalDate.of(2026, 9, 1),
                    maxMembers = 5,
                    owner = pm,
                ),
            )
        projectSkillRepository.save(ProjectSkillModel(project = project, skill = kotlin, level = 3, priority = SkillPriority.MUST_HAVE))

        val user =
            userRepository.save(
                UserModel(
                    email = "user@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "User",
                    lastName = "One",
                    role = empRole,
                ).apply { isEnabled = true },
            )
        val userToken = jwtService.generateAccessToken(user)

        // when & then
        mockMvc
            .get("/api/matching/me/projects") {
                withAuth(userToken)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
    }

    @Test
    fun `should return 401 when finding projects not authenticated`() {
        // when & then
        mockMvc
            .get("/api/matching/me/projects")
            .andExpect {
                status { isUnauthorized() }
            }
    }
}
