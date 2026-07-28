package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectBuilder
import org.efehan.skillmatcherbackend.fixtures.requests.ProjectFixtures
import org.efehan.skillmatcherbackend.persistence.ProjectMemberModel
import org.efehan.skillmatcherbackend.persistence.ProjectMemberStatus
import org.efehan.skillmatcherbackend.persistence.ProjectModel
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
import org.springframework.test.web.servlet.put
import java.time.Instant

@DisplayName("ProjectController Integration Tests")
class ProjectControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    private fun createProjectManagerAndGetToken(): Pair<UserModel, String> {
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val user =
            UserModel(
                email = "max@firma.de",
                passwordHash = passwordEncoder.encode("Test-Password1!"),
                firstName = "Max",
                lastName = "Mustermann",
                role = role,
            )
        user.isEnabled = true
        userRepository.save(user)
        return user to jwtService.generateAccessToken(user)
    }

    private fun createEmployerAndGetToken(): Pair<UserModel, String> {
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user =
            UserModel(
                email = "employer@firma.de",
                passwordHash = passwordEncoder.encode("Test-Password1!"),
                firstName = "Employer",
                lastName = "User",
                role = role,
            )
        user.isEnabled = true
        userRepository.save(user)
        return user to jwtService.generateAccessToken(user)
    }

    private fun createOtherProjectManagerAndGetToken(): Pair<UserModel, String> {
        val role = roleRepository.findByName("PROJECTMANAGER")!!
        val user =
            UserModel(
                email = "other.pm@firma.de",
                passwordHash = passwordEncoder.encode("Test-Password1!"),
                firstName = "Other",
                lastName = "PM",
                role = role,
            )
        user.isEnabled = true
        userRepository.save(user)
        return user to jwtService.generateAccessToken(user)
    }

    private fun createProject(owner: UserModel): ProjectModel = projectRepository.save(ProjectBuilder().build(owner = owner))

    @Test
    fun `should create project and return 201`() {
        // given
        val (owner, token) = createProjectManagerAndGetToken()
        val request = ProjectFixtures.buildCreateProjectRequest()

        // when & then
        mockMvc
            .post("/api/projects") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { isNotEmpty() }
                jsonPath("$.name") { value(request.name) }
                jsonPath("$.description") { value(request.description) }
                jsonPath("$.status") { value("PLANNED") }
                jsonPath("$.startDate") { value(request.startDate.toString()) }
                jsonPath("$.endDate") { value(request.endDate.toString()) }
                jsonPath("$.maxMembers") { value(request.maxMembers) }
                jsonPath("$.ownerName") { value("${owner.firstName} ${owner.lastName}") }
                jsonPath("$.createdDate") { isNotEmpty() }
            }
    }

    @Test
    fun `should return 400 when a field is blank`() {
        val (_, token) = createProjectManagerAndGetToken()
        val request = ProjectFixtures.buildCreateProjectRequest(name = "")

        // when & then
        mockMvc
            .post("/api/projects") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `should return 401 when not authenticated`() {
        // when & then
        mockMvc
            .post("/api/projects") {
                withBodyRequest(ProjectFixtures.buildCreateProjectRequest())
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `should return 403 when employer tries to create project`() {
        val (_, token) = createEmployerAndGetToken()

        // when & then
        mockMvc
            .post("/api/projects") {
                withAuth(token)
                withBodyRequest(ProjectFixtures.buildCreateProjectRequest())
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("FORBIDDEN") }
            }
    }

    @Test
    fun `should return project and 200`() {
        // given
        val (owner, token) = createProjectManagerAndGetToken()
        val project = createProject(owner)

        // when & then
        mockMvc
            .get("/api/projects/${project.id}") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(project.id) }
                jsonPath("$.name") { value(project.name) }
                jsonPath("$.description") { value(project.description) }
                jsonPath("$.status") { value(project.status.name) }
                jsonPath("$.startDate") { value(project.startDate.toString()) }
                jsonPath("$.endDate") { value(project.endDate.toString()) }
                jsonPath("$.maxMembers") { value(project.maxMembers) }
                jsonPath("$.ownerName") { value("${owner.firstName} ${owner.lastName}") }
            }
    }

    @Test
    fun `should return 404 when project not found`() {
        // given
        val (_, token) = createProjectManagerAndGetToken()

        // when & then
        mockMvc
            .get("/api/projects/nonexistent-id") {
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("PROJECT_NOT_FOUND") }
            }
    }

    @Test
    fun `should return 401 when getting project not authenticated`() {
        // when & then
        mockMvc
            .get("/api/projects/some-id")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `should return all projects and 200`() {
        // given
        val (owner, token) = createProjectManagerAndGetToken()
        createProject(owner)
        projectRepository.save(ProjectBuilder().build(owner = owner, name = "Another Project"))

        // when & then
        mockMvc
            .get("/api/projects") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) }
                jsonPath("$.content[0].id") { isNotEmpty() }
                jsonPath("$.content[1].id") { isNotEmpty() }
            }
    }

    @Test
    fun `should return empty list when no projects exist`() {
        // given
        val (_, token) = createProjectManagerAndGetToken()

        // when & then
        mockMvc
            .get("/api/projects") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(0) }
            }
    }

    @Test
    fun `should return 401 when listing projects not authenticated`() {
        // when & then
        mockMvc
            .get("/api/projects")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `should update project and return 200`() {
        // given
        val (owner, token) = createProjectManagerAndGetToken()
        val project = createProject(owner)
        val request = ProjectFixtures.buildUpdateProjectRequest(name = "Updated Name")

        // when & then
        mockMvc
            .put("/api/projects/${project.id}") {
                withAuth(token)
                withBodyRequest(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value(request.name) }
                jsonPath("$.description") { value(request.description) }
                jsonPath("$.status") { value(request.status) }
                jsonPath("$.startDate") { value(request.startDate.toString()) }
                jsonPath("$.endDate") { value(request.endDate.toString()) }
                jsonPath("$.maxMembers") { value(request.maxMembers) }
            }
    }

    @Test
    fun `should return 404 when updating nonexistent project`() {
        // given
        val (_, token) = createProjectManagerAndGetToken()

        // when & then
        mockMvc
            .put("/api/projects/nonexistent-id") {
                withAuth(token)
                withBodyRequest(ProjectFixtures.buildUpdateProjectRequest())
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("PROJECT_NOT_FOUND") }
            }
    }

    @Test
    fun `should return 403 when other project manager tries to update`() {
        // given
        val (owner, _) = createProjectManagerAndGetToken()
        val project = createProject(owner)
        val (_, otherToken) = createOtherProjectManagerAndGetToken()

        // when & then
        mockMvc
            .put("/api/projects/${project.id}") {
                withAuth(otherToken)
                withBodyRequest(ProjectFixtures.buildUpdateProjectRequest())
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("PROJECT_ACCESS_DENIED") }
            }
    }

    @Test
    fun `should return 403 when employer tries to update project`() {
        // given
        val (owner, _) = createProjectManagerAndGetToken()
        val project = createProject(owner)
        val (_, employerToken) = createEmployerAndGetToken()

        // when & then
        mockMvc
            .put("/api/projects/${project.id}") {
                withAuth(employerToken)
                withBodyRequest(ProjectFixtures.buildUpdateProjectRequest())
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `should return 400 when update validation fails`() {
        // given
        val (owner, token) = createProjectManagerAndGetToken()
        val project = createProject(owner)

        // when & then
        mockMvc
            .put("/api/projects/${project.id}") {
                withAuth(token)
                withBodyRequest(ProjectFixtures.buildUpdateProjectRequest(name = ""))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `should return 401 when updating project not authenticated`() {
        // when & then
        mockMvc
            .put("/api/projects/some-id") {
                withBodyRequest(ProjectFixtures.buildUpdateProjectRequest())
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `should delete project and return 204`() {
        // given
        val (owner, token) = createProjectManagerAndGetToken()
        val project = createProject(owner)

        // when & then
        mockMvc
            .delete("/api/projects/${project.id}") {
                withAuth(token)
            }.andExpect {
                status { isNoContent() }
            }

        assertThat(projectRepository.findById(project.id)).isEmpty
    }

    @Test
    fun `should delete project with active members and return 204`() {
        // given
        val (owner, token) = createProjectManagerAndGetToken()
        val project = createProject(owner)
        val (member, _) = createOtherProjectManagerAndGetToken()
        projectMemberRepository.save(
            ProjectMemberModel(
                project = project,
                user = member,
                status = ProjectMemberStatus.ACTIVE,
                joinedDate = Instant.now(),
            ),
        )

        // when & then
        mockMvc
            .delete("/api/projects/${project.id}") {
                withAuth(token)
            }.andExpect {
                status { isNoContent() }
            }

        assertThat(projectRepository.findById(project.id)).isEmpty
        assertThat(projectMemberRepository.findAll()).isEmpty()
    }

    @Test
    fun `should return 404 when deleting nonexistent project`() {
        // given
        val (_, token) = createProjectManagerAndGetToken()

        // when & then
        mockMvc
            .delete("/api/projects/nonexistent-id") {
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("PROJECT_NOT_FOUND") }
            }
    }

    @Test
    fun `should return 403 when other project manager tries to delete`() {
        // given
        val (owner, _) = createProjectManagerAndGetToken()
        val project = createProject(owner)
        val (_, otherToken) = createOtherProjectManagerAndGetToken()

        // when & then
        mockMvc
            .delete("/api/projects/${project.id}") {
                withAuth(otherToken)
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("PROJECT_ACCESS_DENIED") }
            }
    }

    @Test
    fun `should return 403 when employer tries to delete project`() {
        // given
        val (owner, _) = createProjectManagerAndGetToken()
        val project = createProject(owner)
        val (_, employerToken) = createEmployerAndGetToken()

        // when & then
        mockMvc
            .delete("/api/projects/${project.id}") {
                withAuth(employerToken)
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `should return 403 when deleting project not authenticated`() {
        // CSRF check rejects unauthenticated mutations before authentication
        // when & then
        mockMvc
            .delete("/api/projects/some-id")
            .andExpect {
                status { isForbidden() }
            }
    }
}
