package org.efehan.skillmatcherbackend.core.invitation

import org.efehan.skillmatcherbackend.config.properties.InvitationProperties
import org.efehan.skillmatcherbackend.config.properties.JwtProperties
import org.efehan.skillmatcherbackend.core.auth.AuthResponse
import org.efehan.skillmatcherbackend.core.auth.AuthTokens
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.auth.PasswordValidationService
import org.efehan.skillmatcherbackend.core.mail.EmailService
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.InvitationTokenModel
import org.efehan.skillmatcherbackend.persistence.InvitationTokenRepository
import org.efehan.skillmatcherbackend.persistence.RefreshTokenModel
import org.efehan.skillmatcherbackend.persistence.RefreshTokenRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidTokenException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@Transactional
class InvitationService(
    private val invitationTokenRepository: InvitationTokenRepository,
    private val userService: UserService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtService: JwtService,
    private val jwtProperties: JwtProperties,
    private val emailService: EmailService,
    private val invitationProperties: InvitationProperties,
    private val passwordEncoder: PasswordEncoder,
    private val passwordValidationService: PasswordValidationService,
    private val clock: Clock,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(InvitationService::class.java)
    private val secureRandom = SecureRandom()

    fun createAndSendInvitation(user: UserModel) {
        val rawToken = jwtService.generateOpaqueRefreshToken()
        val tokenHash = jwtService.hashToken(rawToken)
        val expiresAt = Instant.now(clock).plus(invitationProperties.tokenExpirationHours, ChronoUnit.HOURS)

        invitationTokenRepository.save(
            InvitationTokenModel(
                tokenHash = tokenHash,
                user = user,
                expiresAt = expiresAt,
                // explicit: callers may run in root context (company registration, superadmin)
            ).apply { companyId = user.companyId },
        )

        emailService.sendInvitationEmail(user, rawToken, invitationProperties.tokenExpirationHours)
        logger.info("Invitation created for user={}", user.email)
    }

    /**
     * Self-registration proves email ownership with a 6-digit code typed into the
     * already-open tab instead of a link. The row shares invitation_tokens with
     * employee invitations (user, expiresAt, used are already there and the zombie
     * cleanup deletes by user); [InvitationTokenModel.codeHash] is what tells the
     * two row types apart. The two flows stay separate on purpose: a code only
     * works when requester and browser are the same person.
     */
    fun createAndSendRegistrationCode(user: UserModel) {
        val code = generateRegistrationCode()
        val expiresAt = Instant.now(clock).plus(invitationProperties.codeExpirationMinutes, ChronoUnit.MINUTES)

        invitationTokenRepository.save(
            InvitationTokenModel(
                // token_hash stays NOT NULL UNIQUE, so the code path mints an
                // opaque token that is never sent anywhere
                tokenHash = jwtService.hashToken(jwtService.generateOpaqueRefreshToken()),
                user = user,
                expiresAt = expiresAt,
                // HMAC via hashToken, not plain SHA-256: without the server secret a
                // rainbow table over the 1 Mio possible codes is useless, so the hash
                // survives a DB dump
                codeHash = jwtService.hashToken(code),
            ).apply { companyId = user.companyId },
        )

        emailService.sendRegistrationCodeEmail(user, code, invitationProperties.codeExpirationMinutes)
        logger.info("Registration code created for user={}", user.email)
    }

    /**
     * Answers unknown email and wrong code identically (and hashes in both branches)
     * so the endpoint cannot enumerate registered emails. Burns an attempt on failure
     * but never consumes the code — that is [completeRegistration]'s job.
     */
    fun verifyRegistrationCode(
        email: String,
        code: String,
    ): Boolean = consumeCodeAttempt(email, code) != null

    /**
     * Re-checks the code before setting the password: verify sets no cookie and no
     * server state, so this repeated check IS the authentication. On success the
     * regular invitation acceptance runs (event, company activation, tokens).
     * Null means "no usable code"; the caller turns that into the error response.
     */
    fun completeRegistration(
        email: String,
        code: String,
        password: String,
        firstName: String,
        lastName: String,
    ): AuthTokens? = consumeCodeAttempt(email, code)?.let { acceptInvitation(it, password, firstName, lastName) }

    /**
     * The one gate in front of both verify and complete. A cap that only verify feeds
     * is no cap at all — an attacker skips verify and guesses straight against
     * complete, which is the endpoint that hands out the tokens. Returning instead of
     * throwing is what makes the cap work: a thrown exception rolls the transaction
     * back and with it the counter increment.
     */
    private fun consumeCodeAttempt(
        email: String,
        code: String,
    ): InvitationTokenModel? {
        val codeHash = jwtService.hashToken(code)
        val invitation = findCodeInvitation(email) ?: return null
        if (invitation.isCodeUsable(codeHash)) return invitation

        invitation.attempts += 1
        invitationTokenRepository.save(invitation)
        return null
    }

    /**
     * Updates the existing row in place instead of inserting a second one: with
     * 6 digits every additional live row doubles the brute-force odds, so there is
     * exactly one live code per user (same rule as PasswordResetService).
     */
    fun resendRegistrationCode(email: String) {
        val invitation = findCodeInvitation(email) ?: return
        if (invitation.used) return
        if (invitation.isInResendCooldown()) {
            logger.debug("Registration code resend suppressed, still in cooldown")
            return
        }

        val code = generateRegistrationCode()
        invitation.codeHash = jwtService.hashToken(code)
        invitation.expiresAt = Instant.now(clock).plus(invitationProperties.codeExpirationMinutes, ChronoUnit.MINUTES)
        invitation.attempts = 0
        invitationTokenRepository.save(invitation)

        emailService.sendRegistrationCodeEmail(invitation.user, code, invitationProperties.codeExpirationMinutes)
        logger.info("Registration code re-sent for user={}", email)
    }

    private fun findCodeInvitation(email: String): InvitationTokenModel? =
        userService
            .findByEmail(email)
            ?.let { invitationTokenRepository.findFirstByUserAndCodeHashNotNullOrderByCreatedDateDesc(it) }

    private fun InvitationTokenModel.isCodeUsable(candidateHash: String): Boolean {
        val storedHash = codeHash ?: return false
        return !used &&
            attempts < invitationProperties.maxCodeAttempts &&
            expiresAt.isAfter(Instant.now(clock)) &&
            // String.equals bails out on the first differing char
            MessageDigest.isEqual(storedHash.toByteArray(), candidateHash.toByteArray())
    }

    /** expiresAt is only ever written when a code is minted, so it doubles as "last sent". */
    private fun InvitationTokenModel.isInResendCooldown(): Boolean =
        expiresAt
            .minus(invitationProperties.codeExpirationMinutes, ChronoUnit.MINUTES)
            .isAfter(Instant.now(clock).minusSeconds(invitationProperties.resendCooldownSeconds))

    private fun generateRegistrationCode(): String = "%06d".format(secureRandom.nextInt(1_000_000))

    fun validateInvitation(rawToken: String): InvitationTokenModel {
        val tokenHash = jwtService.hashToken(rawToken)
        val invitation =
            invitationTokenRepository.findByTokenHash(tokenHash)
                ?: throw InvalidTokenException(
                    message = "Invitation token is invalid.",
                    errorCode = GlobalErrorCode.INVALID_INVITATION_TOKEN,
                    status = HttpStatus.BAD_REQUEST,
                )

        if (invitation.used) {
            throw InvalidTokenException(
                message = "Invitation has already been accepted.",
                errorCode = GlobalErrorCode.INVITATION_ALREADY_ACCEPTED,
                status = HttpStatus.BAD_REQUEST,
            )
        }

        if (invitation.expiresAt.isBefore(Instant.now(clock))) {
            throw InvalidTokenException(
                message = "Invitation token has expired.",
                errorCode = GlobalErrorCode.INVITATION_TOKEN_EXPIRED,
                status = HttpStatus.BAD_REQUEST,
            )
        }

        logger.info("Invitation validated for user={}", invitation.user.email)
        return invitation
    }

    fun acceptInvitation(
        rawToken: String,
        newPassword: String,
        firstName: String,
        lastName: String,
    ): AuthTokens = acceptInvitation(validateInvitation(rawToken), newPassword, firstName, lastName)

    /**
     * Overload for callers that already hold a validated row (registration-code
     * completion), so event, cookie and refresh-token handling live in exactly
     * one place.
     */
    fun acceptInvitation(
        invitation: InvitationTokenModel,
        newPassword: String,
        firstName: String,
        lastName: String,
    ): AuthTokens {
        passwordValidationService.validateOrThrow(newPassword)

        logger.info("Accepting invitation for user={}", invitation.user.email)
        val user = invitation.user
        user.passwordHash = passwordEncoder.encode(newPassword)
        user.firstName = firstName
        user.lastName = lastName
        user.isEnabled = true
        userService.save(user)
        // listeners (e.g. self-registered company activation) join this transaction
        eventPublisher.publishEvent(InvitationAcceptedEvent(user))

        invitation.used = true
        invitationTokenRepository.save(invitation)

        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = jwtService.generateOpaqueRefreshToken()
        val refreshTokenHash = jwtService.hashToken(refreshToken)
        val refreshTokenExpiration = Instant.now(clock).plusMillis(jwtProperties.refreshTokenExpiration)

        refreshTokenRepository.save(
            RefreshTokenModel(
                tokenHash = refreshTokenHash,
                user = user,
                expiresAt = refreshTokenExpiration,
                familyId = UUID.randomUUID().toString(),
            ),
        )

        return AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            response =
                AuthResponse(
                    expiresIn = jwtProperties.accessTokenExpiration,
                    user = user.toAuthDTO(),
                ),
        )
    }

    /**
     * Invite acceptance doubles as the email-ownership proof for self-registered
     * companies: CompanyService listens for [InvitationAcceptedEvent] and flips
     * their is_enabled in this transaction.
     */
    fun resendInvitation(userId: String) {
        val user = userService.getUser(userId)

        logger.info("Resending invitation for userId={}", userId)
        createAndSendInvitation(user)
    }
}
