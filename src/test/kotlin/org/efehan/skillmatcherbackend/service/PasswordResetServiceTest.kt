package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.efehan.skillmatcherbackend.config.properties.PasswordResetProperties
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.auth.PasswordResetService
import org.efehan.skillmatcherbackend.core.auth.PasswordValidationService
import org.efehan.skillmatcherbackend.core.auth.RefreshTokenService
import org.efehan.skillmatcherbackend.core.mail.EmailService
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.PasswordResetTokenModel
import org.efehan.skillmatcherbackend.persistence.PasswordResetTokenRepository
import org.efehan.skillmatcherbackend.persistence.RoleModel
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidTokenException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@ExtendWith(MockKExtension::class)
@DisplayName("Password Reset Service Unit Tests")
class PasswordResetServiceTest {
    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var passwordResetTokenRepository: PasswordResetTokenRepository

    @MockK
    private lateinit var jwtService: JwtService

    @MockK
    private lateinit var emailService: EmailService

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK
    private lateinit var passwordValidationService: PasswordValidationService

    @MockK
    private lateinit var passwordResetProperties: PasswordResetProperties

    @MockK
    private lateinit var refreshTokenService: RefreshTokenService

    private lateinit var passwordResetService: PasswordResetService

    companion object {
        private val FIXED_INSTANT: Instant = Instant.parse("2025-01-01T12:00:00Z")
        private const val EMAIL = "test@example.com"
        private const val RAW_TOKEN = "raw-reset-token"
        private const val TOKEN_HASH = "hashed-reset-token"
        private const val NEW_PASSWORD = "NewSecret-Password1!"
        private const val ENCODED_PASSWORD = "encoded-password-hash"
        private const val TOKEN_EXPIRATION_HOURS = 24L
    }

    private val fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        every { passwordResetProperties.tokenExpirationHours } returns TOKEN_EXPIRATION_HOURS

