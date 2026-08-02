package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.efehan.skillmatcherbackend.config.properties.JwtProperties
import org.efehan.skillmatcherbackend.config.properties.LoginLockoutProperties
import org.efehan.skillmatcherbackend.core.audit.AuditService
import org.efehan.skillmatcherbackend.core.auth.AuthenticationService
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.auth.PasswordValidationService
import org.efehan.skillmatcherbackend.core.auth.RefreshTokenService
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.RefreshTokenModel
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.shared.exceptions.AccountLockedException
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidCredentialsException
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidTokenException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@ExtendWith(MockKExtension::class)
@DisplayName("Authentication Service Unit Tests")
class AuthenticationServiceTest {
    @MockK
    private lateinit var userService: UserService

    @MockK
    private lateinit var authenticationManager: AuthenticationManager

    @MockK
    private lateinit var jwtService: JwtService

    @MockK
    private lateinit var refreshTokenService: RefreshTokenService

    @MockK
    private lateinit var jwtProperties: JwtProperties

    @MockK
    private lateinit var loginLockoutProperties: LoginLockoutProperties

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK
    private lateinit var passwordValidationService: PasswordValidationService

    @MockK(relaxed = true)
    private lateinit var auditService: AuditService

    @MockK(relaxed = true)
    private lateinit var transactionManager: org.springframework.transaction.PlatformTransactionManager

    @MockK
    private lateinit var clock: Clock

    @InjectMockKs
    private lateinit var authenticationService: AuthenticationService

    companion object {
        private const val EMAIL = "test@example.com"
        private const val PASSWORD = "Secret-password1"
        private const val ACCESS_TOKEN = "access-token-jwt"
        private const val REFRESH_TOKEN = "refresh-token-uuid"
        private const val REFRESH_TOKEN_HASH = "hashed-refresh-token"
        private const val ACCESS_TOKEN_EXPIRATION = 900_000L
        private val FIXED_INSTANT: Instant = Instant.parse("2025-01-01T12:00:00Z")
    }

    @BeforeEach
    fun setUp() {
        every { clock.instant() } returns FIXED_INSTANT
        every { jwtProperties.accessTokenExpiration } returns ACCESS_TOKEN_EXPIRATION
        every { loginLockoutProperties.maxFailedAttempts } returns 5
        every { loginLockoutProperties.lockoutDurationMinutes } returns 15
    }

    @Test
    fun `login successfully with correct credentials`() {
        // given
        val user = UserBuilder().build(email = EMAIL, firstName = "Test", lastName = "User", role = RoleModel("ADMIN", null))

        every { userService.findByEmail(EMAIL) } returns user
        every { authenticationManager.authenticate(any()) } returns mockk()
        every { jwtService.generateAccessToken(user) } returns ACCESS_TOKEN
        every { refreshTokenService.issue(user) } returns REFRESH_TOKEN

        // when
        val result = authenticationService.login(EMAIL, PASSWORD)

        // then
        assertThat(result.accessToken).isEqualTo(ACCESS_TOKEN)
        assertThat(result.refreshToken).isEqualTo(REFRESH_TOKEN)
        assertThat(result.response.expiresIn).isEqualTo(ACCESS_TOKEN_EXPIRATION)
        assertThat(result.response.user.email).isEqualTo(EMAIL)
        assertThat(result.response.user.firstName).isEqualTo("Test")
        assertThat(result.response.user.lastName).isEqualTo("User")
        assertThat(result.response.user.role).isEqualTo("ADMIN")
    }

    @Test
    fun `login calls authenticationManager with correct credentials`() {
        // given
        val user = UserBuilder().build(email = EMAIL, firstName = "Test", lastName = "User", role = RoleModel("ADMIN", null))

        every { userService.findByEmail(EMAIL) } returns user
        every { authenticationManager.authenticate(any()) } returns mockk()
        every { jwtService.generateAccessToken(user) } returns ACCESS_TOKEN
        every { refreshTokenService.issue(user) } returns REFRESH_TOKEN

        // when
        authenticationService.login(EMAIL, PASSWORD)

        // then
        verify {
            authenticationManager.authenticate(
                withArg {
                    assertThat(it.principal).isEqualTo(EMAIL)
                    assertThat(it.credentials).isEqualTo(PASSWORD)
                },
            )
        }
    }

