package org.efehan.skillmatcherbackend.integration.api

import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectApplicationBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectMemberBuilder
import org.efehan.skillmatcherbackend.fixtures.requests.ProjectMemberFixtures
import org.efehan.skillmatcherbackend.persistence.ApplicationStatus
import org.efehan.skillmatcherbackend.persistence.ProjectMemberStatus
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

@DisplayName("ProjectMemberController Integration Tests")
class ProjectMemberControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    @Test
    fun `should add member and return 201`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "owner@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val member =
            userRepository.save(
                UserModel(
                    email = "member@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)
        applicationRepository.save(
            ProjectApplicationBuilder().build(project = project, user = member, status = ApplicationStatus.ACCEPTED),
        )

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/members") {
                withAuth(token)
                withBodyRequest(ProjectMemberFixtures.buildAddProjectMemberRequest(userId = member.id))
            }.andExpect {
                status { isCreated() }
                jsonPath("$.userId") { value(member.id) }
                jsonPath("$.status") { value("ACTIVE") }
                jsonPath("$.userName") { value("${member.firstName} ${member.lastName}") }
                jsonPath("$.email") { value(member.email) }
            }
    }

    @Test
    fun `should return 403 without accepted application`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "owner@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val member =
            userRepository.save(
                UserModel(
                    email = "member@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/members") {
                withAuth(token)
                withBodyRequest(ProjectMemberFixtures.buildAddProjectMemberRequest(userId = member.id))
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("PROJECT_MEMBER_REQUIRES_ACCEPTED_APPLICATION") }
            }
    }

    @Test
    fun `should return 409 when member already active`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "owner@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val member =
            userRepository.save(
                UserModel(
                    email = "member@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)
        applicationRepository.save(
            ProjectApplicationBuilder().build(project = project, user = member, status = ApplicationStatus.ACCEPTED),
        )
        projectMemberRepository.save(ProjectMemberBuilder().build(project = project, user = member))

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/members") {
                withAuth(token)
                withBodyRequest(ProjectMemberFixtures.buildAddProjectMemberRequest(userId = member.id))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("PROJECT_MEMBER_DUPLICATE") }
            }
    }

    @Test
    fun `should reactivate LEFT member`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "owner@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val member =
            userRepository.save(
                UserModel(
                    email = "member@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)
        applicationRepository.save(
            ProjectApplicationBuilder().build(project = project, user = member, status = ApplicationStatus.ACCEPTED),
        )
        projectMemberRepository.save(
            ProjectMemberBuilder().build(project = project, user = member, status = ProjectMemberStatus.LEFT),
        )

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/members") {
                withAuth(token)
                withBodyRequest(ProjectMemberFixtures.buildAddProjectMemberRequest(userId = member.id))
            }.andExpect {
                status { isCreated() }
                jsonPath("$.status") { value("ACTIVE") }
            }
    }

    @Test
    fun `should return 409 when project is full`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "owner@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val member1 =
            userRepository.save(
                UserModel(
                    email = "m1@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val member2 =
            userRepository.save(
                UserModel(
                    email = "m2@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val newMember =
            userRepository.save(
                UserModel(
                    email = "new@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner, maxMembers = 2))
        val token = jwtService.generateAccessToken(owner)

        applicationRepository.save(
            ProjectApplicationBuilder().build(project = project, user = newMember, status = ApplicationStatus.ACCEPTED),
        )
        projectMemberRepository.save(ProjectMemberBuilder().build(project = project, user = member1))
        projectMemberRepository.save(ProjectMemberBuilder().build(project = project, user = member2))

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/members") {
                withAuth(token)
                withBodyRequest(ProjectMemberFixtures.buildAddProjectMemberRequest(userId = newMember.id))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("PROJECT_FULL") }
            }
    }

    @Test
    fun `should return 403 when caller is not project owner`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "owner@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val nonOwner =
            userRepository.save(
                UserModel(
                    email = "other@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(nonOwner)

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/members") {
                withAuth(token)
                withBodyRequest(ProjectMemberFixtures.buildAddProjectMemberRequest(userId = nonOwner.id))
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("PROJECT_ACCESS_DENIED") }
            }
    }

    @Test
    fun `should list only active members`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "owner@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val active =
            userRepository.save(
                UserModel(
                    email = "active@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val left =
            userRepository.save(
                UserModel(
                    email = "left@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)

        projectMemberRepository.save(ProjectMemberBuilder().build(project = project, user = active))
        projectMemberRepository.save(
            ProjectMemberBuilder().build(project = project, user = left, status = ProjectMemberStatus.LEFT),
        )

        // when & then
        mockMvc
            .get("/api/projects/${project.id}/members") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].email") { value(active.email) }
            }
    }

    @Test
    fun `should remove member and return 204`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "owner@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val member =
            userRepository.save(
                UserModel(
                    email = "member@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)

        projectMemberRepository.save(ProjectMemberBuilder().build(project = project, user = member))

        // when & then
        mockMvc
            .delete("/api/projects/${project.id}/members/${member.id}") {
                withAuth(token)
            }.andExpect {
                status { isNoContent() }
            }
    }

    @Test
    fun `should return 404 when removing non-member`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "owner@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val nonMember =
            userRepository.save(
                UserModel(
                    email = "non@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(owner)

        // when & then
        mockMvc
            .delete("/api/projects/${project.id}/members/${nonMember.id}") {
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("PROJECT_MEMBER_NOT_FOUND") }
            }
    }

    @Test
    fun `should allow member to leave project`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "owner@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val member =
            userRepository.save(
                UserModel(
                    email = "member@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val memberToken = jwtService.generateAccessToken(member)

        projectMemberRepository.save(ProjectMemberBuilder().build(project = project, user = member))

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/members/leave") {
                withAuth(memberToken)
            }.andExpect {
                status { isNoContent() }
            }
    }

    @Test
    fun `should return 404 when leaving project user is not member of`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val owner =
            userRepository.save(
                UserModel(
                    email = "owner@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val nonMember =
            userRepository.save(
                UserModel(
                    email = "non@firma.de",
                    passwordHash = passwordEncoder.encode("Test-Password1!"),
                    firstName = "Test",
                    lastName = "User",
                    role = role,
                ).apply { isEnabled = true },
            )
        val project = projectRepository.save(ProjectBuilder().build(owner = owner))
        val token = jwtService.generateAccessToken(nonMember)

        // when & then
        mockMvc
            .post("/api/projects/${project.id}/members/leave") {
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("PROJECT_MEMBER_NOT_FOUND") }
            }
    }

    @Test
    fun `should return 401 when not authenticated`() {
        mockMvc
            .get("/api/projects/some-id/members")
            .andExpect { status { isUnauthorized() } }

        mockMvc
            .post("/api/projects/some-id/members")
            .andExpect { status { isForbidden() } }
    }
}
