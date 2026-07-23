package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectApplicationBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectMemberBuilder
import org.efehan.skillmatcherbackend.persistence.ApplicationStatus
import org.efehan.skillmatcherbackend.persistence.ProjectMemberStatus
import org.efehan.skillmatcherbackend.persistence.ProjectModel
import org.efehan.skillmatcherbackend.persistence.ProjectStatus
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
import java.time.LocalDate

@DisplayName("ApplicationController Integration Tests")
class ApplicationControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    private fun createRoleAndUser(
        roleName: String,
        email: String,
    ): Pair<RoleModel, UserModel> {
        val role = roleRepository.findByName(roleName) ?: roleRepository.save(RoleModel(roleName, null))
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

    private fun createProject(owner: UserModel): ProjectModel =
        projectRepository.save(
            ProjectModel(
                name = "Test Project",
                description = "Test Description",
                status = ProjectStatus.PLANNED,
                startDate = LocalDate.of(2026, 3, 1),
                endDate = LocalDate.of(2026, 9, 1),
                maxMembers = 5,
                owner = owner,
            ),
        )

    // --- apply ---

    @Test
    fun `employer can apply to a project and gets 201`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val (_, employer) = createRoleAndUser("EMPLOYER", "emp@firma.de")
        val token = jwtService.generateAccessToken(employer)
        val project = createProject(pm)

        mockMvc
            .post("/api/projects/${project.id}/applications") {
                header("Authorization", "Bearer $token")
                withBodyRequest(mapOf("message" to "I'd like to join"))
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { isNotEmpty() }
                jsonPath("$.status") { value("PENDING") }
                jsonPath("$.userId") { value(employer.id) }
                jsonPath("$.projectId") { value(project.id) }
                jsonPath("$.message") { value("I'd like to join") }
            }
    }

    @Test
    fun `employer gets 409 when applying twice with active PENDING`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val (_, employer) = createRoleAndUser("EMPLOYER", "emp@firma.de")
        val token = jwtService.generateAccessToken(employer)
        val project = createProject(pm)

        mockMvc
            .post("/api/projects/${project.id}/applications") {
                header("Authorization", "Bearer $token")
                withBodyRequest(mapOf("message" to null))
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/projects/${project.id}/applications") {
                header("Authorization", "Bearer $token")
                withBodyRequest(mapOf("message" to null))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("APPLICATION_DUPLICATE") }
            }
    }

    @Test
    fun `employer gets 409 when already an active member`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val (_, employer) = createRoleAndUser("EMPLOYER", "emp@firma.de")
        val token = jwtService.generateAccessToken(employer)
        val project = createProject(pm)
        projectMemberRepository.save(ProjectMemberBuilder().build(project = project, user = employer, status = ProjectMemberStatus.ACTIVE))

        mockMvc
            .post("/api/projects/${project.id}/applications") {
                header("Authorization", "Bearer $token")
                withBodyRequest(mapOf("message" to null))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("APPLICATION_FOR_MEMBER") }
            }
    }

    @Test
    fun `employer gets 404 when project does not exist`() {
        val (_, employer) = createRoleAndUser("EMPLOYER", "emp@firma.de")
        val token = jwtService.generateAccessToken(employer)

        mockMvc
            .post("/api/projects/nonexistent/applications") {
                header("Authorization", "Bearer $token")
                withBodyRequest(mapOf("message" to null))
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("PROJECT_NOT_FOUND") }
            }
    }

    @Test
    fun `project manager gets 403 when trying to apply`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val token = jwtService.generateAccessToken(pm)
        val project = createProject(pm)

        mockMvc
            .post("/api/projects/${project.id}/applications") {
                header("Authorization", "Bearer $token")
                withBodyRequest(mapOf("message" to null))
            }.andExpect { status { isForbidden() } }
    }

    // --- listForProject ---

    @Test
    fun `project owner can list applications with default sort (appliedAt desc)`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val (_, employer1) = createRoleAndUser("EMPLOYER", "emp1@firma.de")
        val (_, employer2) = createRoleAndUser("EMPLOYER", "emp2@firma.de")
        val token = jwtService.generateAccessToken(pm)
        val project = createProject(pm)
        val older = Instant.now().minusSeconds(3600)
        val newer = Instant.now()
        applicationRepository.save(
            ProjectApplicationBuilder().build(project = project, user = employer1, status = ApplicationStatus.DECLINED, appliedAt = older),
        )
        applicationRepository.save(
            ProjectApplicationBuilder().build(project = project, user = employer2, status = ApplicationStatus.PENDING, appliedAt = newer),
        )

        mockMvc
            .get("/api/projects/${project.id}/applications") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) }
                jsonPath("$.content[0].status") { value("PENDING") }
                jsonPath("$.content[1].status") { value("DECLINED") }
            }
    }

    @Test
    fun `project owner can override sort via query param`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val (_, employer1) = createRoleAndUser("EMPLOYER", "emp1@firma.de")
        val (_, employer2) = createRoleAndUser("EMPLOYER", "emp2@firma.de")
        val token = jwtService.generateAccessToken(pm)
        val project = createProject(pm)
        applicationRepository.save(
            ProjectApplicationBuilder().build(project = project, user = employer1, status = ApplicationStatus.ACCEPTED),
        )
        applicationRepository.save(
            ProjectApplicationBuilder().build(project = project, user = employer2, status = ApplicationStatus.PENDING),
        )

        mockMvc
            .get("/api/projects/${project.id}/applications?sort=status,asc") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) }
                // ACCEPTED < PENDING alphabetically
                jsonPath("$.content[0].status") { value("ACCEPTED") }
                jsonPath("$.content[1].status") { value("PENDING") }
            }
    }

    @Test
    fun `non-owner project manager gets 403 when listing applications`() {
        val (_, pm1) = createRoleAndUser("PROJECTMANAGER", "pm1@firma.de")
        val (_, pm2) = createRoleAndUser("PROJECTMANAGER", "pm2@firma.de")
        val token = jwtService.generateAccessToken(pm2)
        val project = createProject(pm1)

        mockMvc
            .get("/api/projects/${project.id}/applications") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isForbidden() } }
    }

    // --- accept ---

    @Test
    fun `project owner can accept an application and user becomes member`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val (_, employer) = createRoleAndUser("EMPLOYER", "emp@firma.de")
        val token = jwtService.generateAccessToken(pm)
        val project = createProject(pm)
        val application =
            applicationRepository.save(
                ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.PENDING),
            )

        mockMvc
            .post("/api/applications/${application.id}/accept") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ACCEPTED") }
                jsonPath("$.decidedById") { value(pm.id) }
            }

        val member = projectMemberRepository.findByProjectAndUser(project, employer)
        assertThat(member).isNotNull
        assertThat(member?.status).isEqualTo(ProjectMemberStatus.ACTIVE)
    }

    @Test
    fun `non-owner project manager gets 403 when accepting`() {
        val (_, pm1) = createRoleAndUser("PROJECTMANAGER", "pm1@firma.de")
        val (_, pm2) = createRoleAndUser("PROJECTMANAGER", "pm2@firma.de")
        val (_, employer) = createRoleAndUser("EMPLOYER", "emp@firma.de")
        val token = jwtService.generateAccessToken(pm2)
        val project = createProject(pm1)
        val application =
            applicationRepository.save(
                ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.PENDING),
            )

        mockMvc
            .post("/api/applications/${application.id}/accept") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("APPLICATION_ACCESS_DENIED") }
            }
    }

    @Test
    fun `accepting already decided application returns 409`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val (_, employer) = createRoleAndUser("EMPLOYER", "emp@firma.de")
        val token = jwtService.generateAccessToken(pm)
        val project = createProject(pm)
        val application =
            applicationRepository.save(
                ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.DECLINED),
            )

        mockMvc
            .post("/api/applications/${application.id}/accept") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("APPLICATION_ALREADY_DECIDED") }
            }
    }

    // --- decline ---

    @Test
    fun `project owner can decline an application with reason`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val (_, employer) = createRoleAndUser("EMPLOYER", "emp@firma.de")
        val token = jwtService.generateAccessToken(pm)
        val project = createProject(pm)
        val application =
            applicationRepository.save(
                ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.PENDING),
            )

        mockMvc
            .post("/api/applications/${application.id}/decline") {
                header("Authorization", "Bearer $token")
                withBodyRequest(mapOf("reason" to "Not enough experience"))
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("DECLINED") }
                jsonPath("$.message") { value("Not enough experience") }
            }
    }

    // --- withdraw ---

    @Test
    fun `employer can withdraw own pending application`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val (_, employer) = createRoleAndUser("EMPLOYER", "emp@firma.de")
        val token = jwtService.generateAccessToken(employer)
        val project = createProject(pm)
        val application =
            applicationRepository.save(
                ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.PENDING),
            )

        mockMvc
            .post("/api/applications/${application.id}/withdraw") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("WITHDRAWN") }
            }
    }

    @Test
    fun `employer gets 403 when withdrawing someone elses application`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val (_, employer1) = createRoleAndUser("EMPLOYER", "emp1@firma.de")
        val (_, employer2) = createRoleAndUser("EMPLOYER", "emp2@firma.de")
        val token = jwtService.generateAccessToken(employer2)
        val project = createProject(pm)
        val application =
            applicationRepository.save(
                ProjectApplicationBuilder().build(project = project, user = employer1, status = ApplicationStatus.PENDING),
            )

        mockMvc
            .post("/api/applications/${application.id}/withdraw") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("APPLICATION_ACCESS_DENIED") }
            }
    }

    // --- listForUser ---

    @Test
    fun `employer can list own applications`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val (_, employer) = createRoleAndUser("EMPLOYER", "emp@firma.de")
        val token = jwtService.generateAccessToken(employer)
        val project = createProject(pm)
        applicationRepository.save(
            ProjectApplicationBuilder().build(project = project, user = employer, status = ApplicationStatus.PENDING),
        )

        mockMvc
            .get("/api/me/applications") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) }
                jsonPath("$.content[0].userId") { value(employer.id) }
                jsonPath("$.content[0].status") { value("PENDING") }
            }
    }

    // --- auth ---

    @Test
    fun `unauthenticated gets 401 on all endpoints`() {
        val (_, pm) = createRoleAndUser("PROJECTMANAGER", "pm@firma.de")
        val project = createProject(pm)

        mockMvc
            .post("/api/projects/${project.id}/applications") {
                withBodyRequest(mapOf("message" to null))
            }.andExpect { status { isUnauthorized() } }

        mockMvc
            .get("/api/projects/${project.id}/applications")
            .andExpect { status { isUnauthorized() } }

        mockMvc
            .get("/api/me/applications")
            .andExpect { status { isUnauthorized() } }
    }
}
