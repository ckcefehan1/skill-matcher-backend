package org.efehan.skillmatcherbackend.core.auth

import org.efehan.skillmatcherbackend.config.properties.PasswordResetProperties
import org.efehan.skillmatcherbackend.core.mail.EmailService
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.PasswordResetTokenModel
import org.efehan.skillmatcherbackend.persistence.PasswordResetTokenRepository
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidTokenException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
@Transactional
class PasswordResetService(
    private val userService: UserService,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val jwtService: JwtService,
    private val emailService: EmailService,
    private val passwordEncoder: PasswordEncoder,
    private val passwordValidationService: PasswordValidationService,
    private val passwordResetProperties: PasswordResetProperties,
    private val clock: Clock = Clock.systemUTC(),
    private val refreshTokenService: RefreshTokenService,
) {
    private val logger = LoggerFactory.getLogger(PasswordResetService::class.java)

    fun requestPasswordReset(email: String) {
        val user = userService.findByEmail(email)

        if (user == null) {
            // Log but don't reveal that the user doesn't exist
            logger.info("Password reset requested for non-existent email: {}", email)
            return
        }

        if (!user.isEnabled) {
            logger.info("Password reset requested for disabled account: {}", email)
            return
        }

        // Invalidate any existing tokens for this user
        passwordResetTokenRepository.invalidateAllUserTokens(user.id)

        // Generate new token
        val token = jwtService.generateOpaqueRefreshToken()
        val tokenHash = jwtService.hashToken(token)
        val expiresAt = Instant.now(clock).plusSeconds(passwordResetProperties.tokenExpirationHours * 3600)

        val resetToken =
            PasswordResetTokenModel(
                tokenHash = tokenHash,
                user = user,
                expiresAt = expiresAt,
            )

        passwordResetTokenRepository.save(resetToken)

        // Send email
        emailService.sendPasswordResetEmail(
            user = user,
            resetToken = token,
            expirationHours = passwordResetProperties.tokenExpirationHours,
        )

        logger.info("Password reset token generated for user: {}", user.id)
    }

    fun validateToken(token: String): PasswordResetTokenValidationResponse {
        val tokenHash = jwtService.hashToken(token)
        val resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)

        if (resetToken == null || resetToken.used || resetToken.expiresAt.isBefore(Instant.now(clock))) {
            return PasswordResetTokenValidationResponse(valid = false, email = null)
        }

        return PasswordResetTokenValidationResponse(
            valid = true,
            email = maskEmail(resetToken.user.email),
        )
    }

    fun resetPassword(
        token: String,
        newPassword: String,
    ) {
        val tokenHash = jwtService.hashToken(token)
        val resetToken =
            passwordResetTokenRepository.findByTokenHash(tokenHash)
                ?: throw InvalidTokenException(
                    message = "Invalid password reset token",
                    errorCode = GlobalErrorCode.INVALID_PASSWORD_RESET_TOKEN,
                    status = HttpStatus.BAD_REQUEST,
                )

        if (resetToken.used) {
            throw InvalidTokenException(
                message = "Password reset token has already been used",
                errorCode = GlobalErrorCode.PASSWORD_RESET_TOKEN_USED,
                status = HttpStatus.BAD_REQUEST,
            )
        }

        if (resetToken.expiresAt.isBefore(Instant.now(clock))) {
            throw InvalidTokenException(
                message = "Password reset token has expired",
                errorCode = GlobalErrorCode.PASSWORD_RESET_TOKEN_EXPIRED,
                status = HttpStatus.BAD_REQUEST,
            )
        }

        // Validate new password
        passwordValidationService.validateOrThrow(newPassword)

        // Update password
        val user = resetToken.user
        user.passwordHash = passwordEncoder.encode(newPassword)
        userService.save(user)

        refreshTokenService.revokeAllForUser(user.id)

        // Mark token as used
        resetToken.used = true
        passwordResetTokenRepository.save(resetToken)

        logger.info("Password successfully reset for user: {}", user.id)
    }

    private fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return "***"

        val localPart = parts[0]
        val domain = parts[1]

        val maskedLocal =
            if (localPart.length <= 2) {
                "${localPart.first()}***"
            } else {
                "${localPart.first()}***${localPart.last()}"
            }

        return "$maskedLocal@$domain"
    }
}
