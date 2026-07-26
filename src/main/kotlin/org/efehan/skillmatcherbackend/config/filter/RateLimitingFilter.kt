package org.efehan.skillmatcherbackend.config.filter

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.efehan.skillmatcherbackend.config.properties.RateLimitProperties
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.exception.GlobalErrorCodeResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class RateLimitingFilter(
    private val properties: RateLimitProperties,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    private data class Rule(
        val pathPrefix: String,
        val capacity: Long,
    )

    private val rules by lazy {
        listOf(
            Rule("/api/auth/login", properties.loginPerMinute),
            Rule("/api/auth/password-reset", properties.passwordResetPerMinute),
            Rule("/api/auth/invitations", properties.invitationPerMinute),
            Rule("/api/auth/refresh", properties.refreshPerMinute),
        )
    }

    // ponytail: unbounded map, one entry per rule+IP — swap to Caffeine when caching lands (ROADMAP 4)
    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val rule = rules.firstOrNull { request.requestURI.startsWith(it.pathPrefix) }
        if (rule == null) {
            filterChain.doFilter(request, response)
            return
        }

        val key = "${rule.pathPrefix}:${request.remoteAddr}"
        val bucket = buckets.computeIfAbsent(key) { newBucket(rule.capacity) }

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response)
            return
        }

        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(GlobalErrorCodeResponse(errorCode = GlobalErrorCode.RATE_LIMIT_EXCEEDED)),
        )
    }

    private fun newBucket(capacity: Long): Bucket =
        Bucket
            .builder()
            .addLimit(
                Bandwidth
                    .builder()
                    .capacity(capacity)
                    .refillGreedy(capacity, Duration.ofMinutes(1))
                    .build(),
            ).build()
}
