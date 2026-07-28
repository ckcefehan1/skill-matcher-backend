package org.efehan.skillmatcherbackend.config.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {
    companion object {
        const val HEADER_NAME = "X-Correlation-ID"
        const val MDC_KEY = "correlationId"
        private const val MAX_HEADER_LENGTH = 100
        private val ALLOWED_CHARS = Regex("^[A-Za-z0-9-]+$")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId =
            request
                .getHeader(HEADER_NAME)
                ?.takeIf { it.isNotBlank() && it.length <= MAX_HEADER_LENGTH && it.matches(ALLOWED_CHARS) }
                ?: UUID.randomUUID().toString()

        MDC.put(MDC_KEY, correlationId)
        response.setHeader(HEADER_NAME, correlationId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }
}
