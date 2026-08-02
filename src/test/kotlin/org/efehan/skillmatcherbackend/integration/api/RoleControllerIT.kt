package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.role.CreateRoleRequest
import org.efehan.skillmatcherbackend.core.role.UpdateRoleRequest
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@DisplayName("Role Controller Integration Tests")
class RoleControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var jwtService: JwtService

    private fun adminToken(): String {
        val role = roleRepository.save(RoleModel("ADMIN", null))
        val user = userRepository.save(UserBuilder().build(email = "admin@firma-a.de", role = role))
        return jwtService.generateAccessToken(user)
    }

    private fun employerToken(): String {
        val role = roleRepository.save(RoleModel("EMPLOYER", null))
        val user = userRepository.save(UserBuilder().build(email = "max@firma-a.de", role = role))
        return jwtService.generateAccessToken(user)
    }

    private fun customRole(name: String = "AUDITOR") = roleRepository.save(RoleModel(name, "Reads audit logs"))

    @Test
    fun `creates a role with an uppercased name`() {
        val token = adminToken()

        mockMvc
            .post("/api/admin/roles") {
                withBodyRequest(CreateRoleRequest(name = "auditor", description = " Reads audit logs "))
                withAuth(token)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.name") { value("AUDITOR") }
                jsonPath("$.description") { value("Reads audit logs") }
                jsonPath("$.builtIn") { value(false) }
            }

        assertThat(roleRepository.findByCompanyIdAndName(companyA.id, "AUDITOR")).isNotNull()
    }

    @Test
    fun `rejects a duplicate role name`() {
        val token = adminToken()
        customRole()

        mockMvc
            .post("/api/admin/roles") {
                withBodyRequest(CreateRoleRequest(name = "AUDITOR", description = null))
                withAuth(token)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("ROLE_ALREADY_EXISTS") }
            }
    }

    @Test
    fun `allows the same role name in another company`() {
        // the catalog is per tenant, so company B's AUDITOR must not block company A's
        val token = adminToken()
        TenantContext.withTenant(companyB.id) { roleRepository.save(RoleModel("AUDITOR", null)) }

        mockMvc
            .post("/api/admin/roles") {
                withBodyRequest(CreateRoleRequest(name = "AUDITOR", description = null))
                withAuth(token)
            }.andExpect {
                status { isCreated() }
            }
    }

    @Test
    fun `rejects a reserved role name`() {
        // SUPERADMIN is never seeded into a company tenant, so only the reserved-name
        // guard stops an admin from minting a name-derived ROLE_SUPERADMIN authority
        val token = adminToken()

        mockMvc
            .post("/api/admin/roles") {
                withBodyRequest(CreateRoleRequest(name = "SUPERADMIN", description = null))
                withAuth(token)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("ROLE_IMMUTABLE") }
            }
    }

    @Test
    fun `rejects a name that would not survive as an authority`() {
        val token = adminToken()

        mockMvc
            .post("/api/admin/roles") {
                withBodyRequest(CreateRoleRequest(name = "AUDITOR,ADMIN", description = null))
                withAuth(token)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `lists only the own company's roles`() {
        val token = adminToken()
        customRole()
        TenantContext.withTenant(companyB.id) { roleRepository.save(RoleModel("FOREIGN", null)) }

        mockMvc
            .get("/api/admin/roles") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].name") { value("ADMIN") }
                jsonPath("$[0].builtIn") { value(true) }
                jsonPath("$[1].name") { value("AUDITOR") }
                jsonPath("$[1].builtIn") { value(false) }
            }
    }

    @Test
    fun `updates the description`() {
        val token = adminToken()
        val role = customRole()

        mockMvc
            .patch("/api/admin/roles/${role.id}") {
                withBodyRequest(UpdateRoleRequest(description = "Reads audit logs only"))
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("AUDITOR") }
                jsonPath("$.description") { value("Reads audit logs only") }
            }
    }

    @Test
    fun `returns 404 when updating a role of another company`() {
        val token = adminToken()
        val foreign = TenantContext.withTenant(companyB.id) { roleRepository.save(RoleModel("FOREIGN", null)) }

        mockMvc
            .patch("/api/admin/roles/${foreign.id}") {
                withBodyRequest(UpdateRoleRequest(description = "takeover"))
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("ROLE_NOT_FOUND") }
            }
    }

    @Test
    fun `deletes an unassigned custom role`() {
        val token = adminToken()
        val role = customRole()

        mockMvc
            .delete("/api/admin/roles/${role.id}") {
                withAuth(token)
            }.andExpect {
                status { isNoContent() }
            }

        assertThat(roleRepository.findByCompanyIdAndName(companyA.id, "AUDITOR")).isNull()
    }

    @Test
    fun `refuses to delete a built-in role`() {
        val token = adminToken()
        val builtIn = roleRepository.save(RoleModel("EMPLOYER", null))

        mockMvc
            .delete("/api/admin/roles/${builtIn.id}") {
                withAuth(token)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("ROLE_IMMUTABLE") }
            }
    }

    @Test
    fun `refuses to delete a role that is still assigned`() {
        val token = adminToken()
        val role = customRole()
        userRepository.save(UserBuilder().build(email = "auditor@firma-a.de", role = role))

        mockMvc
            .delete("/api/admin/roles/${role.id}") {
                withAuth(token)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("ROLE_IN_USE") }
            }
    }

    @Test
    fun `returns 404 for a role of another company`() {
        val token = adminToken()
        val foreign = TenantContext.withTenant(companyB.id) { roleRepository.save(RoleModel("FOREIGN", null)) }

        mockMvc
            .delete("/api/admin/roles/${foreign.id}") {
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("ROLE_NOT_FOUND") }
            }
    }

    @Test
    fun `returns 404 for an unknown role`() {
        val token = adminToken()

        mockMvc
            .delete("/api/admin/roles/does-not-exist") {
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("ROLE_NOT_FOUND") }
            }
    }

    @Test
    fun `non-admin roles cannot manage the catalog`() {
        val token = employerToken()

        mockMvc
            .post("/api/admin/roles") {
                withBodyRequest(CreateRoleRequest(name = "AUDITOR", description = null))
                withAuth(token)
            }.andExpect {
                status { isForbidden() }
            }
    }
}
