package org.efehan.skillmatcherbackend.config

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.config.filter.CorrelationIdFilter
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class CorrelationIdFilterTest {
    private val filter = CorrelationIdFilter()
    private val request =
        mockk<HttpServletRequest>(relaxed = true) {
            // relaxed mock returns "" here, which OncePerRequestFilter reads as "already filtered" and skips
            every { getAttribute(any<String>()) } returns null
        }
    private val response = mockk<HttpServletResponse>(relaxed = true)

    @Test
    fun `generates correlation id when header missing and clears MDC after request`() {
        every { request.getHeader(CorrelationIdFilter.HEADER_NAME) } returns null
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        val headerSlot = slot<String>()
        verify { response.setHeader(CorrelationIdFilter.HEADER_NAME, capture(headerSlot)) }
        assertThat(headerSlot.captured).isNotBlank()
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull()
    }

    @Test
    fun `reuses incoming correlation id and exposes it in MDC during request`() {
        every { request.getHeader(CorrelationIdFilter.HEADER_NAME) } returns "abc-123"
        var mdcDuringRequest: String? = null
        val chain =
            FilterChain { _, _ ->
                mdcDuringRequest = MDC.get(CorrelationIdFilter.MDC_KEY)
            }

        filter.doFilter(request, response, chain)

        assertThat(mdcDuringRequest).isEqualTo("abc-123")
        verify { response.setHeader(CorrelationIdFilter.HEADER_NAME, "abc-123") }
    }

    @Test
    fun `ignores overlong incoming correlation id`() {
        every { request.getHeader(CorrelationIdFilter.HEADER_NAME) } returns "x".repeat(500)
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        val headerSlot = slot<String>()
        verify { response.setHeader(CorrelationIdFilter.HEADER_NAME, capture(headerSlot)) }
        assertThat(headerSlot.captured).isNotEqualTo("x".repeat(500))
    }

    @Test
    fun `ignores incoming correlation id with disallowed characters`() {
        every { request.getHeader(CorrelationIdFilter.HEADER_NAME) } returns "bad id[31m"
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        val headerSlot = slot<String>()
        verify { response.setHeader(CorrelationIdFilter.HEADER_NAME, capture(headerSlot)) }
        assertThat(headerSlot.captured).isNotEqualTo("bad id[31m")
    }
}
