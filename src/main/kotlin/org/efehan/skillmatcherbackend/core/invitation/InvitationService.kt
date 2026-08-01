package org.efehan.skillmatcherbackend.core.invitation

import org.efehan.skillmatcherbackend.config.properties.InvitationProperties
import org.efehan.skillmatcherbackend.config.properties.JwtProperties
import org.efehan.skillmatcherbackend.core.audit.AuditService
import org.efehan.skillmatcherbackend.core.auth.AuthResponse
import org.efehan.skillmatcherbackend.core.auth.AuthTokens
import org.efehan.skillmatcherbackend.core.auth.JwtService
import org.efehan.skillmatcherbackend.core.auth.PasswordValidationService
import org.efehan.skillmatcherbackend.core.mail.EmailService
import org.efehan.skillmatcherbackend.config.CacheConfig
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.AuditAction
import org.efehan.skillmatcherbackend.persistence.CompanyRepository
import org.efehan.skillmatcherbackend.persistence.InvitationTokenModel
import org.efehan.skillmatcherbackend.persistence.InvitationTokenRepository
import org.efehan.skillmatcherbackend.persistence.RefreshTokenModel
import org.efehan.skillmatcherbackend.persistence.RefreshTokenRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
import org.efehan.skillmatcherbackend.shared.exceptions.EntryNotFoundException
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidTokenException
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@Transactional
class InvitationService(
    private val invitationTokenRepository: InvitationTokenRepository,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtService: JwtService,
    private val jwtProperties: JwtProperties,
    private val emailService: EmailService,
    private val invitationProperties: InvitationProperties,
    private val passwordEncoder: PasswordEncoder,
    private val passwordValidationService: PasswordValidationService,
    private val clock: Clock,
    private val companyRepository: CompanyRepository,
    private val cacheManager: CacheManager,
    private val auditService: AuditService,
) {
    private val logger = LoggerFactory.getLogger(InvitationService::class.java)

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
    ): AuthTokens {
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

        passwordValidationService.validateOrThrow(newPassword)

        logger.info("Accepting invitation for user={}", invitation.user.email)
        val user = invitation.user
        user.passwordHash = passwordEncoder.encode(newPassword)
        user.firstName = firstName
        user.lastName = lastName
        user.isEnabled = true
        userRepository.save(user)
        activateSelfRegisteredCompany(user)

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
     * companies: this is the moment their is_enabled flips. Only self-registered
     * ones — a company disabled by the platform stays disabled.
     */
    private fun activateSelfRegisteredCompany(user: UserModel) {
        val companyId = user.companyId ?: return
        val company = companyRepository.findById(companyId).orElse(null) ?: return
        if (!company.selfRegistered || company.isEnabled) return

        company.isEnabled = true
        companyRepository.save(company)
        cacheManager.getCache(CacheConfig.COMPANY_ENABLED)?.evict(companyId)
        auditService.record(
            AuditAction.COMPANY_ENABLED,
            actor = user,
            targetId = company.id,
            detail = "name=${company.name}, via invitation acceptance",
            companyId = company.id,
        )
    }

    fun resendInvitation(userId: String) {
        val user =
            userRepository.findById(userId).orElseThrow {
                EntryNotFoundException(
                    resource = "User",
                    field = "id",
                    value = userId,
                    errorCode = GlobalErrorCode.USER_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                )
            }

        logger.info("Resending invitation for userId={}", userId)
        createAndSendInvitation(user)
    }
}