        passwordResetService =
            PasswordResetService(
                userService = UserService(userRepository),
                passwordResetTokenRepository = passwordResetTokenRepository,
                jwtService = jwtService,
                emailService = emailService,
                passwordEncoder = passwordEncoder,
                passwordValidationService = passwordValidationService,
                passwordResetProperties = passwordResetProperties,
                clock = fixedClock,
                refreshTokenService = refreshTokenService,
            )
    }

    private fun buildTestUser(
        email: String = EMAIL,
        isEnabled: Boolean = true,
    ): UserModel {
        val role = RoleModel("ADMIN", null)
        val user =
            UserModel(
                email = email,
                passwordHash = "old-password-hash",
                firstName = "Test",
                lastName = "User",
                role = role,
            )
        user.isEnabled = isEnabled
        return user
    }

    @Nested
    @DisplayName("Request Password Reset")
    inner class RequestPasswordReset {
        @Test
        fun `should generate token and send email for valid user`() {
            // given
            val user = buildTestUser()
            val tokenSlot = slot<PasswordResetTokenModel>()

            every { userRepository.findByEmail(EMAIL) } returns user
            every { passwordResetTokenRepository.invalidateAllUserTokens(user.id) } just runs
            every { jwtService.generateOpaqueRefreshToken() } returns RAW_TOKEN
            every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
            every { passwordResetTokenRepository.save(capture(tokenSlot)) } returnsArgument 0
            every { emailService.sendPasswordResetEmail(any(), any(), any()) } just runs

            // when
            passwordResetService.requestPasswordReset(EMAIL)

            // then
            val saved = tokenSlot.captured
            assertThat(saved.tokenHash).isEqualTo(TOKEN_HASH)
            assertThat(saved.user).isEqualTo(user)
            assertThat(saved.expiresAt).isEqualTo(FIXED_INSTANT.plusSeconds(TOKEN_EXPIRATION_HOURS * 3600))
            assertThat(saved.used).isFalse()

            verify {
                emailService.sendPasswordResetEmail(user, RAW_TOKEN, TOKEN_EXPIRATION_HOURS)
            }
        }

        @Test
        fun `should invalidate existing tokens before creating new one`() {
            // given
            val user = buildTestUser()

            every { userRepository.findByEmail(EMAIL) } returns user
            every { passwordResetTokenRepository.invalidateAllUserTokens(user.id) } just runs
            every { jwtService.generateOpaqueRefreshToken() } returns RAW_TOKEN
            every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
            every { passwordResetTokenRepository.save(any()) } returnsArgument 0
            every { emailService.sendPasswordResetEmail(any(), any(), any()) } just runs

            // when
            passwordResetService.requestPasswordReset(EMAIL)

            // then
            verify(exactly = 1) { passwordResetTokenRepository.invalidateAllUserTokens(user.id) }
        }

        @Test
        fun `should not throw when user not found - prevents email enumeration`() {
            // given
            every { userRepository.findByEmail(EMAIL) } returns null

            // when & then - should not throw
            passwordResetService.requestPasswordReset(EMAIL)

            verify(exactly = 0) { passwordResetTokenRepository.save(any()) }
            verify(exactly = 0) { emailService.sendPasswordResetEmail(any(), any(), any()) }
        }

        @Test
        fun `should not throw when user is disabled - prevents email enumeration`() {
            // given
            val user = buildTestUser(isEnabled = false)

            every { userRepository.findByEmail(EMAIL) } returns user

            // when & then - should not throw
            passwordResetService.requestPasswordReset(EMAIL)

            verify(exactly = 0) { passwordResetTokenRepository.save(any()) }
            verify(exactly = 0) { emailService.sendPasswordResetEmail(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("Validate Token")
    inner class ValidateToken {
        @Test
        fun `should return valid response for valid token`() {
            // given
            val user = buildTestUser()
            val resetToken =
                PasswordResetTokenModel(
                    tokenHash = TOKEN_HASH,
                    user = user,
                    expiresAt = FIXED_INSTANT.plus(12, ChronoUnit.HOURS),
                    used = false,
                )

            every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
            every { passwordResetTokenRepository.findByTokenHash(TOKEN_HASH) } returns resetToken

            // when
            val result = passwordResetService.validateToken(RAW_TOKEN)

            // then
            assertThat(result.valid).isTrue()
            assertThat(result.email).isEqualTo("t***t@example.com") // masked email
        }

        @Test
        fun `should return invalid when token not found`() {
            // given
            every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
            every { passwordResetTokenRepository.findByTokenHash(TOKEN_HASH) } returns null

            // when
            val result = passwordResetService.validateToken(RAW_TOKEN)

            // then
            assertThat(result.valid).isFalse()
            assertThat(result.email).isNull()
        }

        @Test
        fun `should return invalid when token already used`() {
            // given
            val user = buildTestUser()
            val resetToken =
                PasswordResetTokenModel(
                    tokenHash = TOKEN_HASH,
                    user = user,
                    expiresAt = FIXED_INSTANT.plus(12, ChronoUnit.HOURS),
                    used = true,
                )

            every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
            every { passwordResetTokenRepository.findByTokenHash(TOKEN_HASH) } returns resetToken

            // when
            val result = passwordResetService.validateToken(RAW_TOKEN)

            // then
            assertThat(result.valid).isFalse()
            assertThat(result.email).isNull()
        }

        @Test
        fun `should return invalid when token expired`() {
            // given
            val user = buildTestUser()
            val resetToken =
                PasswordResetTokenModel(
                    tokenHash = TOKEN_HASH,
                    user = user,
                    expiresAt = FIXED_INSTANT.minus(1, ChronoUnit.HOURS),
                    used = false,
                )

            every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
            every { passwordResetTokenRepository.findByTokenHash(TOKEN_HASH) } returns resetToken

            // when
            val result = passwordResetService.validateToken(RAW_TOKEN)

            // then
            assertThat(result.valid).isFalse()
            assertThat(result.email).isNull()
        }

        @Test
        fun `should mask email correctly for short local part`() {
            // given
            val user = buildTestUser(email = "ab@example.com")
            val resetToken =
                PasswordResetTokenModel(
                    tokenHash = TOKEN_HASH,
                    user = user,
                    expiresAt = FIXED_INSTANT.plus(12, ChronoUnit.HOURS),
                    used = false,
                )

            every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
            every { passwordResetTokenRepository.findByTokenHash(TOKEN_HASH) } returns resetToken

            // when
            val result = passwordResetService.validateToken(RAW_TOKEN)

            // then
            assertThat(result.email).isEqualTo("a***@example.com")
        }
    }

    @Nested
    @DisplayName("Reset Password")
    inner class ResetPassword {
        @Test
        fun `should reset password successfully with valid token`() {
            // given
            val user = buildTestUser()
            val resetToken =
                PasswordResetTokenModel(
                    tokenHash = TOKEN_HASH,
                    user = user,
                    expiresAt = FIXED_INSTANT.plus(12, ChronoUnit.HOURS),
                    used = false,
                )

            every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
            every { passwordResetTokenRepository.findByTokenHash(TOKEN_HASH) } returns resetToken
            every { passwordValidationService.validateOrThrow(NEW_PASSWORD) } just runs
            every { passwordEncoder.encode(NEW_PASSWORD) } returns ENCODED_PASSWORD
            every { userRepository.save(user) } returns user
            every { refreshTokenService.revokeAllForUser(user.id) } just runs
            every { passwordResetTokenRepository.save(resetToken) } returns resetToken

            // when
            passwordResetService.resetPassword(RAW_TOKEN, NEW_PASSWORD)

            // then
            assertThat(user.passwordHash).isEqualTo(ENCODED_PASSWORD)
            assertThat(resetToken.used).isTrue()

            verify { userRepository.save(user) }
            verify { refreshTokenService.revokeAllForUser(user.id) }
            verify { passwordResetTokenRepository.save(resetToken) }
        }

        @Test
        fun `should throw when token not found`() {
            // given
            every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
            every { passwordResetTokenRepository.findByTokenHash(TOKEN_HASH) } returns null

            // then
            assertThatThrownBy {
                passwordResetService.resetPassword(RAW_TOKEN, NEW_PASSWORD)
            }.isInstanceOf(InvalidTokenException::class.java)
                .satisfies({ ex ->
                    val e = ex as InvalidTokenException
                    assertThat(e.errorCode).isEqualTo(GlobalErrorCode.INVALID_PASSWORD_RESET_TOKEN)
                    assertThat(e.status).isEqualTo(HttpStatus.BAD_REQUEST)
                })
        }

        @Test
        fun `should throw when token already used`() {
            // given
            val user = buildTestUser()
            val resetToken =
                PasswordResetTokenModel(
                    tokenHash = TOKEN_HASH,
                    user = user,
                    expiresAt = FIXED_INSTANT.plus(12, ChronoUnit.HOURS),
                    used = true,
                )

            every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
            every { passwordResetTokenRepository.findByTokenHash(TOKEN_HASH) } returns resetToken

            // then
            assertThatThrownBy {
                passwordResetService.resetPassword(RAW_TOKEN, NEW_PASSWORD)
            }.isInstanceOf(InvalidTokenException::class.java)
                .satisfies({ ex ->
                    val e = ex as InvalidTokenException
                    assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PASSWORD_RESET_TOKEN_USED)
                    assertThat(e.status).isEqualTo(HttpStatus.BAD_REQUEST)
                })
        }

        @Test
        fun `should throw when token expired`() {
            // given
            val user = buildTestUser()
            val resetToken =
                PasswordResetTokenModel(
                    tokenHash = TOKEN_HASH,
                    user = user,
                    expiresAt = FIXED_INSTANT.minus(1, ChronoUnit.HOURS),
                    used = false,
                )

            every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
            every { passwordResetTokenRepository.findByTokenHash(TOKEN_HASH) } returns resetToken

            // then
            assertThatThrownBy {
                passwordResetService.resetPassword(RAW_TOKEN, NEW_PASSWORD)
            }.isInstanceOf(InvalidTokenException::class.java)
                .satisfies({ ex ->
                    val e = ex as InvalidTokenException
                    assertThat(e.errorCode).isEqualTo(GlobalErrorCode.PASSWORD_RESET_TOKEN_EXPIRED)
                    assertThat(e.status).isEqualTo(HttpStatus.BAD_REQUEST)
                })
        }

        @Test
        fun `should validate password before resetting`() {
            // given
            val user = buildTestUser()
            val resetToken =
                PasswordResetTokenModel(
                    tokenHash = TOKEN_HASH,
                    user = user,
                    expiresAt = FIXED_INSTANT.plus(12, ChronoUnit.HOURS),
                    used = false,
                )

            every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
            every { passwordResetTokenRepository.findByTokenHash(TOKEN_HASH) } returns resetToken
            every { passwordValidationService.validateOrThrow(NEW_PASSWORD) } just runs
            every { passwordEncoder.encode(NEW_PASSWORD) } returns ENCODED_PASSWORD
            every { userRepository.save(user) } returns user
            every { refreshTokenService.revokeAllForUser(user.id) } just runs
            every { passwordResetTokenRepository.save(resetToken) } returns resetToken

            // when
            passwordResetService.resetPassword(RAW_TOKEN, NEW_PASSWORD)

            // then
            verify { passwordValidationService.validateOrThrow(NEW_PASSWORD) }
        }
    }
}
