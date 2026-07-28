package org.efehan.skillmatcherbackend.core.auth

import jakarta.servlet.http.HttpServletResponse
import org.efehan.skillmatcherbackend.config.properties.CookieProperties
import org.efehan.skillmatcherbackend.config.properties.JwtProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class AuthCookieService(
    private val cookieProperties: CookieProperties,
    private val jwtProperties: JwtProperties,
) {
    fun addCookies(
        response: HttpServletResponse,
        accessToken: String,
        refreshToken: String,
    ) {
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie(accessToken).toString())
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString())
    }

    fun clearCookies(response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, clear(cookieProperties.accessTokenName, "/").toString())
        response.addHeader(HttpHeaders.SET_COOKIE, clear(cookieProperties.refreshTokenName, REFRESH_COOKIE_PATH).toString())
    }

    private fun accessCookie(value: String) =
        base(cookieProperties.accessTokenName, value, "/")
            .maxAge(Duration.ofMillis(jwtProperties.accessTokenExpiration))
            .build()

    private fun refreshCookie(value: String) =
        base(cookieProperties.refreshTokenName, value, REFRESH_COOKIE_PATH)
            .maxAge(Duration.ofMillis(jwtProperties.refreshTokenExpiration))
            .build()

    private fun clear(
        name: String,
        path: String,
    ) = base(name, "", path).maxAge(Duration.ZERO).build()

    private fun base(
        name: String,
        value: String,
        path: String,
    ) = ResponseCookie
        .from(name, value)
        .httpOnly(true)
        .secure(cookieProperties.secure)
        .sameSite("Strict")
        .path(path)

    companion object {
        const val REFRESH_COOKIE_PATH = "/api/auth"
    }
}
