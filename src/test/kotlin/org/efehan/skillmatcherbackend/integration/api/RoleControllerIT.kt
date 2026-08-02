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

    private fun superadminToken(): String {
        val role = roleRepository.save(RoleModel("SUPERADMIN", null))
        val user = userRepository.save(UserBuilder().build(email = "root@platform.io", role = role))
        return jwtService.generateAccessToken(user)
    }

    private fun adminToken(): String {
        val role = roleRepository.save(RoleModel("ADMIN", null))
        val user = userRepository.save(UserBuilder().build(email = "admin@firma-a.de", role = role))
        return jwtService.generateAccessToken(user)
    }

    private fun customRole(name: String = "AUDITOR") = roleRepository.save(RoleModel(name, "Reads audit logs"))

    @Test
    fun `creates a role with an uppercased name`() {
        val token = superadminToken()

        mockMvc
            .post("/api/superadmin/roles") {
                withBodyRequest(CreateRoleRequest(name = "auditor", description = " Reads audit logs "))
                withAuth(token)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.name") { value("AUDITOR") }
                jsonPath("$.description") { value("Reads audit logs") }
                jsonPath("$.builtIn") { value(false) }
            }

        TenantContext.runAsRoot {
            assertThat(roleRepository.findByName("AUDITOR")).isNotNull()
        }
    }

    @Test
    fun `rejects a duplicate role name`() {
        val token = superadminToken()
        customRole()

        mockMvc
            .post("/api/superadmin/roles") {
                withBodyRequest(CreateRoleRequest(name = "AUDITOR", description = null))
                withAuth(token)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("ROLE_ALREADY_EXISTS") }
            }
    }

    @Test
    fun `rejects a name that would not survive as an authority`() {
        val token = superadminToken()

        mockMvc
            .post("/api/superadmin/roles") {
                withBodyRequest(CreateRoleRequest(name = "AUDITOR,ADMIN", description = null))
                withAuth(token)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `lists built-in and custom roles`() {
        val token = superadminToken()
        customRole()

        mockMvc
            .get("/api/superadmin/roles") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].name") { value("AUDITOR") }
                jsonPath("$[0].builtIn") { value(false) }
                jsonPath("$[1].name") { value("SUPERADMIN") }
                jsonPath("$[1].builtIn") { value(true) }
            }
    }

    @Test
    fun `updates the description`() {
        val token = superadminToken()
        val role = customRole()

        mockMvc
            .patch("/api/superadmin/roles/${role.id}") {
                withBodyRequest(UpdateRoleRequest(description = "Reads audit logs only"))
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("AUDITOR") }
                jsonPath("$.description") { value("Reads audit logs only") }
            }
    }

    @Test
    fun `deletes an unassigned custom role`() {
        val token = superadminToken()
        val role = customRole()

        mockMvc
            .delete("/api/superadmin/roles/${role.id}") {
                withAuth(token)
            }.andExpect {
                status { isNoContent() }
            }

        TenantContext.runAsRoot {
            assertThat(roleRepository.findByName("AUDITOR")).isNull()
        }
    }

    @Test
    fun `refuses to delete a built-in role`() {
        val token = superadminToken()
        val builtIn = roleRepository.save(RoleModel("EMPLOYER", null))

        mockMvc
            .delete("/api/superadmin/roles/${builtIn.id}") {
                withAuth(token)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("ROLE_IMMUTABLE") }
            }
    }

    @Test
    fun `refuses to delete a role a tenant still assigns`() {
        // the holder sits in company A while the request runs as root — the check has to
        // see across tenants, otherwise the delete would strand that user's foreign key
        val token = superadminToken()
        val role = customRole()
        userRepository.save(UserBuilder().build(email = "auditor@firma-a.de", role = role))

        mockMvc
            .delete("/api/superadmin/roles/${role.id}") {
                withAuth(token)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.errorCode") { value("ROLE_IN_USE") }
            }
    }

    @Test
    fun `returns 404 for an unknown role`() {
        val token = superadminToken()

        mockMvc
            .delete("/api/superadmin/roles/does-not-exist") {
                withAuth(token)
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.errorCode") { value("ROLE_NOT_FOUND") }
            }
    }

    @Test
    fun `admin role cannot manage the global catalog`() {
        val token = adminToken()

        mockMvc
            .post("/api/superadmin/roles") {
                withBodyRequest(CreateRoleRequest(name = "AUDITOR", description = null))
                withAuth(token)
            }.andExpect {
                status { isForbidden() }
            }
    }
}
