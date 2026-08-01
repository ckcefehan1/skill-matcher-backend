package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.efehan.skillmatcherbackend.core.admin.AdminUserService
import org.efehan.skillmatcherbackend.core.chat.ChatService
import org.efehan.skillmatcherbackend.core.project.ProjectService
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest

/**
 * Isolation is proven on the service layer — repositories alone would miss a
 * code path that drops the tenant. Controller-level cross-tenant access with
 * real JWTs is covered by [CrossTenantSecurityIT].
 */
@DisplayName("Tenant Isolation Integration Tests")
class TenantIsolationIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var chatService: ChatService

    @Autowired
    private lateinit var projectService: ProjectService

    @Autowired
    private lateinit var adminUserService: AdminUserService

    private fun createUser(
        email: String,
        role: RoleModel,
    ): UserModel =
        userRepository.save(
            UserBuilder().build(email = email, role = role),
        )

    @Test
    fun `admin user list does not leak across tenants`() {
        // given
        val role = roleRepository.save(RoleModel("ADMIN", null))
        val userA = createUser("a@firma-a.de", role)
        TenantContext.set(companyB.id)
        createUser("b@firma-b.de", role)

        // when/then: B sees only its own user
        assertThat(adminUserService.listUsers(PageRequest.of(0, 10)).map { it.email })
            .containsExactly("b@firma-b.de")

        // and A sees only its own
        TenantContext.set(companyA.id)
        assertThat(adminUserService.listUsers(PageRequest.of(0, 10)).map { it.email })
            .containsExactly(userA.email)
    }

    @Test
    fun `project service does not leak across tenants`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val pmA = createUser("pm-a@firma-a.de", role)
        val projectA =
            projectRepository.save(
                ProjectBuilder().build(owner = pmA, name = "Project A"),
            )

        TenantContext.set(companyB.id)
        val pmB = createUser("pm-b@firma-b.de", role)
        projectRepository.save(
            ProjectBuilder().build(owner = pmB, name = "Project B"),
        )

        // when/then: list is scoped …
        assertThat(projectService.getAllProjects(PageRequest.of(0, 10)).map { it.name })
            .containsExactly("Project B")

        // … and direct id access to tenant A's project fails
        assertThatThrownBy { projectService.getProject(projectA.id) }
            .isInstanceOf(RuntimeException::class.java)

        TenantContext.set(companyA.id)
        assertThat(projectService.getAllProjects(PageRequest.of(0, 10)).map { it.name })
            .containsExactly("Project A")
    }

    @Test
    fun `chat partner search does not leak across tenants`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val userA = createUser("anna@firma-a.de", role)
        userRepository.save(UserBuilder().build(email = "andre@firma-a.de", firstName = "Andre", role = role))
        TenantContext.set(companyB.id)
        val userB = createUser("berta@firma-b.de", role)

        // when/then
        assertThat(chatService.searchChatPartners(userB, "andr", 10)).isEmpty()

        TenantContext.set(companyA.id)
        assertThat(chatService.searchChatPartners(userA, "andr", 10).map { it.email })
            .containsExactly("andre@firma-a.de")
    }

    @Test
    fun `explicit root context sees all tenants`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        createUser("a@firma-a.de", role)
        TenantContext.set(companyB.id)
        createUser("b@firma-b.de", role)

        // when/then: only the explicitly declared root context is unfiltered
        TenantContext.runAsRoot {
            assertThat(userRepository.findAll().map { it.email })
                .containsExactlyInAnyOrder("a@firma-a.de", "b@firma-b.de")
        }
    }
}
