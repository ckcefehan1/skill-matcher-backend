package org.efehan.skillmatcherbackend.config.filter

import io.jsonwebtoken.Claims
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
        // restored rather than cleared: outer tenant scopes (tests, interceptors) survive the request
        val previousTenant = TenantContext.get()
        val previousRoot = TenantContext.isRootExplicit()

        try {
            // a request never inherits an ambient tenant — only the claim decides
            TenantContext.clear()
            val claims = token?.takeIf { SecurityContextHolder.getContext().authentication == null }?.let(::validateOrNull)
            val companyId = claims?.get("companyId") as String?

            if (companyId != null) {
                // before the first DB access: everything below runs in this tenant
                TenantContext.set(companyId)
                if (!companyService.isEnabled(companyId)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Company is disabled")
                    return
                }
                authenticate(claims, request)
                filterChain.doFilter(request, response)
            } else {
                // no tenant claim: public endpoints and SUPERADMIN run explicitly as root
                TenantContext.runAsRoot {
                    claims?.let { authenticate(it, request) }
                    filterChain.doFilter(request, response)
                }
            }
        } finally {
            TenantContext.clear()
            if (previousRoot) TenantContext.allowRoot()
            previousTenant?.let { TenantContext.set(it) }
        }
    }

    private fun validateOrNull(token: String) =
        try {
            jwtService.validateToken(token)
        } catch (_: InvalidTokenException) {
            // Token ungueltig — Request bleibt unauthentifiziert, Spring Security gibt 401
            null
        }

    private fun authenticate(
        claims: Claims,
        request: HttpServletRequest,
    ) {
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
