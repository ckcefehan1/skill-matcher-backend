package org.efehan.skillmatcherbackend.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.efehan.skillmatcherbackend.config.properties.InvitationProperties
import org.efehan.skillmatcherbackend.config.properties.JwtProperties
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.auth.PasswordValidationService
import org.efehan.skillmatcherbackend.core.invitation.InvitationService
import org.efehan.skillmatcherbackend.core.mail.EmailService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.fixtures.builder.UserBuilder
import org.efehan.skillmatcherbackend.persistence.InvitationTokenModel
import org.efehan.skillmatcherbackend.persistence.InvitationTokenRepository
import org.efehan.skillmatcherbackend.persistence.RefreshTokenRepository
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidTokenException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

@ExtendWith(MockKExtension::class)
@DisplayName("Invitation Service Unit Tests")
class InvitationServiceTest {
    @MockK
    private lateinit var invitationTokenRepository: InvitationTokenRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @MockK
    private lateinit var jwtService: JwtService

    @MockK
    private lateinit var jwtProperties: JwtProperties

    @MockK
    private lateinit var emailService: EmailService

    @MockK
    private lateinit var invitationProperties: InvitationProperties

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK
    private lateinit var passwordValidationService: PasswordValidationService

    @MockK
    private lateinit var clock: Clock

    @MockK
    private lateinit var companyRepository: org.efehan.skillmatcherbackend.persistence.CompanyRepository

    @MockK
    private lateinit var cacheManager: org.springframework.cache.CacheManager

    @MockK
    private lateinit var auditService: org.efehan.skillmatcherbackend.core.audit.AuditService

    @InjectMockKs
    private lateinit var invitationService: InvitationService

    companion object {
        private val FIXED_INSTANT: Instant = Instant.parse("2025-01-01T12:00:00Z")
        private const val RAW_TOKEN = "raw-invitation-token"
        private const val TOKEN_HASH = "hashed-invitation-token"
        private const val ACCESS_TOKEN = "access-token-jwt"
        private const val REFRESH_TOKEN = "refresh-token-uuid"
        private const val REFRESH_TOKEN_HASH = "hashed-refresh-token"
    }

    @BeforeEach
    fun setUp() {
        every { clock.instant() } returns FIXED_INSTANT
        every { invitationProperties.tokenExpirationHours } returns 72L
        every { jwtProperties.accessTokenExpiration } returns 900_000L
        every { jwtProperties.refreshTokenExpiration } returns 604_800_000L
    }

    @Test
    fun `createAndSendInvitation saves token and sends email`() {
        // given
        val user = UserBuilder().build(passwordHash = null, firstName = null, lastName = null, isEnabled = false)
        val tokenSlot = slot<InvitationTokenModel>()

        every { jwtService.generateOpaqueRefreshToken() } returns RAW_TOKEN
        every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
        every { invitationTokenRepository.save(capture(tokenSlot)) } returnsArgument 0
        every { emailService.sendInvitationEmail(any(), any(), any()) } just runs

        // when
        invitationService.createAndSendInvitation(user)

        // then
        val saved = tokenSlot.captured
        assertThat(saved.tokenHash).isEqualTo(TOKEN_HASH)
        assertThat(saved.user).isEqualTo(user)
        assertThat(saved.expiresAt).isEqualTo(FIXED_INSTANT.plus(72, ChronoUnit.HOURS))
        assertThat(saved.used).isFalse()

        verify {
            emailService.sendInvitationEmail(user, RAW_TOKEN, 72L)
        }
    }

    @Test
    fun `validateInvitation returns invitation for valid token`() {
        // given
        val user = UserBuilder().build(passwordHash = null, firstName = null, lastName = null, isEnabled = false)
        val invitation =
            InvitationTokenModel(
                tokenHash = TOKEN_HASH,
                user = user,
                expiresAt = FIXED_INSTANT.plus(24, ChronoUnit.HOURS),
                used = false,
            )

        every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
        every { invitationTokenRepository.findByTokenHash(TOKEN_HASH) } returns invitation

        // when
        val result = invitationService.validateInvitation(RAW_TOKEN)

        // then
        assertThat(result).isEqualTo(invitation)
        assertThat(result.user.email).isEqualTo("max@firma.de")
    }