    @Test
    fun `login throws InvalidCredentialsException when user not found`() {
        // given
        every { userService.findByEmail(EMAIL) } returns null
        every { passwordEncoder.encode(any()) } returns "dummy-hash"
        every { passwordEncoder.matches(any(), any()) } returns false

        // then
        assertThatThrownBy {
            authenticationService.login(EMAIL, PASSWORD)
        }.isInstanceOf(InvalidCredentialsException::class.java)
            .satisfies({ ex ->
                val e = ex as InvalidCredentialsException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.BAD_CREDENTIALS)
                assertThat(e.status).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED)
            })

        // timing equalization: unknown users still cost one bcrypt comparison
        verify(exactly = 1) { passwordEncoder.matches(PASSWORD, any()) }
        verify(exactly = 0) { authenticationManager.authenticate(any()) }
        verify(exactly = 0) { jwtService.generateAccessToken(any()) }
    }

    @Test
    fun `login propagates DisabledException from authenticationManager`() {
        // given
        val user =
            UserBuilder().build(
                email = EMAIL,
                firstName = "Test",
                lastName = "User",
                role = RoleModel("ADMIN", null),
                isEnabled = false,
            )

        every { userService.findByEmail(EMAIL) } returns user
        every { authenticationManager.authenticate(any()) } throws DisabledException("User is disabled")

        // then
        assertThatThrownBy {
            authenticationService.login(EMAIL, PASSWORD)
        }.isInstanceOf(DisabledException::class.java)

        verify(exactly = 0) { jwtService.generateAccessToken(any()) }
    }

    @Test
    fun `login throws AccountLockedException when account is locked`() {
        // given
        val user =
            UserBuilder().build(
                email = EMAIL,
                firstName = "Test",
                lastName = "User",
                role = RoleModel("ADMIN", null),
            )
        user.lockedUntil = FIXED_INSTANT.plus(10, ChronoUnit.MINUTES)

        every { userService.findByEmail(EMAIL) } returns user

        // then
        assertThatThrownBy {
            authenticationService.login(EMAIL, PASSWORD)
        }.isInstanceOf(AccountLockedException::class.java)
            .satisfies({ ex ->
                val e = ex as AccountLockedException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.ACCOUNT_LOCKED)
                assertThat(e.status).isEqualTo(org.springframework.http.HttpStatus.LOCKED)
            })

        verify(exactly = 0) { authenticationManager.authenticate(any()) }
        verify(exactly = 0) { jwtService.generateAccessToken(any()) }
    }

    @Test
    fun `login increments failed attempts on bad credentials`() {
        // given
        val user = UserBuilder().build(email = EMAIL, firstName = "Test", lastName = "User", role = RoleModel("ADMIN", null))

        every { userService.findByEmail(EMAIL) } returns user
        every { userService.findById(user.id) } returns user
        every { authenticationManager.authenticate(any()) } throws BadCredentialsException("Bad credentials")
        every { userService.save(any()) } returnsArgument 0

        // when
        assertThatThrownBy {
            authenticationService.login(EMAIL, PASSWORD)
        }.isInstanceOf(BadCredentialsException::class.java)

        // then
        assertThat(user.failedLoginAttempts).isEqualTo(1)
        assertThat(user.lockedUntil).isNull()
        verify(exactly = 1) { userService.save(user) }
    }

    @Test
    fun `login locks account after max failed attempts`() {
        // given
        val user = UserBuilder().build(email = EMAIL, firstName = "Test", lastName = "User", role = RoleModel("ADMIN", null))
        user.failedLoginAttempts = 4

        every { userService.findByEmail(EMAIL) } returns user
        every { userService.findById(user.id) } returns user
        every { authenticationManager.authenticate(any()) } throws BadCredentialsException("Bad credentials")
        every { userService.save(any()) } returnsArgument 0

        // when
        assertThatThrownBy {
            authenticationService.login(EMAIL, PASSWORD)
        }.isInstanceOf(BadCredentialsException::class.java)

        // then
        assertThat(user.lockedUntil).isEqualTo(FIXED_INSTANT.plus(15, ChronoUnit.MINUTES))
        assertThat(user.failedLoginAttempts).isEqualTo(0)
        verify(exactly = 1) { userService.save(user) }
    }

    @Test
    fun `login resets failed attempts on success`() {
        // given
        val user = UserBuilder().build(email = EMAIL, firstName = "Test", lastName = "User", role = RoleModel("ADMIN", null))
        user.failedLoginAttempts = 3

        every { userService.findByEmail(EMAIL) } returns user
        every { authenticationManager.authenticate(any()) } returns mockk()
        every { jwtService.generateAccessToken(user) } returns ACCESS_TOKEN
        every { refreshTokenService.issue(user) } returns REFRESH_TOKEN
        every { userService.save(any()) } returnsArgument 0

        // when
        authenticationService.login(EMAIL, PASSWORD)

        // then
        assertThat(user.failedLoginAttempts).isEqualTo(0)
        assertThat(user.lockedUntil).isNull()
        verify(exactly = 1) { userService.save(user) }
    }

    @Test
    fun `login does not save user when no prior failed attempts`() {
        // given
        val user = UserBuilder().build(email = EMAIL, firstName = "Test", lastName = "User", role = RoleModel("ADMIN", null))

        every { userService.findByEmail(EMAIL) } returns user
        every { authenticationManager.authenticate(any()) } returns mockk()
        every { jwtService.generateAccessToken(user) } returns ACCESS_TOKEN
        every { refreshTokenService.issue(user) } returns REFRESH_TOKEN

        // when
        authenticationService.login(EMAIL, PASSWORD)

        // then
        verify(exactly = 0) { userService.save(any()) }
    }

    @Test
    fun `login allows attempt again after lock expired`() {
        // given
        val user = UserBuilder().build(email = EMAIL, firstName = "Test", lastName = "User", role = RoleModel("ADMIN", null))
        user.lockedUntil = FIXED_INSTANT.minus(1, ChronoUnit.MINUTES)

        every { userService.findByEmail(EMAIL) } returns user
        every { authenticationManager.authenticate(any()) } returns mockk()
        every { jwtService.generateAccessToken(user) } returns ACCESS_TOKEN
        every { refreshTokenService.issue(user) } returns REFRESH_TOKEN
        every { userService.save(any()) } returnsArgument 0

        // when
        val result = authenticationService.login(EMAIL, PASSWORD)

        // then
        assertThat(result.accessToken).isEqualTo(ACCESS_TOKEN)
        assertThat(user.lockedUntil).isNull()
        verify(exactly = 1) { userService.save(user) }
    }

    @Test
    fun `login throws when authenticationManager rejects credentials`() {
        // given
        val user = UserBuilder().build(email = EMAIL, firstName = "Test", lastName = "User", role = RoleModel("ADMIN", null))

        every { userService.findByEmail(EMAIL) } returns user
        every { userService.findById(user.id) } returns user
        every { authenticationManager.authenticate(any()) } throws BadCredentialsException("Bad credentials")
        every { userService.save(any()) } returnsArgument 0

        // then
        assertThatThrownBy {
            authenticationService.login(EMAIL, PASSWORD)
        }.isInstanceOf(BadCredentialsException::class.java)

        verify(exactly = 0) { jwtService.generateAccessToken(any()) }
    }

    @Test
    fun `refreshToken hands the valid token to rotation and returns the new pair`() {
        // given
        val user = UserBuilder().build(email = EMAIL, firstName = "Test", lastName = "User", role = RoleModel("ADMIN", null))
        val newRefreshToken = "new-refresh-token-uuid"
        val existingToken =
            RefreshTokenModel(
                tokenHash = REFRESH_TOKEN_HASH,
                user = user,
                expiresAt = FIXED_INSTANT.plus(7, ChronoUnit.DAYS),
                familyId = "family-1",
            )

        every { refreshTokenService.findByRawToken(REFRESH_TOKEN) } returns existingToken
        every { jwtService.generateAccessToken(user) } returns ACCESS_TOKEN
        every { refreshTokenService.rotate(existingToken) } returns newRefreshToken

        // when
        val result = authenticationService.refreshToken(REFRESH_TOKEN)

        // then
        assertThat(result.accessToken).isEqualTo(ACCESS_TOKEN)
        assertThat(result.refreshToken).isEqualTo(newRefreshToken)
        assertThat(result.response.expiresIn).isEqualTo(ACCESS_TOKEN_EXPIRATION)
        assertThat(result.response.user.email).isEqualTo(EMAIL)
    }

    @Test
    fun `refreshToken throws InvalidTokenException when token not found`() {
        // given
        every { refreshTokenService.findByRawToken("unknown-token") } returns null

        // then
        assertThatThrownBy {
            authenticationService.refreshToken("unknown-token")
        }.isInstanceOf(InvalidTokenException::class.java)
            .satisfies({ ex ->
                val e = ex as InvalidTokenException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.REFRESH_TOKEN_NOT_FOUND)
                assertThat(e.status).isEqualTo(HttpStatus.UNAUTHORIZED)
            })

        verify(exactly = 0) { jwtService.generateAccessToken(any()) }
    }

    @Test
    fun `refreshToken revokes whole family when revoked token is reused`() {
        // given
        val user = UserBuilder().build(email = EMAIL, firstName = "Test", lastName = "User", role = RoleModel("ADMIN", null))
        val revokedToken =
            RefreshTokenModel(
                tokenHash = REFRESH_TOKEN_HASH,
                user = user,
                expiresAt = FIXED_INSTANT.plus(7, ChronoUnit.DAYS),
                familyId = "family-1",
                revoked = true,
            )

        every { refreshTokenService.findByRawToken(REFRESH_TOKEN) } returns revokedToken
        every { refreshTokenService.revokeFamily("family-1") } returns Unit

        // then
        assertThatThrownBy {
            authenticationService.refreshToken(REFRESH_TOKEN)
        }.isInstanceOf(InvalidTokenException::class.java)
            .satisfies({ ex ->
                val e = ex as InvalidTokenException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.INVALID_REFRESH_TOKEN)
            })

        verify(exactly = 1) { refreshTokenService.revokeFamily("family-1") }
        verify(exactly = 0) { jwtService.generateAccessToken(any()) }
    }

    @Test
    fun `refreshToken throws InvalidTokenException when token is expired`() {
        // given
        val user = UserBuilder().build(email = EMAIL, firstName = "Test", lastName = "User", role = RoleModel("ADMIN", null))
        val expiredToken =
            RefreshTokenModel(
                tokenHash = REFRESH_TOKEN_HASH,
                user = user,
                expiresAt = FIXED_INSTANT.minus(1, ChronoUnit.HOURS),
                familyId = "family-1",
            )

        every { refreshTokenService.findByRawToken(REFRESH_TOKEN) } returns expiredToken

        // then
        assertThatThrownBy {
            authenticationService.refreshToken(REFRESH_TOKEN)
        }.isInstanceOf(InvalidTokenException::class.java)
            .satisfies({ ex ->
                val e = ex as InvalidTokenException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.INVALID_REFRESH_TOKEN)
            })

        verify(exactly = 0) { jwtService.generateAccessToken(any()) }
    }

    @Test
    fun `logout revokes all user tokens`() {
        // given
        val userId = "user-id-123"
        every { refreshTokenService.revokeAllForUser(userId) } returns Unit

        // when
        authenticationService.logout(userId)

        // then
        verify(exactly = 1) { refreshTokenService.revokeAllForUser(userId) }
    }

    @Test
    fun `changePassword revokes all user tokens`() {
        // given
        val user = UserBuilder().build(email = EMAIL, firstName = "Test", lastName = "User", role = RoleModel("ADMIN", null))
        every { passwordEncoder.matches(PASSWORD, user.passwordHash) } returns true
        every { passwordEncoder.encode("New-password1") } returns "new-hash"
        every { passwordValidationService.validateOrThrow("New-password1") } returns Unit
        every { userService.save(user) } returnsArgument 0
        every { refreshTokenService.revokeAllForUser(user.id) } returns Unit

        // when
        authenticationService.changePassword(user, PASSWORD, "New-password1")

        // then
        verify(exactly = 1) { refreshTokenService.revokeAllForUser(user.id) }
    }
}
