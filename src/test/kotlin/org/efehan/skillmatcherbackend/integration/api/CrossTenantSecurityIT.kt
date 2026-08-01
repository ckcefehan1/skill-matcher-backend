package org.efehan.skillmatcherbackend.integration.api

import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.fixtures.builder.ProjectBuilder
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get

/**
 * End-to-end proof that the tenant filter holds on the HTTP path: a real JWT
 * from tenant A must never reach tenant B's data, no matter which controller.
 */
@DisplayName("Cross-Tenant Security Integration Tests")
class CrossTenantSecurityIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var jwtService: JwtService

    private fun createUser(
        email: String,
        roleName: String,
    ): UserModel {
        val role = roleRepository.findByName(roleName) ?: roleRepository.save(RoleModel(roleName, null))
        return userRepository.save(
            UserBuilder().build(email = email, role = role),
        )
    }

    @Test
    fun `tenant A cannot read tenant B projects over HTTP`() {
        // given: project in tenant B
        TenantContext.set(companyB.id)
        val pmB = createUser("pm@firma-b.de", "PROJECTMANAGER")
        val projectB = projectRepository.save(ProjectBuilder().build(owner = pmB, name = "B Projekt"))

        // and: an admin in tenant A with a real token
        TenantContext.set(companyA.id)
        val adminA = createUser("admin@firma-a.de", "ADMIN")
        val tokenA = jwtService.generateAccessToken(adminA)

        // when/then: direct id access does not find B's project
        mockMvc
            .get("/api/projects/${projectB.id}") {
                withAuth(tokenA)
            }.andExpect {
                status { isNotFound() }
            }

        // and: the project list stays in tenant A
        mockMvc
            .get("/api/projects") {
                withAuth(tokenA)
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(0) }
            }
    }

    @Test
    fun `tenant A does not see tenant B users over HTTP`() {
        // given
        TenantContext.set(companyB.id)
        createUser("berta@firma-b.de", "EMPLOYER")

        TenantContext.set(companyA.id)
        val adminA = createUser("admin@firma-a.de", "ADMIN")
        val tokenA = jwtService.generateAccessToken(adminA)

        // when/then: admin list contains only tenant A
        mockMvc
            .get("/api/admin/users") {
                withAuth(tokenA)
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) }
                jsonPath("$.content[0].email") { value("admin@firma-a.de") }
            }
    }

    @Test
    fun `tenant A finds no chat partners in tenant B over HTTP`() {
        // given
        TenantContext.set(companyB.id)
        val roleB = roleRepository.save(RoleModel("EMPLOYER", null))
        userRepository.save(UserBuilder().build(email = "berta@firma-b.de", firstName = "Berta", role = roleB))

        TenantContext.set(companyA.id)
        val userA = createUser("anna@firma-a.de", "EMPLOYER")
        val tokenA = jwtService.generateAccessToken(userA)

        // when/then: without the tenant filter this query would find Berta
        mockMvc
            .get("/api/chat/users/search?q=bert") {
                withAuth(tokenA)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
    }
}
