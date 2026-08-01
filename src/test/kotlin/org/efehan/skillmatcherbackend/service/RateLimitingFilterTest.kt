package org.efehan.skillmatcherbackend.service

import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.config.filter.RateLimitingFilter
import org.efehan.skillmatcherbackend.config.properties.RateLimitProperties
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.ObjectMapper

@DisplayName("Rate Limiting Filter Unit Tests")
class RateLimitingFilterTest {
    private val properties =
        RateLimitProperties(
            loginPerMinute = 2,
            passwordResetPerMinute = 5,
            invitationPerMinute = 5,
            refreshPerMinute = 5,
            wsTicketPerMinute = 5,
            registrationPerMinute = 5,
            registrationCodePerMinute = 5,
        )
    private lateinit var filter: RateLimitingFilter

    @BeforeEach
    fun setUp() {
        filter = RateLimitingFilter(properties, ObjectMapper())
    }

    @Test
    fun `allows requests under the limit`() {
        // given
        val request = MockHttpServletRequest("POST", "/api/auth/login")
        val response = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        // when
        filter.doFilter(request, response, chain)

        // then
        verify(exactly = 1) { chain.doFilter(any(), any()) }
        assertThat(response.status).isEqualTo(200)
    }

    @Test
    fun `rejects requests over the limit with 429`() {
        // given
        val chain = mockk<FilterChain>(relaxed = true)

        // when - login limit is 2 per minute
        repeat(3) {
            val response = MockHttpServletResponse()
            filter.doFilter(MockHttpServletRequest("POST", "/api/auth/login"), response, chain)
            if (it < 2) {
                assertThat(response.status).isEqualTo(200)
            } else {
                assertThat(response.status).isEqualTo(429)
                assertThat(response.contentAsString).contains(GlobalErrorCode.RATE_LIMIT_EXCEEDED.name)
            }
        }

        // then
        verify(exactly = 2) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `tracks limits per IP address`() {
        // given
        val chain = mockk<FilterChain>(relaxed = true)

        // when - exhaust limit for first IP
        repeat(2) {
            filter.doFilter(MockHttpServletRequest("POST", "/api/auth/login"), MockHttpServletResponse(), chain)
        }

        val otherIpRequest = MockHttpServletRequest("POST", "/api/auth/login")
        otherIpRequest.remoteAddr = "10.0.0.1"
        val response = MockHttpServletResponse()
        filter.doFilter(otherIpRequest, response, chain)

        // then - other IP still allowed
        assertThat(response.status).isEqualTo(200)
        verify(exactly = 3) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `ignores paths without rate limit rule`() {
        // given
        val request = MockHttpServletRequest("GET", "/api/projects")
        val response = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        // when
        repeat(10) {
            filter.doFilter(request, response, chain)
        }

        // then
        verify(exactly = 10) { chain.doFilter(any(), any()) }
        assertThat(response.status).isEqualTo(200)
    }

    @Test
    fun `register keeps its own bucket separate from the other company endpoints`() {
        // given - rule order matters: /register must not fall into the
        // /api/public/companies/ prefix bucket
        val chain = mockk<FilterChain>(relaxed = true)

        // when - exhaust the verify/complete/resend bucket
        repeat(5) {
            filter.doFilter(MockHttpServletRequest("POST", "/api/public/companies/verify"), MockHttpServletResponse(), chain)
        }

        // then - register is still allowed, verify is not
        val registerResponse = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("POST", "/api/public/companies/register"), registerResponse, chain)
        assertThat(registerResponse.status).isEqualTo(200)

        val verifyResponse = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("POST", "/api/public/companies/verify"), verifyResponse, chain)
        assertThat(verifyResponse.status).isEqualTo(429)
    }
}
