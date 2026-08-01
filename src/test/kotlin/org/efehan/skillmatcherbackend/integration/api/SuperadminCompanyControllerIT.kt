package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.company.CreateCompanyRequest
import org.efehan.skillmatcherbackend.core.company.UpdateCompanyStatusRequest
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@DisplayName("Superadmin Company Controller Integration Tests")
class SuperadminCompanyControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private fun createSuperadmin(): UserModel {
        if (roleRepository.findByName("ADMIN") == null) {
            roleRepository.save(RoleModel("ADMIN", null))
        }
        val role = roleRepository.save(RoleModel("SUPERADMIN", null))
        // users.company_id is NOT NULL, so the row hangs in company A — the role keeps
        // the companyId out of the JWT, which is what makes requests run as root
        return userRepository.save(
            UserBuilder()
                .build(
                    email = "root@platform.io",
                    passwordHash = passwordEncoder.encode("Secret-Password1!"),
                    role = role,
                ),
        )
    }

    private fun createTenantAdmin(): UserModel {
        val role = roleRepository.findByName("ADMIN") ?: roleRepository.save(RoleModel("ADMIN", null))
        return userRepository.save(
            UserBuilder()
                .build(
                    email = "admin@firma-a.de",
                    passwordHash = passwordEncoder.encode("Secret-Password1!"),
                    role = role,
                ),
        )
    }

    private fun request() =
        CreateCompanyRequest(
            name = "Neue GmbH",
            street = "Hauptstraße 1",
            zip = "10115",
            city = "Berlin",
            country = "DE",
            adminEmail = "chef@neue-gmbh.de",
        )

    @Test
    fun `creates company with first admin and invitation`() {
        // given
        val token = jwtService.generateAccessToken(createSuperadmin())

        // when
        mockMvc
            .post("/api/superadmin/companies") {
                withBodyRequest(request())
                withAuth(token)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.name") { value("Neue GmbH") }
                jsonPath("$.isEnabled") { value(true) }
            }

        // then: company, disabled admin and invitation token exist, all wired to the new tenant
        TenantContext.clear()
        val company = companyRepository.findAll().single { it.name == "Neue GmbH" }
        assertThat(company.isEnabled).isTrue()

        val admin = userRepository.findByEmail("chef@neue-gmbh.de")!!
        assertThat(admin.isEnabled).isFalse()
        assertThat(admin.companyId).isEqualTo(company.id)

        val invitations =
            invitationTokenRepository.findAll().filter { it.user.id == admin.id }
        assertThat(invitations).hasSize(1)
        assertThat(invitations.single().companyId).isEqualTo(company.id)
    }

    @Test
    fun `admin role cannot use superadmin endpoints`() {
        // given
        val token = jwtService.generateAccessToken(createTenantAdmin())

        // when/then
        mockMvc
            .post("/api/superadmin/companies") {
                withBodyRequest(request())
                withAuth(token)
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `lists all companies`() {
        // given
        val token = jwtService.generateAccessToken(createSuperadmin())

        // when/then
        mockMvc
            .get("/api/superadmin/companies") {
                withAuth(token)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
            }
    }

    @Test
    fun `disabled company tokens are rejected with 403`() {
        // given: tenant admin with a valid token, then the company gets disabled
        val superadminToken = jwtService.generateAccessToken(createSuperadmin())
        val adminToken = jwtService.generateAccessToken(createTenantAdmin())

        mockMvc
            .patch("/api/superadmin/companies/${companyA.id}/status") {
                withBodyRequest(UpdateCompanyStatusRequest(enabled = false))
                withAuth(superadminToken)
            }.andExpect {
                status { isNoContent() }
            }

        // when/then: cached status must not delay the lockout beyond this request
        mockMvc
            .get("/api/admin/users") {
                withAuth(adminToken)
            }.andExpect {
                status { isForbidden() }
            }
    }
}
