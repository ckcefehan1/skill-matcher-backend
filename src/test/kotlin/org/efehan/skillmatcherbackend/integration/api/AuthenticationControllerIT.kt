package org.efehan.skillmatcherbackend.integration.api

import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.auth.LoginRequest
import org.efehan.skillmatcherbackend.persistence.RefreshTokenModel
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.testcontainers.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.time.temporal.ChronoUnit

@DisplayName("Authentication Controller Integration Tests")
class AuthenticationControllerIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    private fun createUser(
        email: String = "test@example.com",
        password: String = "Secret-Password1!",
    ): UserModel {
        val role = roleRepository.save(RoleModel("ADMIN", null))
        return userRepository.save(
            UserModel(
                email = email,
                passwordHash = passwordEncoder.encode(password),
                firstName = "Test",
                lastName = "User",
                role = role,
            ).apply { isEnabled = true },
        )
    }

    private fun createRefreshToken(
        user: UserModel,
        rawToken: String,
        familyId: String = "family-1",
        expiresAt: Instant = Instant.now().plus(7, ChronoUnit.DAYS),
        revoked: Boolean = false,
    ): RefreshTokenModel =
        refreshTokenRepository.save(
            RefreshTokenModel(
                tokenHash = jwtService.hashToken(rawToken),
                user = user,
                expiresAt = expiresAt,
                familyId = familyId,
                revoked = revoked,
            ),
        )

    private fun setCookies(result: org.springframework.test.web.servlet.MvcResult): List<String> =
        result.response.getHeaders(HttpHeaders.SET_COOKIE)

    @Test
    fun `should login successfully and set httpOnly cookies when valid credentials provided`() {
        // given
        val password = "Secret-Password1!"
        createUser(password = password)
        val request = LoginRequest(email = "test@example.com", password = password)

        // when
        val result =
            mockMvc
                .post("/api/auth/login") {
                    withBodyRequest(request)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.accessToken") { doesNotExist() }
                    jsonPath("$.refreshToken") { doesNotExist() }
                    jsonPath("$.expiresIn") { isNumber() }
                    jsonPath("$.user.email") { value("test@example.com") }
                    jsonPath("$.user.firstName") { value("Test") }
                    jsonPath("$.user.lastName") { value("User") }
                    jsonPath("$.user.role") { value("ADMIN") }
                }.andReturn()

        // then
        val cookies = setCookies(result)
        assertThat(cookies).anyMatch {
            it.startsWith("access_token=") &&
                it.contains("HttpOnly") &&
                it.contains("SameSite=Strict") &&
                it.contains("Path=/")
        }
        assertThat(cookies).anyMatch {
            it.startsWith("refresh_token=") &&
                it.contains("HttpOnly") &&
                it.contains("SameSite=Strict") &&
                it.contains("Path=/api/auth")
        }
    }

    @Test
    fun `should return 403 when login without csrf token`() {
        // when & then
        mockMvc
            .post("/api/auth/login") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(LoginRequest("test@example.com", "Secret-Password1!"))
            }.andExpect {
                status { isForbidden() }
            }
    }

    @Test
    fun `should return 401 when user not found`() {
        // given
        val request = LoginRequest(email = "notexisting@example.com", password = "Secret-Password1!")

        // when & then
        mockMvc
            .post("/api/auth/login") {
                withBodyRequest(request)
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.errorCode") { value("BAD_CREDENTIALS") }
            }
    }

    @Test
    fun `should return 401 when password is wrong`() {
        // given
        createUser()
        val request = LoginRequest(email = "test@example.com", password = "Wrong-Password1!")

        // when & then
        mockMvc
            .post("/api/auth/login") {
                withBodyRequest(request)
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `should return 403 when account is disabled`() {
        // given
        val password = "Secret-Password1!"
        val role = roleRepository.save(RoleModel("ADMIN", null))
        userRepository.save(
            UserModel(
                email = "test@example.com",
                passwordHash = passwordEncoder.encode(password),
                firstName = "Test",
                lastName = "User",
                role = role,
            ),
        )
        val request = LoginRequest(email = "test@example.com", password = password)

        // when & then
        mockMvc
            .post("/api/auth/login") {
                withBodyRequest(request)
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.errorCode") { value("ACCOUNT_DISABLED") }
            }
    }

    @Test
    fun `should return 400 when email is invalid`() {
        // given
        val request = LoginRequest(email = "not-an-email", password = "Secret-Password1!")

        // when & then
        mockMvc
            .post("/api/auth/login") {
                withBodyRequest(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `should return 400 when password is blank`() {
        // given
        val request = LoginRequest(email = "test@example.com", password = "")

        // when & then
        mockMvc
            .post("/api/auth/login") {
                withBodyRequest(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `should refresh and rotate token when valid refresh cookie provided`() {
        // given
        val user = createUser()
        createRefreshToken(user, rawToken = "test-refresh-token")

        // when
        val result =
            mockMvc
                .post("/api/auth/refresh") {
                    with(csrf())
                    cookie(Cookie("refresh_token", "test-refresh-token"))
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.accessToken") { doesNotExist() }
                    jsonPath("$.expiresIn") { isNumber() }
                    jsonPath("$.user.email") { value("test@example.com") }
                }.andReturn()

        // then
        val cookies = setCookies(result)
        assertThat(cookies).anyMatch { it.startsWith("access_token=") }
        assertThat(cookies).anyMatch {
            it.startsWith("refresh_token=") && !it.startsWith("refresh_token=test-refresh-token")
        }

        val tokens = refreshTokenRepository.findAll()
        assertThat(tokens).hasSize(2)
        val old = tokens.first { jwtService.hashToken("test-refresh-token") == it.tokenHash }
        val rotated = tokens.first { jwtService.hashToken("test-refresh-token") != it.tokenHash }
        assertThat(old.revoked).isTrue()
        assertThat(rotated.revoked).isFalse()
        assertThat(rotated.familyId).isEqualTo(old.familyId)
    }

    @Test
    fun `should revoke whole family when rotated refresh token is reused`() {
        // given
        val user = createUser()
        createRefreshToken(user, rawToken = "test-refresh-token")

        // first refresh rotates the token
        val firstResult =
            mockMvc
                .post("/api/auth/refresh") {
                    with(csrf())
                    cookie(Cookie("refresh_token", "test-refresh-token"))
                }.andExpect {
                    status { isOk() }
                }.andReturn()
        val rotatedCookie =
            firstResult.response
                .getHeaders(HttpHeaders.SET_COOKIE)
                .first { it.startsWith("refresh_token=") }
                .substringAfter("refresh_token=")
                .substringBefore(";")

        // when — reuse of the already rotated token
        mockMvc
            .post("/api/auth/refresh") {
                with(csrf())
                cookie(Cookie("refresh_token", "test-refresh-token"))
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.errorCode") { value("INVALID_REFRESH_TOKEN") }
            }

        // then — whole family revoked, including the newest token
        val tokens = refreshTokenRepository.findAll()
        assertThat(tokens).hasSize(2)
        assertThat(tokens).allMatch { it.revoked }

        // and the rotated cookie is rejected as well
        mockMvc
            .post("/api/auth/refresh") {
                with(csrf())
                cookie(Cookie("refresh_token", rotatedCookie))
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `should return 401 when refresh cookie is missing`() {
        // when & then
        mockMvc
            .post("/api/auth/refresh") {
                with(csrf())
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.errorCode") { value("INVALID_REFRESH_TOKEN") }
            }
    }

    @Test
    fun `should return 400 when refresh token not found`() {
        // when & then
        mockMvc
            .post("/api/auth/refresh") {
                with(csrf())
                cookie(Cookie("refresh_token", "non-existent-token"))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode") { value("REFRESH_TOKEN_NOT_FOUND") }
            }
    }

    @Test
    fun `should return 401 and revoke family when refresh token is revoked`() {
        // given
        val user = createUser()
        createRefreshToken(user, rawToken = "test-refresh-token", revoked = true)
        createRefreshToken(user, rawToken = "sibling-token")

        // when & then
        mockMvc
            .post("/api/auth/refresh") {
                with(csrf())
                cookie(Cookie("refresh_token", "test-refresh-token"))
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.errorCode") { value("INVALID_REFRESH_TOKEN") }
            }

        assertThat(refreshTokenRepository.findAll()).allMatch { it.revoked }
    }

    @Test
    fun `should return 401 when refresh token is expired`() {
        // given
        val user = createUser()
        createRefreshToken(
            user,
            rawToken = "test-refresh-token",
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS),
        )

        // when & then
        mockMvc
            .post("/api/auth/refresh") {
                with(csrf())
                cookie(Cookie("refresh_token", "test-refresh-token"))
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.errorCode") { value("INVALID_REFRESH_TOKEN") }
            }
    }

    @Test
    fun `should logout successfully and revoke all refresh tokens and clear cookies`() {
        // given
        val user = createUser()
        val accessToken = jwtService.generateAccessToken(user)
        createRefreshToken(user, rawToken = "token-1")
        createRefreshToken(user, rawToken = "token-2", familyId = "family-2")

        // when
        val result =
            mockMvc
                .post("/api/auth/logout") {
                    with(csrf())
                    cookie(Cookie("access_token", accessToken))
                }.andExpect {
                    status { isNoContent() }
                }.andReturn()

        // then
        val tokens = refreshTokenRepository.findAll()
        assertThat(tokens).allMatch { it.revoked }

        val cookies = setCookies(result)
        assertThat(cookies).anyMatch { it.startsWith("access_token=") && it.contains("Max-Age=0") }
        assertThat(cookies).anyMatch { it.startsWith("refresh_token=") && it.contains("Max-Age=0") }
    }

    @Test
    fun `should return 401 when logout without authentication`() {
        // when & then
        mockMvc
            .post("/api/auth/logout") {
                with(csrf())
            }.andExpect {
                status { isUnauthorized() }
            }
    }
}
