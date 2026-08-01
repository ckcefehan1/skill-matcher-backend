package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.company.RegisterCompanyRequest
import org.efehan.skillmatcherbackend.core.invitation.AcceptInvitationRequest
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.persistence.InvitationTokenModel
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.RoleName
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.time.temporal.ChronoUnit

@DisplayName("Company Registration Integration Tests")
class CompanyRegistrationIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var jwtService: JwtService

    @BeforeEach
    fun ensureAdminRole() {
        if (roleRepository.findByName(RoleName.ADMIN.name) == null) {
            roleRepository.save(RoleModel(RoleName.ADMIN.name, null))
        }
    }

    private fun request(
        name: String = "Neue GmbH",
        adminEmail: String = "chef@neue-gmbh.de",
    ) = RegisterCompanyRequest(
        name = name,
        street = "Hauptstraße 1",
        zip = "10115",
        city = "Berlin",
        country = "DE",
        adminEmail = adminEmail,
    )

    @Test
    fun `public config reports registration as enabled in SaaS mode`() {
        mockMvc.get("/api/public/config").andExpect {
            status { isOk() }
            jsonPath("$.registrationEnabled") { value(true) }
        }
    }

    @Test
    fun `registration creates disabled company and admin, invite acceptance activates both`() {
        // when
        mockMvc
            .post("/api/public/companies/register") {
                withBodyRequest(request())
            }.andExpect {
                status { isAccepted() }
            }

        // then: company disabled + self-registered, admin disabled, invitation pending
        TenantContext.runAsRoot {
            val company = companyRepository.findAll().single { it.name == "Neue GmbH" }
            assertThat(company.isEnabled).isFalse()
            assertThat(company.selfRegistered).isTrue()

            val admin = userRepository.findByEmail("chef@neue-gmbh.de")!!
            assertThat(admin.isEnabled).isFalse()
            assertThat(admin.companyId).isEqualTo(company.id)

            // given: a known raw token replacing the generated one
            invitationTokenRepository.deleteAll()
            invitationTokenRepository.save(
                InvitationTokenModel(
                    tokenHash = jwtService.hashToken("registration-token"),
                    user = admin,
                    expiresAt = Instant.now().plus(72, ChronoUnit.HOURS),
                    // root context here, so the tenant has to come from the user
                ).apply { companyId = admin.companyId },
            )
        }

        // when: invite accepted
        mockMvc
            .post("/api/auth/invitations/accept") {
                withBodyRequest(
                    AcceptInvitationRequest(
                        token = "registration-token",
                        password = "Secret-Password1!",
                        firstName = "Chef",
                        lastName = "Neue",
                    ),
                )
            }.andExpect {
                status { isOk() }
            }

        // then: user and company are enabled
        TenantContext.runAsRoot {
            assertThat(userRepository.findByEmail("chef@neue-gmbh.de")!!.isEnabled).isTrue()
            val company = companyRepository.findAll().single { it.name == "Neue GmbH" }
            assertThat(companyRepository.findById(company.id).get().isEnabled).isTrue()
        }
    }

    @Test
    fun `duplicate admin email is answered like a success and creates nothing`() {
        // given
        mockMvc
            .post("/api/public/companies/register") {
                withBodyRequest(request())
            }.andExpect {
                status { isAccepted() }
            }

        // when: same email, different company name
        mockMvc
            .post("/api/public/companies/register") {
                withBodyRequest(request(name = "Andere GmbH"))
            }.andExpect {
                status { isAccepted() }
            }

        // then
        TenantContext.runAsRoot {
            assertThat(companyRepository.findAll().none { it.name == "Andere GmbH" }).isTrue()
            assertThat(userRepository.findAll().count { it.email == "chef@neue-gmbh.de" }).isEqualTo(1)
        }
    }

    @Test
    fun `duplicate company name is answered like a success and creates nothing`() {
        // given
        mockMvc
            .post("/api/public/companies/register") {
                withBodyRequest(request())
            }.andExpect {
                status { isAccepted() }
            }

        // when: same name, different email — same answer as success, otherwise the
        // endpoint would enumerate the customer list
        mockMvc
            .post("/api/public/companies/register") {
                withBodyRequest(request(adminEmail = "wer@anders.de"))
            }.andExpect {
                status { isAccepted() }
            }

        // then
        TenantContext.runAsRoot {
            assertThat(companyRepository.findAll().count { it.name == "Neue GmbH" }).isEqualTo(1)
            assertThat(userRepository.findByEmail("wer@anders.de")).isNull()
        }
    }

    @Test
    fun `invalid address is rejected`() {
        mockMvc
            .post("/api/public/companies/register") {
                withBodyRequest(request().copy(street = ""))
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `lowercase country is rejected`() {
        mockMvc
            .post("/api/public/companies/register") {
                withBodyRequest(request().copy(country = "de"))
            }.andExpect {
                status { isBadRequest() }
            }
    }
}
