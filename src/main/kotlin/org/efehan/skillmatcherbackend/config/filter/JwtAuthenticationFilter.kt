package org.efehan.skillmatcherbackend.config.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.efehan.skillmatcherbackend.config.properties.CookieProperties
import org.efehan.skillmatcherbackend.core.auth.CustomUserDetailsService
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.company.CompanyService
import org.efehan.skillmatcherbackend.core.tenant.TenantContext
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidTokenException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: CustomUserDetailsService,
    private val cookieProperties: CookieProperties,
    private val companyService: CompanyService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = extractToken(request)

        try {
            if (token != null && SecurityContextHolder.getContext().authentication == null) {
                try {
                    val claims = jwtService.validateToken(token)
                    val companyId = claims["companyId"] as String?

                    if (companyId != null && !companyService.isEnabled(companyId)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Company is disabled")
                        return
                    }

                    // before the first DB access: everything below runs in this tenant
                    companyId?.let { TenantContext.set(it) }

                    val userDetails = userDetailsService.loadUserByUsername(claims.subject)

                    if (userDetails.isEnabled) {
                        val auth =
                            UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.authorities,
                            )
                        auth.details = WebAuthenticationDetailsSource().buildDetails(request)
                        SecurityContextHolder.getContext().authentication = auth
                    }
                } catch (_: InvalidTokenException) {
                    // Token ungueltig — Request bleibt unauthentifiziert, Spring Security gibt 401
                }
            }

            filterChain.doFilter(request, response)
        } finally {
            TenantContext.clear()
        }
    }

    private fun extractToken(request: HttpServletRequest): String? {
        request.cookies
            ?.firstOrNull { it.name == cookieProperties.accessTokenName }
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).ifBlank { null }
        }
        return null
    }
}
