package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.company.CompleteRegistrationRequest
import org.efehan.skillmatcherbackend.core.company.RegisterCompanyRequest
import org.efehan.skillmatcherbackend.core.company.ResendRegistrationCodeRequest
import org.efehan.skillmatcherbackend.core.company.VerifyRegistrationCodeRequest
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.RoleName
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.post

@DisplayName("Company Registration Code Integration Tests")
class CompanyRegistrationCodeIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var jwtService: JwtService

    @BeforeEach
    fun ensureAdminRole() {
        if (roleRepository.findByName(RoleName.ADMIN.name) == null) {
            roleRepository.save(RoleModel(RoleName.ADMIN.name, null))
        }
    }

    private fun register(email: String = "chef@neue-gmbh.de") {
        mockMvc
            .post("/api/public/companies/register") {
                withBodyRequest(
                    RegisterCompanyRequest(
                        name = "Neue GmbH",
                        street = "Hauptstraße 1",
                        zip = "10115",
                        city = "Berlin",
                        country = "DE",
                        adminEmail = email,
                    ),
                )
            }.andExpect {
                status { isAccepted() }
            }
    }

    private fun admin(email: String = "chef@neue-gmbh.de"): UserModel = TenantContext.runAsRoot { userRepository.findByEmail(email)!! }

    /** The mailed code is random, so tests pin a known one straight into the row. */
    private fun pinCode(
        email: String = "chef@neue-gmbh.de",
        code: String = "123456",
    ) {
        TenantContext.runAsRoot {
            val row =
                invitationTokenRepository
                    .findFirstByUserAndCodeHashNotNullOrderByCreatedDateDesc(admin(email))!!
            row.codeHash = jwtService.hashToken(code)
            invitationTokenRepository.save(row)
        }
    }

    private fun verify(
        email: String,
        code: String,
    ) = mockMvc
        .post("/api/public/companies/verify") {
            withBodyRequest(VerifyRegistrationCodeRequest(email, code))
        }

    private fun complete(
        email: String,
        code: String,
    ) = mockMvc
        .post("/api/public/companies/complete") {
            withBodyRequest(
                CompleteRegistrationRequest(
                    email = email,
                    code = code,
                    password = "Secret-Password1!",
                    firstName = "Chef",
                    lastName = "Neue",
                ),
            )
        }

    @Test
    fun `wrong code and unknown email get byte-identical responses`() {
        register()
        pinCode()

        val wrongCode = verify("chef@neue-gmbh.de", "000000").andReturn()
        val unknownEmail = verify("ghost@example.com", "000000").andReturn()

        assertThat(wrongCode.response.status).isEqualTo(200)
        assertThat(unknownEmail.response.status).isEqualTo(200)
        assertThat(wrongCode.response.contentAsString)
            .isEqualTo(unknownEmail.response.contentAsString)
    }

    @Test
    fun `after 5 failed attempts even the correct code is rejected`() {
        register()
        pinCode()

        repeat(5) {
            verify("chef@neue-gmbh.de", "000000").andExpect {
                status { isOk() }
                jsonPath("$.valid") { value(false) }
            }
        }

        verify("chef@neue-gmbh.de", "123456").andExpect {
            status { isOk() }
            jsonPath("$.valid") { value(false) }
        }
        complete("chef@neue-gmbh.de", "123456").andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `complete with wrong code keeps password null and company disabled`() {
        register()
        pinCode()

        complete("chef@neue-gmbh.de", "000000").andExpect {
            status { isBadRequest() }
        }

        TenantContext.runAsRoot {
            assertThat(admin().passwordHash).isNull()
            val company = companyRepository.findAll().single { it.name == "Neue GmbH" }
            assertThat(company.isEnabled).isFalse()
        }
    }

    @Test
    fun `verify does not consume the code and complete activates user and company`() {
        register()
        pinCode()

        verify("chef@neue-gmbh.de", "123456").andExpect {
            status { isOk() }
            jsonPath("$.valid") { value(true) }
        }

        // verify alone must not activate anything
        TenantContext.runAsRoot {
            assertThat(admin().isEnabled).isFalse()
            val company = companyRepository.findAll().single { it.name == "Neue GmbH" }
            assertThat(company.isEnabled).isFalse()
        }

        // same code completes: verify was side-effect free
        val result =
            complete("chef@neue-gmbh.de", "123456")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.user.email") { value("chef@neue-gmbh.de") }
                }.andReturn()

        val setCookies = result.response.getHeaders(org.springframework.http.HttpHeaders.SET_COOKIE)
        assertThat(setCookies.any { it.startsWith("access_token=") }).isTrue()
        assertThat(setCookies.any { it.startsWith("refresh_token=") }).isTrue()

        TenantContext.runAsRoot {
            assertThat(admin().isEnabled).isTrue()
            assertThat(admin().passwordHash).isNotNull()
            val company = companyRepository.findAll().single { it.name == "Neue GmbH" }
            assertThat(company.isEnabled).isTrue()
        }
    }

    @Test
    fun `complete twice with the same code is rejected the second time`() {
        register()
        pinCode()

        complete("chef@neue-gmbh.de", "123456").andExpect {
            status { isOk() }
        }
        complete("chef@neue-gmbh.de", "123456").andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `resend kills the old code and keeps exactly one code row`() {
        register()
        pinCode()

        mockMvc
            .post("/api/public/companies/resend-code") {
                withBodyRequest(ResendRegistrationCodeRequest("chef@neue-gmbh.de"))
            }.andExpect {
                status { isAccepted() }
            }

        TenantContext.runAsRoot {
            val codeRows = invitationTokenRepository.findAll().filter { it.codeHash != null }
            assertThat(codeRows).hasSize(1)
            assertThat(codeRows.single().codeHash).isNotEqualTo(jwtService.hashToken("123456"))
            assertThat(codeRows.single().attempts).isEqualTo(0)
        }

        verify("chef@neue-gmbh.de", "123456").andExpect {
            status { isOk() }
            jsonPath("$.valid") { value(false) }
        }
    }

    @Test
    fun `resend for unknown email is answered the same way`() {
        mockMvc
            .post("/api/public/companies/resend-code") {
                withBodyRequest(ResendRegistrationCodeRequest("ghost@example.com"))
            }.andExpect {
                status { isAccepted() }
            }
    }
}
