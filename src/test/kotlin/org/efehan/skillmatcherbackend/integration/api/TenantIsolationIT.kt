package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.ProjectMemberStatus
import org.efehan.skillmatcherbackend.persistence.ProjectStatus
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest

@DisplayName("Tenant Isolation Integration Tests")
class TenantIsolationIT : AbstractIntegrationTest() {
    private fun createUser(
        email: String,
        role: RoleModel,
    ): UserModel =
        userRepository.save(
            UserBuilder().build(email = email, role = role),
        )

    @Test
    fun `tenant A does not see users of tenant B`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val userA = createUser("a@firma-a.de", role)
        TenantContext.set(companyB.id)
        val userB = createUser("b@firma-b.de", role)

        // when/then: B sees only its own user
        assertThat(userRepository.findAll().map { it.email }).containsExactly(userB.email)
        assertThat(userRepository.searchByRole("EMPLOYER", "firma", PageRequest.of(0, 10)).content.map { it.email })
            .containsExactly(userB.email)

        // and A sees only its own
        TenantContext.set(companyA.id)
        assertThat(userRepository.findAll().map { it.email }).containsExactly(userA.email)
        assertThat(userRepository.searchByRole("EMPLOYER", "firma", PageRequest.of(0, 10)).content.map { it.email })
            .containsExactly(userA.email)
    }

    @Test
    fun `tenant A does not see projects of tenant B`() {
        // given
        val role = roleRepository.save(RoleModel("PROJECTMANAGER", null))
        val pmA = createUser("pm-a@firma-a.de", role)
        val projectA =
            projectRepository.save(
                ProjectBuilder().build(owner = pmA, name = "Project A"),
            )

        TenantContext.set(companyB.id)
        val pmB = createUser("pm-b@firma-b.de", role)
        val projectB =
            projectRepository.save(
                ProjectBuilder().build(owner = pmB, name = "Project B"),
            )

        // when/then
        assertThat(projectRepository.findAll().map { it.name }).containsExactly(projectB.name)
        assertThat(
            projectRepository
                .findMatchableForUser(
                    user = pmB,
                    statuses = listOf(ProjectStatus.PLANNED, ProjectStatus.ACTIVE),
                    activeStatus = ProjectMemberStatus.ACTIVE,
                ).map { it.name },
        ).containsExactly(projectB.name)

        TenantContext.set(companyA.id)
        assertThat(projectRepository.findAll().map { it.name }).containsExactly(projectA.name)
    }

    @Test
    fun `chat partner search does not leak across tenants`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val userA = createUser("anna@firma-a.de", role)
        createUser("andre@firma-a.de", role)
        TenantContext.set(companyB.id)
        val userB = createUser("berta@firma-b.de", role)

        // when/then
        assertThat(
            userRepository
                .searchChatPartners("", userB.id, PageRequest.of(0, 10))
                .map { it.email },
        ).isEmpty()

        TenantContext.set(companyA.id)
        assertThat(
            userRepository
                .searchChatPartners("", userA.id, PageRequest.of(0, 10))
                .map { it.email },
        ).containsExactly("andre@firma-a.de")
    }

    @Test
    fun `root context sees all tenants`() {
        // given
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        createUser("a@firma-a.de", role)
        TenantContext.set(companyB.id)
        createUser("b@firma-b.de", role)

        // when
        TenantContext.clear()

        // then
        assertThat(userRepository.findAll().map { it.email })
            .containsExactlyInAnyOrder("a@firma-a.de", "b@firma-b.de")
    }
}
