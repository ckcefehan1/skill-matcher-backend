package org.efehan.skillmatcherbackend.integration.api

import org.efehan.skillmatcherbackend.core.company.RegisterCompanyRequest
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
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
}
