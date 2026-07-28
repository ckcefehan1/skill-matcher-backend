package org.efehan.skillmatcherbackend.config.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.DeferredCsrfToken
import org.springframework.web.filter.OncePerRequestFilter

// Forces CsrfToken resolution on every request so CookieCsrfTokenRepository writes
// the XSRF-TOKEN cookie even for safe (GET) requests — the SPA reads it from there.
class CsrfCookieFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        when (val attribute = request.getAttribute(CsrfToken::class.java.name)) {
            is CsrfToken -> attribute.token
            is DeferredCsrfToken -> attribute.get()
            else -> null
        }
        filterChain.doFilter(request, response)
    }
}
