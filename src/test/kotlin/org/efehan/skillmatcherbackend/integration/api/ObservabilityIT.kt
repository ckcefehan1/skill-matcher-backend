package org.efehan.skillmatcherbackend.integration.api

import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.config.filter.CorrelationIdFilter
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get

@DisplayName("Observability Integration Tests")
class ObservabilityIT : AbstractIntegrationTest() {
    @Test
    fun `health endpoint is reachable without auth`() {
        mockMvc
            .get("/actuator/health")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
            }
    }

    @Test
    fun `prometheus endpoint exposes metrics without auth`() {
        mockMvc
            .get("/actuator/prometheus")
            .andExpect {
                status { isOk() }
                content { string(org.hamcrest.Matchers.containsString("jvm_")) }
            }
    }

    @Test
    fun `response echoes incoming correlation id`() {
        mockMvc
            .get("/actuator/health") {
                header(CorrelationIdFilter.HEADER_NAME, "test-correlation-42")
            }.andExpect {
                header { string(CorrelationIdFilter.HEADER_NAME, "test-correlation-42") }
            }
    }

    @Test
    fun `response contains generated correlation id when none was sent`() {
        val result =
            mockMvc
                .get("/actuator/health")
                .andReturn()

        assertThat(result.response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotBlank()
    }
}
