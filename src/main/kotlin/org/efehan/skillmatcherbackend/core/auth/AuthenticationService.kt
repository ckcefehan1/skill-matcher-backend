package org.efehan.skillmatcherbackend.core.auth

import org.efehan.skillmatcherbackend.config.properties.JwtProperties
import org.efehan.skillmatcherbackend.config.properties.LoginLockoutProperties
import org.efehan.skillmatcherbackend.core.audit.AuditService
import org.efehan.skillmatcherbackend.core.user.UserService
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.AuditAction
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.shared.exceptions.AccountLockedException
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidCredentialsException
import org.efehan.skillmatcherbackend.shared.exceptions.InvalidTokenException
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
@Transactional
class AuthenticationService(
    private val userService: UserService,
    private val authenticationManager: AuthenticationManager,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    private val jwtProperties: JwtProperties,
    private val loginLockoutProperties: LoginLockoutProperties,
    private val passwordEncoder: PasswordEncoder,
    private val passwordValidationService: PasswordValidationService,
    private val auditService: AuditService,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val requiresNewTx =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionTemplate.PROPAGATION_REQUIRES_NEW
        }
    private val dummyBcryptHash: String by lazy { passwordEncoder.encode("dummy-password")!! }

    fun login(
        email: String,
        password: String,
    ): AuthTokens {
        val user = userService.findByEmail(email)
        if (user == null) {
            // Equalize timing with a real bcrypt comparison to avoid user enumeration
            passwordEncoder.matches(password, dummyBcryptHash)
            // own transaction, the throw below rolls this one back
            requiresNewTx.executeWithoutResult {
                auditService.record(AuditAction.LOGIN_FAILED, actor = null, detail = email)
            }
            throw InvalidCredentialsException(
                errorCode = GlobalErrorCode.BAD_CREDENTIALS,
                status = HttpStatus.UNAUTHORIZED,
            )
        }

        val now = Instant.now(clock)
        if (user.lockedUntil?.isAfter(now) == true) {
            throw AccountLockedException(
                errorCode = GlobalErrorCode.ACCOUNT_LOCKED,
                status = HttpStatus.LOCKED,
            )
        }

        try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(email, password),
            )
        } catch (exception: BadCredentialsException) {
            registerFailedLogin(user, now)
            throw exception
        }

        if (user.failedLoginAttempts > 0 || user.lockedUntil != null) {
            user.failedLoginAttempts = 0
            user.lockedUntil = null
            userService.save(user)
        }

        auditService.record(AuditAction.LOGIN_SUCCEEDED, actor = user)

        return AuthTokens(
            accessToken = jwtService.generateAccessToken(user),
            refreshToken = refreshTokenService.issue(user),
            response =
                AuthResponse(
                    expiresIn = jwtProperties.accessTokenExpiration,
                    user = user.toAuthDTO(),
                ),
        )
    }

    fun refreshToken(rawToken: String): AuthTokens {
        val existingToken =
            refreshTokenService.findByRawToken(rawToken) ?: throw InvalidTokenException(
                message = "Refresh token not found",
                errorCode = GlobalErrorCode.REFRESH_TOKEN_NOT_FOUND,
                status = HttpStatus.UNAUTHORIZED,
            )

        // ponytail: strict reuse detection, no grace window — add one if parallel-tab logouts become a real problem
        if (existingToken.revoked) {
            refreshTokenService.revokeFamily(existingToken.familyId)
            throw InvalidTokenException(
                message = "Refresh token reuse detected, token family revoked",
                errorCode = GlobalErrorCode.INVALID_REFRESH_TOKEN,
                status = HttpStatus.UNAUTHORIZED,
            )
        }

        if (existingToken.expiresAt.isBefore(Instant.now(clock))) {
            throw InvalidTokenException(
                message = "Refresh token is expired or invalid",
                errorCode = GlobalErrorCode.INVALID_REFRESH_TOKEN,
                status = HttpStatus.UNAUTHORIZED,
            )
        }

        val user = existingToken.user

        return AuthTokens(
            accessToken = jwtService.generateAccessToken(user),
            refreshToken = refreshTokenService.rotate(existingToken),
            response =
                AuthResponse(
                    expiresIn = jwtProperties.accessTokenExpiration,
                    user = user.toAuthDTO(),
                ),
        )
    }

    fun changePassword(
        user: UserModel,
        currentPassword: String,
        newPassword: String,
    ) {
        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            throw InvalidCredentialsException(
                errorCode = GlobalErrorCode.BAD_CREDENTIALS,
                status = HttpStatus.UNAUTHORIZED,
            )
        }

        passwordValidationService.validateOrThrow(newPassword)

        user.passwordHash = passwordEncoder.encode(newPassword)
        userService.save(user)

        refreshTokenService.revokeAllForUser(user.id)

        auditService.record(AuditAction.PASSWORD_CHANGED, actor = user)
    }

    fun logout(userId: String) {
        refreshTokenService.revokeAllForUser(userId)
    }

    // Runs in its own transaction: login() rethrows BadCredentialsException right after,
    // which would otherwise roll the counter back and leave the lockout unreachable.
    private fun registerFailedLogin(
        user: UserModel,
        now: Instant,
    ) {
        requiresNewTx.executeWithoutResult {
            val fresh = userService.findById(user.id) ?: return@executeWithoutResult
            fresh.failedLoginAttempts += 1
            auditService.record(AuditAction.LOGIN_FAILED, actor = fresh)
            if (fresh.failedLoginAttempts >= loginLockoutProperties.maxFailedAttempts) {
                fresh.lockedUntil = now.plus(Duration.ofMinutes(loginLockoutProperties.lockoutDurationMinutes))
                fresh.failedLoginAttempts = 0
                auditService.record(AuditAction.ACCOUNT_LOCKED, actor = fresh)
            }
            userService.save(fresh)
        }
    }
}
