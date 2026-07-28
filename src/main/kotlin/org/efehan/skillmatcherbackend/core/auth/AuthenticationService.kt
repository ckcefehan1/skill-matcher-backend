package org.efehan.skillmatcherbackend.core.auth

import org.efehan.skillmatcherbackend.config.properties.JwtProperties
import org.efehan.skillmatcherbackend.config.properties.LoginLockoutProperties
import org.efehan.skillmatcherbackend.exception.GlobalErrorCode
import org.efehan.skillmatcherbackend.persistence.RefreshTokenModel
import org.efehan.skillmatcherbackend.persistence.RefreshTokenRepository
import org.efehan.skillmatcherbackend.persistence.UserModel
import org.efehan.skillmatcherbackend.persistence.UserRepository
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
import java.util.UUID

@Service
@Transactional
class AuthenticationService(
    private val userRepository: UserRepository,
    private val authenticationManager: AuthenticationManager,
    private val jwtService: JwtService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtProperties: JwtProperties,
    private val loginLockoutProperties: LoginLockoutProperties,
    private val passwordEncoder: PasswordEncoder,
    private val passwordValidationService: PasswordValidationService,
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
        val user = userRepository.findByEmail(email)
        if (user == null) {
            // Equalize timing with a real bcrypt comparison to avoid user enumeration
            passwordEncoder.matches(password, dummyBcryptHash)
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
            userRepository.save(user)
        }

        val accessToken = jwtService.generateAccessToken(user)

        val refreshToken = jwtService.generateOpaqueRefreshToken()
        val refreshTokenHash = jwtService.hashToken(refreshToken)

        val refreshTokenExpiration = Instant.now(clock).plusMillis(jwtProperties.refreshTokenExpiration)
        val accessTokenExpiration = jwtProperties.accessTokenExpiration

        val refreshTokenModel =
            RefreshTokenModel(
                tokenHash = refreshTokenHash,
                user = user,
                expiresAt = refreshTokenExpiration,
                familyId = UUID.randomUUID().toString(),
                revoked = false,
            )

        refreshTokenRepository.save(refreshTokenModel)

        return AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            response =
                AuthResponse(
                    expiresIn = accessTokenExpiration,
                    user = user.toAuthDTO(),
                ),
        )
    }

    fun refreshToken(rawToken: String): AuthTokens {
        val tokenHash = jwtService.hashToken(rawToken)
        // findByTokenHash takes a pessimistic write lock — concurrent refreshes of the
        // same token serialize here; the loser sees revoked=true and hits reuse detection
        val existingToken =
            refreshTokenRepository.findByTokenHash(tokenHash) ?: throw InvalidTokenException(
                message = "Refresh token not found",
                errorCode = GlobalErrorCode.REFRESH_TOKEN_NOT_FOUND,
                status = HttpStatus.UNAUTHORIZED,
            )

        // ponytail: strict reuse detection, no grace window — add one if parallel-tab logouts become a real problem
        if (existingToken.revoked) {
            // must commit even though the refresh below rolls back with 401
            requiresNewTx.executeWithoutResult {
                refreshTokenRepository.revokeAllByFamilyId(existingToken.familyId)
            }
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
        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = rotateRefreshToken(existingToken)

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
        userRepository.save(user)

        refreshTokenRepository.revokeAllUserTokens(user.id)
    }

    fun logout(userId: String) {
        refreshTokenRepository.revokeAllUserTokens(userId)
    }

    private fun registerFailedLogin(
        user: UserModel,
        now: Instant,
    ) {
        user.failedLoginAttempts += 1
        if (user.failedLoginAttempts >= loginLockoutProperties.maxFailedAttempts) {
            user.lockedUntil = now.plus(Duration.ofMinutes(loginLockoutProperties.lockoutDurationMinutes))
            user.failedLoginAttempts = 0
        }
        userRepository.save(user)
    }

    private fun rotateRefreshToken(oldToken: RefreshTokenModel): String {
        oldToken.revoked = true

        val newToken = jwtService.generateOpaqueRefreshToken()
        val newTokenHash = jwtService.hashToken(newToken)
        refreshTokenRepository.save(
            RefreshTokenModel(
                tokenHash = newTokenHash,
                user = oldToken.user,
                expiresAt = Instant.now(clock).plusMillis(jwtProperties.refreshTokenExpiration),
                familyId = oldToken.familyId,
            ),
        )
        return newToken
    }
}
