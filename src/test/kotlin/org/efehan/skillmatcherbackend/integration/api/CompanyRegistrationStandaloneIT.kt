package org.efehan.skillmatcherbackend.integration.api

import org.efehan.skillmatcherbackend.core.company.CompleteRegistrationRequest
import org.efehan.skillmatcherbackend.core.company.RegisterCompanyRequest
import org.efehan.skillmatcherbackend.core.company.ResendRegistrationCodeRequest
import org.efehan.skillmatcherbackend.core.company.VerifyRegistrationCodeRequest
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(
    properties = [
        "app.standalone.enabled=true",
        "app.standalone.company-name=Standalone GmbH",
        "app.standalone.company-street=Werkstraße 1",
        "app.standalone.company-zip=70173",
        "app.standalone.company-city=Stuttgart",
        "app.standalone.company-country=DE",
        "app.standalone.admin-email=admin@standalone.local",
    ],
)
@DisplayName("Company Registration in Standalone Mode")
class CompanyRegistrationStandaloneIT : AbstractIntegrationTest() {
    @Test
    fun `public config reports registration as disabled in standalone mode`() {
        mockMvc.get("/api/public/config").andExpect {
            status { isOk() }
            jsonPath("$.registrationEnabled") { value(false) }
        }
    }

    @Test
    fun `registration endpoint returns 404 in standalone mode`() {
        mockMvc
            .post("/api/public/companies/register") {
                withBodyRequest(
                    RegisterCompanyRequest(
                        name = "Neue GmbH",
                        street = "Hauptstraße 1",
                        zip = "10115",
                        city = "Berlin",
                        country = "DE",
                        adminEmail = "chef@neue-gmbh.de",
                    ),
                )
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `verify endpoint returns 404 in standalone mode`() {
        mockMvc
            .post("/api/public/companies/verify") {
                withBodyRequest(
                    VerifyRegistrationCodeRequest(
                        email = "chef@neue-gmbh.de",
                        code = "123456",
                    ),
                )
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `complete endpoint returns 404 in standalone mode`() {
        mockMvc
            .post("/api/public/companies/complete") {
                withBodyRequest(
                    CompleteRegistrationRequest(
                        email = "chef@neue-gmbh.de",
                        code = "123456",
                        password = "Secret-Password1!",
                        firstName = "Chef",
                        lastName = "Neue",
                    ),
                )
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `resend-code endpoint returns 404 in standalone mode`() {
        mockMvc
            .post("/api/public/companies/resend-code") {
                withBodyRequest(
                    ResendRegistrationCodeRequest(
                        email = "chef@neue-gmbh.de",
                    ),
                )
            }.andExpect {
                status { isNotFound() }
            }
    }
}