    @Test
    fun `validateInvitation throws when token not found`() {
        // given
        every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
        every { invitationTokenRepository.findByTokenHash(TOKEN_HASH) } returns null

        // then
        assertThatThrownBy {
            invitationService.validateInvitation(RAW_TOKEN)
        }.isInstanceOf(InvalidTokenException::class.java)
            .satisfies({ ex ->
                val e = ex as InvalidTokenException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.INVALID_INVITATION_TOKEN)
            })
    }

    @Test
    fun `validateInvitation throws when token already used`() {
        // given
        val user = UserBuilder().build(passwordHash = null, firstName = null, lastName = null, isEnabled = false)
        val invitation =
            InvitationTokenModel(
                tokenHash = TOKEN_HASH,
                user = user,
                expiresAt = FIXED_INSTANT.plus(24, ChronoUnit.HOURS),
                used = true,
            )

        every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
        every { invitationTokenRepository.findByTokenHash(TOKEN_HASH) } returns invitation

        // then
        assertThatThrownBy {
            invitationService.validateInvitation(RAW_TOKEN)
        }.isInstanceOf(InvalidTokenException::class.java)
            .satisfies({ ex ->
                val e = ex as InvalidTokenException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.INVITATION_ALREADY_ACCEPTED)
            })
    }

    @Test
    fun `validateInvitation throws when token expired`() {
        // given
        val user = UserBuilder().build(passwordHash = null, firstName = null, lastName = null, isEnabled = false)
        val invitation =
            InvitationTokenModel(
                tokenHash = TOKEN_HASH,
                user = user,
                expiresAt = FIXED_INSTANT.minus(1, ChronoUnit.HOURS),
                used = false,
            )

        every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
        every { invitationTokenRepository.findByTokenHash(TOKEN_HASH) } returns invitation

        // then
        assertThatThrownBy {
            invitationService.validateInvitation(RAW_TOKEN)
        }.isInstanceOf(InvalidTokenException::class.java)
            .satisfies({ ex ->
                val e = ex as InvalidTokenException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.INVITATION_TOKEN_EXPIRED)
            })
    }

    @Test
    fun `acceptInvitation succeeds with valid token`() {
        // given
        val user = UserBuilder().build(passwordHash = null, firstName = null, lastName = null, isEnabled = false)
        val invitation =
            InvitationTokenModel(
                tokenHash = TOKEN_HASH,
                user = user,
                expiresAt = FIXED_INSTANT.plus(24, ChronoUnit.HOURS),
                used = false,
            )

        every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
        every { invitationTokenRepository.findByTokenHash(TOKEN_HASH) } returns invitation
        every { passwordValidationService.validateOrThrow(any()) } just runs
        every { passwordEncoder.encode("NewPassword1!") } returns "encoded-password"
        every { userRepository.save(user) } returns user
        every { invitationTokenRepository.save(invitation) } returns invitation
        every { jwtService.generateAccessToken(user) } returns ACCESS_TOKEN
        every { jwtService.generateOpaqueRefreshToken() } returns REFRESH_TOKEN
        every { jwtService.hashToken(REFRESH_TOKEN) } returns REFRESH_TOKEN_HASH
        every { refreshTokenRepository.save(any()) } returnsArgument 0

        // when
        val result = invitationService.acceptInvitation(RAW_TOKEN, "NewPassword1!", "Max", "Mustermann")

        // then
        assertThat(result.accessToken).isEqualTo(ACCESS_TOKEN)
        assertThat(result.refreshToken).isEqualTo(REFRESH_TOKEN)
        assertThat(user.passwordHash).isEqualTo("encoded-password")
        assertThat(user.firstName).isEqualTo("Max")
        assertThat(user.lastName).isEqualTo("Mustermann")
        assertThat(user.isEnabled).isTrue()
        assertThat(invitation.used).isTrue()
    }

    @Test
    fun `acceptInvitation throws when token not found`() {
        // given
        every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
        every { invitationTokenRepository.findByTokenHash(TOKEN_HASH) } returns null

        // then
        assertThatThrownBy {
            invitationService.acceptInvitation(RAW_TOKEN, "NewPassword1!", "Max", "Mustermann")
        }.isInstanceOf(InvalidTokenException::class.java)
            .satisfies({ ex ->
                val e = ex as InvalidTokenException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.INVALID_INVITATION_TOKEN)
            })
    }

    @Test
    fun `acceptInvitation throws when token already used`() {
        // given
        val user = UserBuilder().build(passwordHash = null, firstName = null, lastName = null, isEnabled = false)
        val invitation =
            InvitationTokenModel(
                tokenHash = TOKEN_HASH,
                user = user,
                expiresAt = FIXED_INSTANT.plus(24, ChronoUnit.HOURS),
                used = true,
            )

        every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
        every { invitationTokenRepository.findByTokenHash(TOKEN_HASH) } returns invitation

        // then
        assertThatThrownBy {
            invitationService.acceptInvitation(RAW_TOKEN, "NewPassword1!", "Max", "Mustermann")
        }.isInstanceOf(InvalidTokenException::class.java)
            .satisfies({ ex ->
                val e = ex as InvalidTokenException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.INVITATION_ALREADY_ACCEPTED)
            })
    }

    @Test
    fun `acceptInvitation throws when token expired`() {
        // given
        val user = UserBuilder().build(passwordHash = null, firstName = null, lastName = null, isEnabled = false)
        val invitation =
            InvitationTokenModel(
                tokenHash = TOKEN_HASH,
                user = user,
                expiresAt = FIXED_INSTANT.minus(1, ChronoUnit.HOURS),
                used = false,
            )

        every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
        every { invitationTokenRepository.findByTokenHash(TOKEN_HASH) } returns invitation

        // then
        assertThatThrownBy {
            invitationService.acceptInvitation(RAW_TOKEN, "NewPassword1!", "Max", "Mustermann")
        }.isInstanceOf(InvalidTokenException::class.java)
            .satisfies({ ex ->
                val e = ex as InvalidTokenException
                assertThat(e.errorCode).isEqualTo(GlobalErrorCode.INVITATION_TOKEN_EXPIRED)
            })
    }

    @Test
    fun `acceptInvitation validates password`() {
        // given
        val user = UserBuilder().build(passwordHash = null, firstName = null, lastName = null, isEnabled = false)
        val invitation =
            InvitationTokenModel(
                tokenHash = TOKEN_HASH,
                user = user,
                expiresAt = FIXED_INSTANT.plus(24, ChronoUnit.HOURS),
                used = false,
            )

        every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
        every { invitationTokenRepository.findByTokenHash(TOKEN_HASH) } returns invitation
        every { passwordValidationService.validateOrThrow("weak") } throws
            org.efehan.skillmatcherbackend.shared.exceptions.PasswordValidationException(
                errorCode = GlobalErrorCode.INVALID_PASSWORD,
                status = HttpStatus.BAD_REQUEST,
                message = "Password does not meet the required complexity.",
            )

        // then
        assertThatThrownBy {
            invitationService.acceptInvitation(RAW_TOKEN, "weak", "Max", "Mustermann")
        }.isInstanceOf(org.efehan.skillmatcherbackend.shared.exceptions.PasswordValidationException::class.java)

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `resendInvitation creates new invitation for existing user`() {
        // given
        val user = UserBuilder().build(passwordHash = null, firstName = null, lastName = null, isEnabled = false)

        every { userRepository.findById("user-id") } returns Optional.of(user)
        every { jwtService.generateOpaqueRefreshToken() } returns RAW_TOKEN
        every { jwtService.hashToken(RAW_TOKEN) } returns TOKEN_HASH
        every { invitationTokenRepository.save(any()) } returnsArgument 0
        every { emailService.sendInvitationEmail(any(), any(), any()) } just runs

        // when
        invitationService.resendInvitation("user-id")

        // then
        verify(exactly = 1) { invitationTokenRepository.save(any()) }
        verify(exactly = 1) { emailService.sendInvitationEmail(user, RAW_TOKEN, 72L) }
    }
}
