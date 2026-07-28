package org.efehan.skillmatcherbackend.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.csrf.CsrfTokenRequestHandler
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler
import java.util.function.Supplier

// From the Spring Security SPA CSRF guide: accepts the raw XSRF-TOKEN cookie value in the
// header (what an SPA can read), and falls back to the Xor-masked variant otherwise.
class SpaCsrfTokenRequestHandler : CsrfTokenRequestAttributeHandler() {
    private val delegate: CsrfTokenRequestHandler = XorCsrfTokenRequestAttributeHandler()

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        deferredCsrfToken: Supplier<CsrfToken>,
    ) {
        delegate.handle(request, response, deferredCsrfToken)
    }

    override fun resolveCsrfTokenValue(
        request: HttpServletRequest,
        csrfToken: CsrfToken,
    ): String? =
        if (!request.getHeader(csrfToken.headerName).isNullOrBlank()) {
            super.resolveCsrfTokenValue(request, csrfToken)
        } else {
            delegate.resolveCsrfTokenValue(request, csrfToken)
        }
}
